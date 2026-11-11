package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.command.SubCommand;
import com.mahghuuuls.agenttesttoolkit.inspect.BlockMatch;
import com.mahghuuuls.agenttesttoolkit.inspect.BlockScan;
import com.mahghuuuls.agenttesttoolkit.inspect.BlockSearch;
import com.mahghuuuls.agenttesttoolkit.inspect.FindArgs;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.SessionStamp;
import com.mahghuuuls.agenttesttoolkit.wait.ChunkRegion;
import net.minecraft.block.Block;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Finds a block in a box of loaded chunks: {@code find block <block>[ <state>] <x1> <y1> <z1>
 * <x2> <y2> <z2> [limit <n>]}.
 *
 * <p>Writes one {@code BLOCK_FOUND} per position, up to the limit, then one
 * {@code BLOCK_SEARCH} saying how many were found and how many chunks could not be searched
 * because they are not loaded. Reads only.
 */
public final class FindSubCommand implements SubCommand {

    public static final String KEY_UNKNOWN_BLOCK = "devtool.find.unknownBlock";
    public static final String KEY_BAD_STATE = "devtool.find.badState";
    public static final String KEY_TOO_LARGE = "devtool.find.tooLarge";

    private static final String USAGE = "find block <block> [<property=value,...>] <x1> <y1> <z1> <x2> <y2> <z2> [limit <n>]";

    @Override
    public String getName() {
        return "find";
    }

    @Override
    public String getDescription() {
        return "Find a block in a box of loaded chunks";
    }

    @Override
    public String getUsage() {
        return USAGE;
    }

    @Override
    public boolean requiresPlayer() {
        // Absolute coordinates work from the console; relative ones are checked below.
        return false;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        FindArgs find = FindArgs.parse(args);
        if (find == null) {
            throw new WrongUsageException("/devtool " + USAGE);
        }
        Block block = Block.REGISTRY.containsKey(new ResourceLocation(find.block))
                ? Block.REGISTRY.getObject(new ResourceLocation(find.block)) : null;
        if (block == null) {
            ToolkitLog.error("Unknown block to find", find.block);
            throw new CommandException(KEY_UNKNOWN_BLOCK, find.block);
        }
        Map<String, String> wanted = Collections.emptyMap();
        if (find.state != null) {
            wanted = BlockMatch.parseState(find.state);
            String impossible = wanted == null ? find.state
                    : BlockMatch.firstImpossible(BlockSearch.storedProperties(block), wanted);
            if (impossible != null) {
                String name = impossible.indexOf('=') > 0 ? impossible.substring(0, impossible.indexOf('=')) : impossible;
                String why = BlockSearch.computedProperties(block).contains(name)
                        ? " (not stored in the world: worked out from the neighbours when drawn)" : "";
                ToolkitLog.error("Block state to find is not possible", find.block + " " + impossible + why);
                throw new CommandException(KEY_BAD_STATE, find.block + " " + impossible + why);
            }
        }
        boolean relative = false;
        for (String word : find.coordinates) {
            relative |= word.startsWith("~");
        }
        if (relative && sender.getCommandSenderEntity() == null) {
            // A console's position is the world origin, which would quietly search the wrong box.
            ToolkitLog.error("Relative coordinates require an entity sender", "find block");
            throw new CommandException("Relative coordinates need a player sender. Use absolute coordinates.");
        }
        if (!(sender.getEntityWorld() instanceof WorldServer)) {
            throw new CommandException("find runs on the server.");
        }
        double bx = sender.getPositionVector().x;
        double by = sender.getPositionVector().y;
        double bz = sender.getPositionVector().z;
        String[] c = find.coordinates;
        int x1 = floor(CommandBase.parseCoordinate(bx, c[0], false).getResult());
        int y1 = floor(CommandBase.parseCoordinate(by, c[1], false).getResult());
        int z1 = floor(CommandBase.parseCoordinate(bz, c[2], false).getResult());
        int x2 = floor(CommandBase.parseCoordinate(bx, c[3], false).getResult());
        int y2 = floor(CommandBase.parseCoordinate(by, c[4], false).getResult());
        int z2 = floor(CommandBase.parseCoordinate(bz, c[5], false).getResult());
        if (Math.max(y1, y2) < BlockScan.MIN_Y || Math.min(y1, y2) > BlockScan.MAX_Y) {
            throw new CommandException("The box is outside the world's height, " + BlockScan.MIN_Y
                    + " to " + BlockScan.MAX_Y + ".");
        }

        ChunkRegion region = ChunkRegion.ofBlocks(x1, z1, x2, z2);
        if (!region.isWithinCap()) {
            ToolkitLog.error("Block search box too large", region.chunkCount() + " chunks, at most " + ChunkRegion.MAX_CHUNKS);
            throw new CommandException(KEY_TOO_LARGE, Long.toString(region.chunkCount()));
        }

        final ICommandSender who = sender;
        WorldServer world = (WorldServer) sender.getEntityWorld();
        long started = System.currentTimeMillis();
        BlockScan.Result result = BlockSearch.search(world, block, wanted, x1, y1, z1, x2, y2, z2, find.limit,
                new BlockSearch.Sink() {
                    @Override
                    public void found(LogRecord record) {
                        write(record, who);
                    }
                });
        long elapsed = System.currentTimeMillis() - started;

        LogRecord record = LogRecord.of(EventType.BLOCK_SEARCH)
                .add("block", find.block)
                .add("state", find.state)
                .add("dimension", world.provider.getDimension())
                // The box as searched: the height clamped to the world's.
                .add("from", Math.min(x1, x2) + "," + result.minY + "," + Math.min(z1, z2))
                .add("to", Math.max(x1, x2) + "," + result.maxY + "," + Math.max(z1, z2))
                .add("found", result.found)
                .add("limit", find.limit)
                .add("truncated", result.truncated)
                .add("chunksSearched", result.chunksSearched)
                .add("chunksSkipped", result.chunksSkipped)
                .add("chunksNotRead", result.chunksNotRead)
                .add("elapsedMillis", elapsed);
        write(record, sender);
        sender.sendMessage(new TextComponentString("[DevToolkit] Found " + result.found
                + (result.truncated ? "+" : "") + " " + find.block + "; " + result.chunksSkipped
                + " chunks not loaded and not searched."));
    }

    private static void write(LogRecord record, ICommandSender sender) {
        RecordContext.stamp(record, sender);
        SessionStamp.apply(record);
        ToolkitLog.write(record);
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length <= 1) {
            return CommandBase.getListOfStringsMatchingLastWord(args, "block");
        }
        return Collections.emptyList();
    }
}
