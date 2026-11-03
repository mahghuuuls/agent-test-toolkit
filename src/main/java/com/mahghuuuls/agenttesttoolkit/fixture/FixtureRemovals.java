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
 * its {@code FIXTURE} record waits here until the end of the server tick in which that update
 * has run (REQ-210). Its {@code confirmedTick} is then one more than the world tick of the clear.
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
        final int dimension;
        final LogRecord record;
        final int queuedAt;
        final long queuedWorldTick;

        Pending(String handle, String uuid, int entityId, String sessionName, int dimension, LogRecord record,
                int queuedAt, long queuedWorldTick) {
            this.handle = handle;
            this.uuid = uuid;
            this.entityId = entityId;
            this.sessionName = sessionName;
            this.dimension = dimension;
            this.record = record;
            this.queuedAt = queuedAt;
            this.queuedWorldTick = queuedWorldTick;
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
                session == null ? null : session.getName(), entity.dimension, record, server.getTickCounter(),
                entity.world.getTotalWorldTime()));
    }

    /**
     * Whether a removal can be checked now: its world has run an entity update since. Read from
     * the world's own clock, which moves just before that update, so a clear typed in chat, at
     * the start of a tick, is checked at the end of that same tick, and one from a bundle step,
     * at the end of a tick, at the end of the next. Two server ticks always suffice, for a world
     * that has unloaded meanwhile. Pure, for tests.
     *
     * @param worldTick the world's total time now, or -1 when the world is gone
     */
    static boolean isDue(long worldTick, long queuedWorldTick, int serverTick, int queuedAt) {
        if (serverTick > queuedAt + 1) {
            return true;
        }
        return worldTick >= 0L && worldTick > queuedWorldTick;
    }

    /** What a removal's record says once it is checked. */
    public enum Outcome {
        /** Not found, or found as a new entity under the same UUID: the removed one is gone. */
        REMOVED,
        /** Found, the same entity, dead: removed, but its world has stopped updating entities. */
        REMOVED_UNLOAD_PENDING,
        /** Found, the same entity, alive: something brought it back. */
        STILL_ALIVE
    }

    /**
     * The confirmation decision, from what a UUID lookup found a tick or more after the removal.
     * Pure, for tests.
     *
     * @param found an entity has the UUID
     * @param sameEntity it is the entity that was removed (same entity id)
     * @param dead it is dead
     */
    public static Outcome outcome(boolean found, boolean sameEntity, boolean dead) {
        if (!found || !sameEntity) {
            return Outcome.REMOVED;
        }
        return dead ? Outcome.REMOVED_UNLOAD_PENDING : Outcome.STILL_ALIVE;
    }

    /** How many removals still wait for their record. */
    public static int pending() {
        return PENDING.size();
    }

    /** At the end of each server tick, checks the removals whose world has updated since ({@link #isDue}). */
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
            net.minecraft.world.WorldServer world =
                    net.minecraftforge.common.DimensionManager.getWorld(pending.dimension);
            if (isDue(world == null ? -1L : world.getTotalWorldTime(), pending.queuedWorldTick, now,
                    pending.queuedAt)) {
                due.add(pending);
            }
        }
        PENDING.removeAll(due);
        for (Pending pending : due) {
            Entity still = server.getEntityFromUuid(UUID.fromString(pending.uuid));
            Outcome outcome = outcome(still != null, still != null && still.getEntityId() == pending.entityId,
                    still != null && still.isDead);
            // The record carries the tick of the clear; this says when the removal was seen done.
            net.minecraft.world.WorldServer world =
                    net.minecraftforge.common.DimensionManager.getWorld(pending.dimension);
            if (world != null) {
                pending.record.add("confirmedTick", world.getTotalWorldTime());
            }
            if (outcome == Outcome.REMOVED) {
                pending.record.add("outcome", "removed");
            } else if (outcome == Outcome.REMOVED_UNLOAD_PENDING) {
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
