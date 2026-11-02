package com.mahghuuuls.agenttesttoolkit.fixture;

import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.DiagnosticSession;
import com.mahghuuuls.agenttesttoolkit.state.ToolkitState;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Takes a fixture out of the world without a death, and says {@code removed} only once it is.
 *
 * <p>Before 1.4.0 {@code fixture clear} called the kill path and wrote {@code removed} at once,
 * while a living fixture went on dying for about a second, dropped its loot and fired death
 * events, and an assertion two ticks later still found it (CYCLE-005 intake D2). Now the entity
 * is removed with no drops, which vanilla carries out at the world's next entity update, and
 * its {@code FIXTURE} record waits here until the end of a later server tick, when that update
 * has run (REQ-210).
 *
 * <p>Server-bound transient state, like pending damage records: discarded, with a record each,
 * when the server stops.
 */
public final class FixtureRemovals {

    private static final class Pending {
        final String handle;
        final String uuid;
        final int entityId;
        final String sessionName;
        final LogRecord record;
        final int queuedAt;

        Pending(String handle, String uuid, int entityId, String sessionName, LogRecord record, int queuedAt) {
            this.handle = handle;
            this.uuid = uuid;
            this.entityId = entityId;
            this.sessionName = sessionName;
            this.record = record;
            this.queuedAt = queuedAt;
        }
    }

    private static final List<Pending> PENDING = new ArrayList<Pending>();

    /**
     * Removes the entity and queues its record. The caller has checked the tag and built the
     * record up to, but not including, its outcome.
     */
    public static void remove(MinecraftServer server, Entity entity, String handle, LogRecord record) {
        // No drops: a container minecart would otherwise spill its inventory as it goes.
        entity.setDropItemsWhenDead(false);
        // removeEntity, not the kill path: no death, no loot, no experience, riders dismounted
        // and left in the world. Not removeEntityDangerously, which skips the riders.
        entity.world.removeEntity(entity);
        ToolkitState.removeFixture(handle);
        DiagnosticSession session = ToolkitState.getActiveSession();
        PENDING.add(new Pending(handle, entity.getUniqueID().toString(), entity.getEntityId(),
                session == null ? null : session.getName(), record, server.getTickCounter()));
    }

    /** How many removals still wait for their record. */
    public static int pending() {
        return PENDING.size();
    }

    /**
     * At the end of a server tick after the one the removal was queued in, the world's entity
     * update has run. A bundle step runs at the end of a tick, before the next one's update, so
     * waiting for a later tick is what makes "gone" true for it too.
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return;
        }
        int now = server.getTickCounter();
        List<Pending> due = new ArrayList<Pending>();
        for (Pending pending : PENDING) {
            if (now > pending.queuedAt) {
                due.add(pending);
            }
        }
        PENDING.removeAll(due);
        for (Pending pending : due) {
            Entity still = server.getEntityFromUuid(UUID.fromString(pending.uuid));
            if (still != null && still.getEntityId() != pending.entityId) {
                // A new entity under the same UUID, summoned once the old one had left. The
                // removed one is gone.
                still = null;
            }
            if (still == null) {
                pending.record.add("outcome", "removed");
            } else if (still.isDead) {
                // A world with no player and no forced chunk stops updating entities after a
                // while, and a removed entity there stays findable, dead, until it does.
                pending.record.add("outcome", "removed");
                pending.record.add("unloadPending", true);
            } else {
                // Something brought it back. Said so, and the handle is kept, but only where it
                // cannot overwrite anything: the same session, the handle not claimed again.
                pending.record.add("outcome", "failed");
                pending.record.add("reason", "stillAlive");
                DiagnosticSession session = ToolkitState.getActiveSession();
                if (session != null && session.getName().equals(pending.sessionName)
                        && ToolkitState.getFixtureUuid(pending.handle) == null) {
                    ToolkitState.putFixture(pending.handle, pending.uuid);
                }
            }
            ToolkitLog.write(pending.record);
        }
    }

    /** At a server stop: each waiting removal is recorded as not confirmed. */
    public static void discardPending() {
        for (Pending pending : PENDING) {
            pending.record.add("outcome", "unconfirmed");
            pending.record.add("reason", "serverStopped");
            ToolkitLog.write(pending.record);
        }
        PENDING.clear();
    }

    /** For tests. */
    public static void resetForTesting() {
        PENDING.clear();
    }
}
