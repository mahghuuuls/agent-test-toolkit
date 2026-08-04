package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.command.SubCommand;
import com.mahghuuuls.agenttesttoolkit.config.ToolkitConfigLoader;
import com.mahghuuuls.agenttesttoolkit.inspect.EntityTarget;
import com.mahghuuuls.agenttesttoolkit.inspect.Truncation;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.SessionStamp;
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
import net.minecraft.util.text.TextComponentString;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Writes raw NBT to the log, literally and without interpretation.
 *
 * <p>REQ-075 forbids comparing, diffing or judging NBT, and that is an identity decision rather
 * than a simplification: the toolkit reports facts, and deciding whether a tag is "correct"
 * requires knowing what the mod under test intended.
 *
 * <p>Output goes to the log, never to chat. A shulker box or a written book carries enough NBT
 * to flood a chat window and push everything useful out of scroll history; chat gets a one line
 * confirmation with the lengths, which is what tells the operator where to look.
 */
public final class NbtSubCommand implements SubCommand {

    private static final List<String> TARGETS = Arrays.asList("entity", "block", "held");

    @Override
    public String getName() {
        return "nbt";
    }

    @Override
    public String getDescription() {
        return "Write raw NBT for an entity, block, or held item to the log";
    }

    @Override
    public String getUsage() {
        return "nbt <entity <selector> | block <x> <y> <z> | held>";
    }

    @Override
    public boolean requiresPlayer() {
        // Only `held` needs a player; entity and block do not, so the requirement is checked
        // per target rather than refusing the console outright.
        return false;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (args.length == 0) {
            throw new WrongUsageException("/devtool " + getUsage());
        }

        String target = args[0].toLowerCase(Locale.ROOT);
        if ("entity".equals(target)) {
            entity(server, sender, args);
        } else if ("block".equals(target)) {
            block(sender, args);
        } else if ("held".equals(target)) {
            held(sender);
        } else {
            throw new WrongUsageException("/devtool " + getUsage());
        }
    }

    private void entity(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (args.length < 2) {
            throw new WrongUsageException("/devtool nbt entity <selector>");
        }
        // REQ-070 applies here: a selector matching two entities is an error, not an
        // invitation to pick one.
        Entity entity = EntityTarget.requireExactlyOne(server, sender, args[1]);
        NBTTagCompound tag = new NBTTagCompound();
        entity.writeToNBT(tag);

        LogRecord record = begin(sender, "entity");
        record.add("entityId", entity.getEntityId());
        record.add("uuid", entity.getUniqueID().toString());
        emit(sender, record, tag);
    }

    private void block(ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 4) {
            throw new WrongUsageException("/devtool nbt block <x> <y> <z>");
        }
        BlockPos pos = CommandBase.parseBlockPos(sender, args, 1, false);
        TileEntity tile = sender.getEntityWorld().getTileEntity(pos);

        LogRecord record = begin(sender, "block");
        record.addBlockPos("pos", pos.getX(), pos.getY(), pos.getZ());

        if (tile == null) {
            // Stated rather than reported as empty NBT. "No tile entity here" and "a tile
            // entity with no tags" are different facts, and an agent acting on the wrong one
            // would look for a bug that does not exist.
            record.add("hasTileEntity", false);
            SessionStamp.apply(record);
            ToolkitLog.write(record);
            sender.sendMessage(new TextComponentString(
                    "[DevToolkit] No tile entity at that position. Recorded."));
            return;
        }

        NBTTagCompound tag = new NBTTagCompound();
        tile.writeToNBT(tag);
        record.add("hasTileEntity", true);
        record.add("tileEntityClass", tile.getClass().getName());
        emit(sender, record, tag);
    }

    private void held(ICommandSender sender) throws CommandException {
        EntityPlayer player = CommandBase.getCommandSenderAsPlayer(sender);
        ItemStack stack = player.getHeldItemMainhand();

        LogRecord record = begin(sender, "held");
        if (stack == null || stack.isEmpty()) {
            record.add("empty", true);
            SessionStamp.apply(record);
            ToolkitLog.write(record);
            sender.sendMessage(new TextComponentString(
                    "[DevToolkit] Main hand is empty. Recorded."));
            return;
        }

        record.add("item", net.minecraft.item.Item.REGISTRY
                .getNameForObject(stack.getItem()).toString());
        record.add("count", stack.getCount());
        record.add("meta", stack.getMetadata());

        NBTTagCompound tag = stack.getTagCompound();
        emit(sender, record, tag == null ? new NBTTagCompound() : tag);
    }

    private LogRecord begin(ICommandSender sender, String target) {
        LogRecord record = RecordContext.stamp(LogRecord.of(EventType.NBT), sender);
        record.add("target", target);
        return record;
    }

    /** Applies the configured bound, reports it when it bites, and writes the record. */
    private void emit(ICommandSender sender, LogRecord record, NBTTagCompound tag) {
        SessionStamp.apply(record);

        int limit = ToolkitConfigLoader.get().getMaxNbtOutputLength();
        Truncation bounded = Truncation.of(tag.toString(), limit);

        record.add("nbtLength", bounded.getOriginalLength());
        record.add("outputLength", bounded.getOutputLength());
        // REQ-076: never silent. The flag is always emitted, not only when true, so its
        // absence can never be mistaken for "not truncated".
        record.add("truncated", bounded.isTruncated());
        if (bounded.isTruncated()) {
            record.add("limit", limit);
        }
        record.add("nbt", bounded.getText());
        ToolkitLog.write(record);

        String message = "[DevToolkit] NBT written to log (" + bounded.getOriginalLength()
                + " chars" + (bounded.isTruncated()
                ? ", truncated to " + bounded.getOutputLength() : "") + ").";
        sender.sendMessage(new TextComponentString(message));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                          String[] args) {
        if (args.length <= 1) {
            return CommandBase.getListOfStringsMatchingLastWord(args, TARGETS);
        }
        return Collections.emptyList();
    }
}
