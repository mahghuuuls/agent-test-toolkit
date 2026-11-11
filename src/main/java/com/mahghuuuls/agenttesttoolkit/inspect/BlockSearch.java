package com.mahghuuuls.agenttesttoolkit.inspect;

import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.wait.LoadedChunks;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Searches a box for a block, in the chunks the server already holds.
 *
 * <p>Reads only: a chunk not loaded is counted as skipped, never loaded, generated or kept
 * loaded, so a search reaching outside the loaded area says so instead of reading as "nothing
 * found". The walk is {@link BlockScan}; this class reads the world for it. The state read is
 * the one the world stores, so only properties the stored state keeps can be searched for:
 * the others (a fence's sides, a wire's connections) are worked out from the neighbours when
 * the block is drawn, and are refused rather than never matched.
 */
public final class BlockSearch {

    /** Receives one record per position found. */
    public interface Sink {
        void found(LogRecord record);
    }

    private BlockSearch() {
    }

    /**
     * The properties the world stores for a block in at least one of its states, with the names
     * of the values each can take. A property no state stores is computed from the neighbours,
     * and every stored state has its default value.
     */
    public static Map<String, List<String>> storedProperties(Block block) {
        Map<String, List<String>> stored = new LinkedHashMap<String, List<String>>();
        for (IProperty<?> property : block.getBlockState().getProperties()) {
            if (isStoredSomewhere(block, property)) {
                stored.put(property.getName(), valueNames(property));
            }
        }
        return stored;
    }

    /** The properties a block has that the world does not store. */
    public static List<String> computedProperties(Block block) {
        List<String> computed = new ArrayList<String>();
        for (IProperty<?> property : block.getBlockState().getProperties()) {
            if (!isStoredSomewhere(block, property)) {
                computed.add(property.getName());
            }
        }
        return computed;
    }

    private static boolean isStoredSomewhere(Block block, IProperty<?> property) {
        for (IBlockState state : block.getBlockState().getValidStates()) {
            if (isStoredAt(state, property)) {
                return true;
            }
        }
        return false;
    }

    /** Whether this state stores this property, so its value there says something about the block. */
    static <T extends Comparable<T>> boolean isStoredAt(IBlockState state, IProperty<T> property) {
        Block block = state.getBlock();
        java.util.Collection<T> values = property.getAllowedValues();
        int[] metas = new int[values.size()];
        int i = 0;
        for (T value : values) {
            metas[i++] = block.getMetaFromState(state.withProperty(property, value));
        }
        return BlockMatch.isStoredAt(block.getMetaFromState(state), metas);
    }

    private static <T extends Comparable<T>> List<String> valueNames(IProperty<T> property) {
        List<String> names = new ArrayList<String>();
        for (T value : property.getAllowedValues()) {
            names.add(property.getName(value));
        }
        return names;
    }

    /** The property values of a state, as text. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static Map<String, String> propertyValues(IBlockState state) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        for (Map.Entry<IProperty<?>, Comparable<?>> entry : state.getProperties().entrySet()) {
            IProperty property = entry.getKey();
            values.put(property.getName(), property.getName(entry.getValue()));
        }
        return values;
    }

    /** Searches the box and hands one {@code BLOCK_FOUND} record per match to the sink, up to the limit. */
    public static BlockScan.Result search(final WorldServer world, final Block block, final Map<String, String> wanted,
                                          int x1, int y1, int z1, int x2, int y2, int z2, int limit, final Sink sink) {
        final ResourceLocation id = Block.REGISTRY.getNameForObject(block);
        final int dimension = world.provider.getDimension();
        return BlockScan.scan(x1, y1, z1, x2, y2, z2, block == Blocks.AIR, limit,
                new BlockScan.Source<IBlockState>() {
                    @Override
                    public boolean isLoaded(int chunkX, int chunkZ) {
                        return LoadedChunks.get(world.getChunkProvider(), chunkX, chunkZ) != null;
                    }

                    @Override
                    public boolean isSectionEmpty(int chunkX, int chunkZ, int section) {
                        Chunk chunk = LoadedChunks.get(world.getChunkProvider(), chunkX, chunkZ);
                        ExtendedBlockStorage storage = chunk.getBlockStorageArray()[section];
                        // A section emptied by a fill is kept, but counts no blocks.
                        return storage == Chunk.NULL_BLOCK_STORAGE || storage.isEmpty();
                    }

                    @Override
                    public IBlockState stateAt(int chunkX, int chunkZ, int x, int y, int z) {
                        return LoadedChunks.get(world.getChunkProvider(), chunkX, chunkZ).getBlockState(x, y, z);
                    }
                },
                new BlockScan.Visitor<IBlockState>() {
                    @Override
                    public boolean matches(IBlockState state) {
                        if (state.getBlock() != block) {
                            return false;
                        }
                        if (wanted.isEmpty()) {
                            return true;
                        }
                        if (!BlockMatch.matches(wanted, propertyValues(state))) {
                            return false;
                        }
                        // A value this state does not store is only the default, not the
                        // block's: such a state is left out rather than taken as a match.
                        for (String name : wanted.keySet()) {
                            IProperty<?> property = block.getBlockState().getProperty(name);
                            if (property == null || !isStoredAt(state, property)) {
                                return false;
                            }
                        }
                        return true;
                    }

                    @Override
                    public void found(int x, int y, int z, IBlockState state) {
                        LogRecord record = LogRecord.of(EventType.BLOCK_FOUND)
                                .add("block", id == null ? null : id.toString())
                                .add("dimension", dimension);
                        record.addBlockPos("pos", x, y, z);
                        record.add("blockState", state.toString());
                        sink.found(record);
                    }
                });
    }
}
