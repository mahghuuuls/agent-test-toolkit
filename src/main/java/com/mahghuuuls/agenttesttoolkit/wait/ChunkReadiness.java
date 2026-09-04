package com.mahghuuuls.agenttesttoolkit.wait;

import com.mahghuuuls.agenttesttoolkit.bundle.WaitCondition;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.DimensionManager;

import java.util.UUID;

/**
 * Waits until every chunk in a region of one dimension is loaded.
 *
 * <p>Observes and never loads. The check is {@code chunkExists}, one map lookup per chunk,
 * which answers "loaded now" and nothing more; asking the provider for the chunk instead
 * would generate it, which is a world mutation a diagnostic command must not perform. A
 * region nobody is near never becomes ready, and the timeout is what catches that.
 *
 * <p>The dimension is fixed when the wait starts. If the sender leaves it, the wait is
 * cancelled rather than silently following them: the region was named in the coordinates of
 * one world and means nothing in another.
 */
public final class ChunkReadiness implements WaitCondition {

    public static final String KIND = "chunks";

    private final MinecraftServer server;
    private final ChunkRegion region;
    private final int dimension;
    /** The waiting player, or null for a sender that cannot change dimension. */
    private final UUID player;

    private String firstUnloaded;
    private long loadedCount;

    /**
     * @param player the sender's UUID when the sender is a player, so a dimension change can
     *               be noticed; null for the console or a command block
     */
    public ChunkReadiness(MinecraftServer server, ChunkRegion region, int dimension, UUID player) {
        if (server == null || region == null) {
            throw new IllegalArgumentException("server and region must not be null");
        }
        this.server = server;
        this.region = region;
        this.dimension = dimension;
        this.player = player;
    }

    @Override
    public Poll poll(int elapsedTicks) {
        if (player != null) {
            EntityPlayerMP current = server.getPlayerList().getPlayerByUUID(player);
            if (current == null) {
                return Poll.cancelled("senderLost");
            }
            if (current.dimension != dimension) {
                return Poll.cancelled("dimensionChanged");
            }
        }

        // Asked of the dimension manager, not the server: the server's own lookup loads a
        // dimension that is not loaded, which is the one thing this condition must never do.
        WorldServer world = DimensionManager.getWorld(dimension);
        if (world == null) {
            return Poll.cancelled("dimensionUnloaded");
        }
        ChunkProviderServer provider = world.getChunkProvider();

        firstUnloaded = null;
        loadedCount = 0;
        for (int cx = region.getMinChunkX(); cx <= region.getMaxChunkX(); cx++) {
            for (int cz = region.getMinChunkZ(); cz <= region.getMaxChunkZ(); cz++) {
                if (provider.chunkExists(cx, cz)) {
                    loadedCount++;
                } else if (firstUnloaded == null) {
                    // Remembered rather than returned at once, so the count of loaded
                    // chunks in the record is complete and a reader can see progress.
                    firstUnloaded = cx + "," + cz;
                }
            }
        }
        return firstUnloaded == null ? Poll.satisfied() : Poll.pending();
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public void describe(LogRecord record) {
        record.add("dimension", dimension);
        record.add("chunks", region.bounds());
        record.add("chunkCount", region.chunkCount());
        record.add("loadedChunks", loadedCount);
        if (firstUnloaded != null) {
            record.add("firstUnloadedChunk", firstUnloaded);
        }
    }

    /** Chunks found loaded on the most recent poll. */
    public long getLoadedCount() {
        return loadedCount;
    }

    /** The first chunk found missing on the most recent poll, or null when all were loaded. */
    public String getFirstUnloaded() {
        return firstUnloaded;
    }

    public ChunkRegion getRegion() {
        return region;
    }

    public int getDimension() {
        return dimension;
    }
}
