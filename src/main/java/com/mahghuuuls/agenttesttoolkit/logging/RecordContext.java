package com.mahghuuuls.agenttesttoolkit.logging;

import net.minecraft.command.ICommandSender;
import net.minecraft.world.World;

/**
 * Stamps the runtime context fields that every record carries: the logical side and the
 * world tick.
 *
 * <p>{@code side} is the logical side. This version only observes the logical server, so the
 * value is effectively constant, but it is emitted anyway so the format does not change when
 * client side observation is added.
 *
 * <p>{@code worldTick} is taken from the dimension in which the event occurred.
 * Dimensions keep independent tick counts, so a single global counter would be wrong: an
 * event in the Nether and one in the Overworld are not comparable through a shared clock.
 *
 * <p>Lives in {@code logging} rather than {@code command} on purpose. The event handlers in
 * {@code observe} need exactly this, and {@code observe} is not allowed to depend on
 * {@code command}. Every subsystem may depend on {@code logging}, so this is the only
 * placement that serves both without duplicating the logic or breaking the dependency graph.
 */
public final class RecordContext {

    public static final String SERVER = "SERVER";
    public static final String CLIENT = "CLIENT";

    private RecordContext() {
    }

    public static String side(ICommandSender sender) {
        return sender == null ? SERVER : side(sender.getEntityWorld());
    }

    public static String side(World world) {
        return (world != null && world.isRemote) ? CLIENT : SERVER;
    }

    /**
     * @return the total elapsed tick count of the given world, or -1 when no world is
     * available. Callers omit the field rather than emitting -1.
     */
    public static long worldTick(World world) {
        return world == null ? -1L : world.getTotalWorldTime();
    }

    public static long worldTick(ICommandSender sender) {
        return sender == null ? -1L : worldTick(sender.getEntityWorld());
    }

    /**
     * Adds {@code side} and, when a world is available, {@code worldTick}.
     *
     * <p>Called first when building a record so these fields lead consistently, which is what
     * keeps field order stable across every event type.
     */
    public static LogRecord stamp(LogRecord record, ICommandSender sender) {
        return stamp(record, snapshot(sender));
    }

    public static LogRecord stamp(LogRecord record, Snapshot context) {
        record.add("side", context.side);
        if (context.worldTick >= 0L) {
            record.add("worldTick", context.worldTick);
        }
        return record;
    }

    public static Snapshot snapshot(ICommandSender sender) {
        return new Snapshot(side(sender), worldTick(sender));
    }

    /** Context for an observed game event, which has a world but no command sender. */
    public static Snapshot snapshot(World world) {
        return new Snapshot(side(world), worldTick(world));
    }

    /**
     * An immutable capture of the context fields, taken at the moment of the event.
     *
     * <p>Exists so components that must stay free of Minecraft types can still stamp records
     * correctly. {@code SessionManager} previously took a bare tick value, which meant it was
     * structurally unable to emit {@code side} at all, and silently omitted it on two
     * event types. Passing the whole context instead of one field makes that failure mode
     * impossible rather than merely fixed.
     */
    public static final class Snapshot {

        /** Context with no world, used before a world exists or from a worldless sender. */
        public static final Snapshot NONE = new Snapshot(SERVER, -1L);

        private final String side;
        private final long worldTick;

        public Snapshot(String side, long worldTick) {
            this.side = side == null ? SERVER : side;
            this.worldTick = worldTick;
        }
    }
}
