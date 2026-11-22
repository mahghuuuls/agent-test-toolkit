package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.command.Senders;
import com.mahghuuuls.agenttesttoolkit.config.ToolkitConfigLoader;
import com.mahghuuuls.agenttesttoolkit.inspect.EntityTarget;
import com.mahghuuuls.agenttesttoolkit.inspect.LoadedPositions;
import com.mahghuuuls.agenttesttoolkit.inspect.NbtLookup;
import com.mahghuuuls.agenttesttoolkit.inspect.NbtPath;
import com.mahghuuuls.agenttesttoolkit.inspect.Truncation;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

/**
 * Finds the NBT that {@code nbt} and {@code assert nbt} read, and puts the same lookup fields on
 * their records, so a value read by path names its target exactly as the raw NBT record does.
 *
 * <p>Each method takes the arguments from the target's word on ({@code entity <target>},
 * {@code block <x> <y> <z>}, {@code held}).
 */
final class NbtTargets {

    /** What was found, and where the arguments after the target start. */
    static final class Read {
        /** The compound to read; null when there is none: no tile entity, an empty hand, or not loaded. */
        final NBTTagCompound tag;
        /** The index of the first argument after the target's words. */
        final int next;
        /** True when the block's chunk is not loaded and the caller asked to be told rather than refused. */
        final boolean unloaded;

        Read(NBTTagCompound tag, int next, boolean unloaded) {
            this.tag = tag;
            this.next = next;
            this.unloaded = unloaded;
        }
    }

    private NbtTargets() {
    }

    /**
     * How many words name a target, its own word included: 2 for {@code entity <target>}, 4 for
     * {@code block <x> <y> <z>}, 1 for {@code held}; -1 for anything else. Whatever follows them is
     * the path, and for an assertion the value.
     */
    static int words(String target) {
        if ("entity".equals(target)) {
            return 2;
        }
        if ("block".equals(target)) {
            return 4;
        }
        return "held".equals(target) ? 1 : -1;
    }

    /**
     * The arguments from {@code from} on, joined with the single spaces the command line was split
     * on, so a quoted key with a space survives the split.
     */
    static String joinFrom(String[] args, int from) {
        StringBuilder out = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) {
                out.append(' ');
            }
            out.append(args[i]);
        }
        return out.toString();
    }

    /** Reads a path, or fails the command with what is wrong in it. */
    static NbtPath parsePath(String text) throws CommandException {
        try {
            return NbtPath.parse(text);
        } catch (IllegalArgumentException bad) {
            ToolkitLog.error("Bad NBT path", text + ": " + bad.getMessage());
            throw new CommandException("[DevToolkit] Bad NBT path '" + text + "': " + bad.getMessage() + ".");
        }
    }

    /**
     * {@code found}, then {@code type} and {@code value}, or {@code missingAt}; for a compound, a
     * list or an array, {@code truncated} always and {@code limit} when it bit. Shared with
     * {@code assert nbt}, so both records name a value the same way.
     *
     * @param found null when there was nothing to read the path from
     */
    static void addValue(LogRecord record, NbtLookup.Result found) {
        record.add("found", found != null && found.isFound());
        if (found == null) {
            return;
        }
        if (!found.isFound()) {
            record.add("missingAt", found.getMissingAt());
            return;
        }
        record.add("type", found.getType());
        record.add("value", found.getValue());
        Truncation bounded = found.getBounded();
        if (bounded != null) {
            record.add("truncated", bounded.isTruncated());
            if (bounded.isTruncated()) {
                record.add("valueLength", bounded.getOriginalLength());
                record.add("limit", ToolkitConfigLoader.get().getMaxNbtOutputLength());
            }
        }
    }


    static Read entity(MinecraftServer server, ICommandSender sender, String[] args, LogRecord record)
            throws CommandException {
        if (args.length < 2) {
            throw new WrongUsageException("/devtool nbt entity <selector|uuid|entityId>");
        }
        // A selector matching two entities is an error, not an invitation to pick one.
        Entity entity = EntityTarget.requireExactlyOne(server, sender, args[1]);
        NBTTagCompound tag = new NBTTagCompound();
        entity.writeToNBT(tag);
        // How the entity was found. Three lookup forms produce one record shape, and without
        // this a reader cannot tell a selector hit from a direct handle.
        record.add("lookup", EntityTarget.classify(args[1]).recordName());
        record.add("entityId", entity.getEntityId());
        record.add("uuid", entity.getUniqueID().toString());
        // An entity ID is only unique within a dimension, so a record found by ID is
        // ambiguous without this; a UUID lookup gets it for consistency.
        record.add("dimension", entity.dimension);
        return new Read(tag, words("entity"), false);
    }

    /**
     * @param refuseUnloaded true to fail with the inspection's not-loaded key, as raw NBT does;
     *                       false to answer {@link Read#unloaded}, as an assertion needs
     */
    static Read block(ICommandSender sender, String[] args, LogRecord record, boolean refuseUnloaded)
            throws CommandException {
        if (args.length < 4) {
            throw new WrongUsageException("/devtool nbt block <x> <y> <z>");
        }
        BlockPos pos = CommandBase.parseBlockPos(sender, args, 1, false);
        record.addBlockPos("pos", pos.getX(), pos.getY(), pos.getZ());
        if (refuseUnloaded) {
            LoadedPositions.require(sender.getEntityWorld(), pos);
        } else if (!sender.getEntityWorld().isBlockLoaded(pos)) {
            record.add("regionLoaded", false);
            return new Read(null, words("block"), true);
        }
        TileEntity tile = sender.getEntityWorld().getTileEntity(pos);
        if (tile == null) {
            // Stated rather than reported as empty NBT. "No tile entity here" and "a tile
            // entity with no tags" are different facts, and an agent acting on the wrong one
            // would look for a bug that does not exist.
            record.add("hasTileEntity", false);
            return new Read(null, words("block"), false);
        }
        NBTTagCompound tag = new NBTTagCompound();
        tile.writeToNBT(tag);
        record.add("hasTileEntity", true);
        record.add("tileEntityClass", tile.getClass().getName());
        return new Read(tag, words("block"), false);
    }

    /**
     * @param wholeStack true for the stack as the game saves it ({@code id}, {@code Count},
     *                   {@code Damage}, {@code tag}, {@code ForgeCaps}), which a path reads;
     *                   false for only its {@code tag}, which raw {@code nbt held} has always written
     */
    static Read held(ICommandSender sender, LogRecord record, boolean wholeStack) throws CommandException {
        EntityPlayer player = Senders.asPlayer(sender);
        if (player == null) {
            throw new CommandException(
                    "This command must be run by a player, or by a bundle a player started.");
        }
        ItemStack stack = player.getHeldItemMainhand();
        if (stack == null || stack.isEmpty()) {
            record.add("empty", true);
            return new Read(null, words("held"), false);
        }
        record.add("item", net.minecraft.item.Item.REGISTRY.getNameForObject(stack.getItem()).toString());
        record.add("count", stack.getCount());
        record.add("meta", stack.getMetadata());
        if (wholeStack) {
            return new Read(stack.writeToNBT(new NBTTagCompound()), words("held"), false);
        }
        NBTTagCompound tag = stack.getTagCompound();
        return new Read(tag == null ? new NBTTagCompound() : tag, words("held"), false);
    }
}
