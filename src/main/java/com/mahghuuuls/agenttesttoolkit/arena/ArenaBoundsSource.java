package com.mahghuuuls.agenttesttoolkit.arena;

import com.mahghuuuls.agenttesttoolkit.logging.filter.ArenaBounds;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

/**
 * The live arena lookup filters use.
 *
 * <p>Lives in {@code arena} rather than in {@code logging/filter} on purpose. Filters may
 * query the arena but never the reverse, and the filter package only knows the
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
        // DimensionManager rather than server.getWorld, which Forge patches to *initialise* a
        // dimension that is not loaded. This runs on every observed event while an arena filter
        // is active, so calling it with an id that happens to be unloaded would load a whole
        // dimension as a side effect of deciding whether to write a log line.
        World world = net.minecraftforge.common.DimensionManager.getWorld(dimension);
        if (world == null) {
            // Not loaded, or not a dimension. Either way there is no arena to be inside, so
            // the filter admits nothing, which is the same answer it gives when a loaded
            // dimension has no arena.
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
