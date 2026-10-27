package com.mahghuuuls.agenttesttoolkit.session;

import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.DiagnosticSession;
import com.mahghuuuls.agenttesttoolkit.state.SessionStamp;
import com.mahghuuuls.agenttesttoolkit.state.ToolkitState;
import com.mahghuuuls.agenttesttoolkit.world.WorldIdentity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Writes a {@code WORLD} record when the overworld loads, and ends a session from another save.
 *
 * <p>Runs at the highest priority, so it comes before the scenario check and every toolkit
 * record a command, a bundle, the inbox or a join can write in the new world: when a session from
 * another save is stopped, its records (an operator unbinding, then the stop) come first, and
 * nothing written afterwards carries the old session's name. Opening the same save again keeps
 * the session (ARC-001 still holds for the same world).
 *
 * <p>One narrow exception: a brand-new world generates its spawn area before this event, and if
 * {@code entity_spawn} logging is still on from the previous world, animals spawned there are
 * recorded under the old session. An existing save never takes that path.
 *
 * <p>Lives in {@code session} because it applies a session rule; {@code world} only says what
 * the identity is.
 */
public final class WorldChangeListener {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onWorldLoad(WorldEvent.Load event) {
        World world = event.getWorld();
        if (world == null || world.isRemote || world.provider.getDimension() != 0) {
            return;
        }
        MinecraftServer server = world.getMinecraftServer();
        if (server == null) {
            return;
        }
        loaded(new WorldIdentity(server.getFolderName(), world.getSeed()), RecordContext.snapshot(world));
    }

    /** The decision and the records, apart from the event so they are tested without a world. */
    static void loaded(WorldIdentity identity, RecordContext.Snapshot context) {
        DiagnosticSession session = ToolkitState.getActiveSession();
        Boolean sameAsSession = null;
        if (session != null) {
            if (identity.endsSessionFrom(session.getWorldKey())) {
                SessionManager.stop(context, "worldChanged");
                sameAsSession = Boolean.FALSE;
            } else if (session.getWorldKey() != null) {
                sameAsSession = Boolean.TRUE;
            }
        }
        ToolkitState.setCurrentWorldKey(identity.key());

        LogRecord record = RecordContext.stamp(LogRecord.of(EventType.WORLD), context);
        SessionStamp.apply(record);
        record.add("folder", identity.getFolder());
        record.add("seed", identity.getSeed());
        if (sameAsSession != null) {
            // Omitted with no session, or a session that never knew its world.
            record.add("sameAsSession", sameAsSession.booleanValue());
        }
        ToolkitLog.write(record);
    }
}
