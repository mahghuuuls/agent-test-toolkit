package com.mahghuuuls.agenttesttoolkit.arena;

import com.mahghuuuls.agenttesttoolkit.logging.filter.ArenaBounds;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

/**
 * The live arena lookup filters use.
 *
 * <p>Lives in {@code arena} rather than in {@code logging/filter} on purpose. ARC-007 permits
 * filters to query the arena and forbids the reverse, and the filter package only knows the
 * {@link ArenaBounds} interface. Putting the Minecraft-aware implementation here keeps
 * {@code logging} free of world lookups, which is what lets filter evaluation stay unit
 * testable.
 */
public final class ArenaBoundsSource implements ArenaBounds {

    private final MinecraftServer server;

    public ArenaBoundsSource(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public Box boundsFor(int dimension) {
        if (server == null) {
            return null;
        }
        World world = server.getWorld(dimension);
        if (world == null) {
            // The dimension is not loaded. Not an error: the filter simply admits nothing
            // there, which is what it would do anyway with no arena.
            return null;
        }
        ArenaRecord record = ArenaStorage.get(world).getArena();
        if (record == null) {
            return null;
        }
        ArenaGeometry geometry = record.geometry();
        return new Box(geometry.getMinX(), geometry.getMinY(), geometry.getMinZ(),
                geometry.getMaxX(), geometry.getMaxY(), geometry.getMaxZ());
    }
}
