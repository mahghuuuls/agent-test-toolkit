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

    /** Also wait until the chunks are decorated, and the ones that decorate into them. */
    private final boolean populated;

    private String firstUnloaded;
    private long loadedCount;
    private ChunkPopulation.Result population;

    /**
     * @param player the sender's UUID when the sender is a player, so a dimension change can
     *               be noticed; null for the console or a command block
     */
    public ChunkReadiness(MinecraftServer server, ChunkRegion region, int dimension, UUID player) {
        this(server, region, dimension, player, false);
    }

    /** @param populated also wait until the chunks are decorated for good ({@link ChunkPopulation}) */
    public ChunkReadiness(MinecraftServer server, ChunkRegion region, int dimension, UUID player, boolean populated) {
        if (server == null || region == null) {
            throw new IllegalArgumentException("server and region must not be null");
        }
        this.server = server;
        this.region = region;
        this.dimension = dimension;
        this.player = player;
        this.populated = populated;
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
        if (!populated) {
            return firstUnloaded == null ? Poll.satisfied() : Poll.pending();
        }
        final ChunkProviderServer chunks = provider;
        population = ChunkPopulation.check(region.getMinChunkX(), region.getMinChunkZ(),
                region.getMaxChunkX(), region.getMaxChunkZ(), new ChunkPopulation.Flags() {
                    @Override
                    public boolean isLoaded(int chunkX, int chunkZ) {
                        return LoadedChunks.get(chunks, chunkX, chunkZ) != null;
                    }

                    @Override
                    public boolean isDecorated(int chunkX, int chunkZ) {
                        net.minecraft.world.chunk.Chunk chunk = LoadedChunks.get(chunks, chunkX, chunkZ);
                        return chunk != null && chunk.isTerrainPopulated();
                    }
                });
        return firstUnloaded == null && population.isReady() ? Poll.satisfied() : Poll.pending();
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
        if (populated) {
            record.add("populated", true);
            if (population != null) {
                record.add("populatedChunks", population.getDecoratedCount());
                record.add("populatedChecked", population.getCheckedCount());
                if (population.getFirstNotDecorated() != null) {
                    record.add("firstUnpopulatedChunk", population.getFirstNotDecorated());
                }
            }
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

    /** Whether this wait also asks for decorated chunks. */
    public boolean isPopulated() {
        return populated;
    }

    /** The decoration check of the most recent poll; null without {@code populated} or before a poll. */
    public ChunkPopulation.Result getPopulation() {
        return population;
    }

    public ChunkRegion getRegion() {
        return region;
    }

    public int getDimension() {
        return dimension;
    }
}
