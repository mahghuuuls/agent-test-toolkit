package com.mahghuuuls.agenttesttoolkit.scenario;

import com.mahghuuuls.agenttesttoolkit.bundle.BundleDefinition;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.DiagnosticSession;
import com.mahghuuuls.agenttesttoolkit.state.OperatorBinding;
import com.mahghuuuls.agenttesttoolkit.state.SessionStamp;
import com.mahghuuuls.agenttesttoolkit.state.ToolkitState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The scenario checkpoint's lifecycle: written before a stop, reported at load, consulted
 * before every run, and cleared by completion or abandonment.
 *
 * <p>The refusal is the feature. A wrong bundle, or the right bundle whose text changed, is
 * turned away with both sides named, so evidence from a resumed test can never belong to a
 * different scenario or a different revision than it claims. Everything else here exists to
 * make that refusal trustworthy: the load record that says what stands, and the explicit
 * clear that ends it.
 */
public final class Scenarios {

    /** Failure key for a run refused by a standing checkpoint. Tolerable by name like any key. */
    public static final String KEY_MISMATCH = "devtool.scenario.mismatch";

    /** Public because the event bus instantiates one to receive world-load events. */
    public Scenarios() {
    }

    /** Reports the stored checkpoint, or its absence, when the overworld loads. */
    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        World world = event.getWorld();
        if (world == null || world.isRemote || world.provider.getDimension() != 0) {
            return;
        }
        ScenarioStorage storage = ScenarioStorage.get(world);
        LogRecord record = RecordContext.stamp(LogRecord.of(EventType.SCENARIO), RecordContext.snapshot(world));
        record.add("action", "load");
        if (storage.isCorrupt()) {
            record.add("state", "corrupt");
            record.add("reason", storage.getCorruptReason());
            record.add("enforced", false);
        } else if (storage.getCheckpoint() == null) {
            record.add("state", "absent");
        } else {
            ScenarioCheckpoint checkpoint = storage.getCheckpoint();
            record.add("state", "present");
            describe(record, checkpoint);
            record.add("enforced", true);
        }
        ToolkitLog.write(record);
    }

    /** What a standing checkpoint says about a run: the checkpoint, and a refusal reason or null. */
    public static final class Decision {
        public final ScenarioCheckpoint checkpoint;
        public final String refusal;

        Decision(ScenarioCheckpoint checkpoint, String refusal) {
            this.checkpoint = checkpoint;
            this.refusal = refusal;
        }

        public boolean isRefused() {
            return refusal != null;
        }
    }

    /**
     * Whether a run may go ahead while a checkpoint stands.
     *
     * <p>A corrupt or absent store refuses nothing and returns null. Otherwise the decision
     * carries the checkpoint, so the caller can restore its state when the run is allowed,
     * and a reason when it is not. Nothing is recorded here; the caller records the outcome
     * once it has acted on it.
     */
    public static Decision decide(MinecraftServer server, BundleDefinition bundle) {
        ScenarioCheckpoint checkpoint = ScenarioStorage.get(server.getWorld(0)).getCheckpoint();
        if (checkpoint == null) {
            return null;
        }
        return new Decision(checkpoint, checkpoint.refusalFor(bundle.getName(), bundle.getHash()));
    }

    /** Writes the record for a decision, refused or allowed, with what was restored when allowed. */
    public static void record(Decision decision, BundleDefinition bundle, int restoredFixtures,
                              RecordContext.Snapshot context) {
        LogRecord record = RecordContext.stamp(LogRecord.of(EventType.SCENARIO), context);
        SessionStamp.apply(record);
        record.add("action", decision.isRefused() ? "refuse" : "resume");
        record.add("scenarioId", decision.checkpoint.getScenarioId());
        record.add("expectedBundle", decision.checkpoint.getNextBundle());
        record.add("expectedHash", decision.checkpoint.getNextHash());
        record.add("offeredBundle", bundle.getName());
        record.add("offeredHash", bundle.getHash());
        if (decision.isRefused()) {
            record.add("reason", decision.refusal);
        } else {
            record.add("restoredFixtures", restoredFixtures);
        }
        ToolkitLog.write(record);
    }

    /** The first twelve characters of a hash, or all of a shorter one. Safe for foreign data. */
    public static String abbrev(String hash) {
        return hash == null ? "" : hash.substring(0, Math.min(12, hash.length()));
    }

    /** Writes a checkpoint from the current session state. */
    public static ScenarioCheckpoint checkpoint(MinecraftServer server, String scenarioId,
                                                BundleDefinition next, boolean expectFatalStop,
                                                RecordContext.Snapshot context) {
        DiagnosticSession session = ToolkitState.getActiveSession();
        OperatorBinding operator = ToolkitState.getOperator();
        List<ScenarioCheckpoint.Fixture> fixtures = new ArrayList<ScenarioCheckpoint.Fixture>();
        for (Map.Entry<String, String> entry : ToolkitState.getFixtures().entrySet()) {
            ToolkitState.FixturePlace place = ToolkitState.getFixturePlace(entry.getValue());
            fixtures.add(new ScenarioCheckpoint.Fixture(entry.getKey(), entry.getValue(),
                    place == null ? 0 : place.dimension, place == null ? 0 : place.x,
                    place == null ? 0 : place.y, place == null ? 0 : place.z));
        }
        ScenarioCheckpoint checkpoint = new ScenarioCheckpoint(scenarioId,
                session == null ? null : session.getName(), next.getName(), next.getHash(),
                operator == null ? null : operator.getUuid().toString(),
                operator == null ? null : operator.getName(), expectFatalStop,
                RecordContext.worldTick(server.getWorld(0)), fixtures);
        ScenarioStorage.get(server.getWorld(0)).setCheckpoint(checkpoint);

        LogRecord record = RecordContext.stamp(LogRecord.of(EventType.SCENARIO), context);
        SessionStamp.apply(record);
        record.add("action", "checkpoint");
        describe(record, checkpoint);
        ToolkitLog.write(record);
        return checkpoint;
    }

    /**
     * Clears the checkpoint.
     *
     * @param action {@code abandoned} or {@code completed}, for the record
     * @return the checkpoint that was cleared, or null when none stood
     */
    public static ScenarioCheckpoint clear(MinecraftServer server, String scenarioId, String action,
                                           RecordContext.Snapshot context) {
        ScenarioStorage storage = ScenarioStorage.get(server.getWorld(0));
        ScenarioCheckpoint checkpoint = storage.getCheckpoint();
        if (checkpoint == null && !storage.isCorrupt()) {
            return null;
        }
        storage.setCheckpoint(null);
        LogRecord record = RecordContext.stamp(LogRecord.of(EventType.SCENARIO), context);
        SessionStamp.apply(record);
        record.add("action", action);
        record.add("scenarioId", checkpoint == null ? scenarioId : checkpoint.getScenarioId());
        if (checkpoint != null) {
            record.add("expectedBundle", checkpoint.getNextBundle());
        }
        ToolkitLog.write(record);
        return checkpoint;
    }

    /** The standing checkpoint, or null. */
    public static ScenarioCheckpoint current(MinecraftServer server) {
        return ScenarioStorage.get(server.getWorld(0)).getCheckpoint();
    }

    public static boolean isCorrupt(MinecraftServer server) {
        return ScenarioStorage.get(server.getWorld(0)).isCorrupt();
    }

    static void describe(LogRecord record, ScenarioCheckpoint checkpoint) {
        record.add("scenarioId", checkpoint.getScenarioId());
        record.add("session", checkpoint.getSession());
        record.add("expectedBundle", checkpoint.getNextBundle());
        record.add("expectedHash", checkpoint.getNextHash());
        record.add("operator", checkpoint.getOperatorName());
        record.add("operatorUuid", checkpoint.getOperatorUuid());
        record.add("expectedStop", checkpoint.isExpectFatalStop() ? "fatal" : "clean");
        record.add("createdTick", checkpoint.getCreatedTick());
        record.add("fixtures", checkpoint.getFixtures().size());
    }
}
