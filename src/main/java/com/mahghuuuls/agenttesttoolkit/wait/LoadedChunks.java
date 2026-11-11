package com.mahghuuuls.agenttesttoolkit.wait;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;

/**
 * A chunk the server already holds, read without any effect on the world.
 *
 * <p>Not {@code getLoadedChunk}, which also takes the chunk off the unload queue and so keeps
 * it loaded longer than the world would: a diagnostic must leave the world as it found it.
 * The map lookup itself neither loads, generates nor keeps anything.
 */
public final class LoadedChunks {

    private LoadedChunks() {
    }

    /** The chunk at these chunk coordinates when it is loaded; null otherwise. */
    public static Chunk get(ChunkProviderServer provider, int chunkX, int chunkZ) {
        return provider.loadedChunks.get(ChunkPos.asLong(chunkX, chunkZ));
    }
}
