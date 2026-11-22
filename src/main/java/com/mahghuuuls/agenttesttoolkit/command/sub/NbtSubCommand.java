package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.bundle.Replies;
import com.mahghuuuls.agenttesttoolkit.command.SubCommand;
import com.mahghuuuls.agenttesttoolkit.config.ToolkitConfigLoader;
import com.mahghuuuls.agenttesttoolkit.inspect.NbtLookup;
import com.mahghuuuls.agenttesttoolkit.inspect.NbtPath;
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
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Writes raw NBT to the log, literally and without interpretation.
 *
 * <p>NBT is never diffed or judged here, and that is an identity decision rather than a
 * simplification: the toolkit reports facts, and deciding whether a tag is "correct" requires
 * knowing what the mod under test intended. Only {@code assert nbt} compares, and only one value
 * against the value a bundle declared.
 *
 * <p>Output goes to the log, never to chat. A shulker box or a written book carries enough NBT
 * to flood a chat window and push everything useful out of scroll history; chat gets a one line
 * confirmation with the lengths, which is what tells the operator where to look.
 *
 * <p>With {@code path=} it writes one value instead, as an {@code NBT_VALUE} record: the path's
 * grammar is {@link NbtPath}'s. A held item's path is read from the whole saved stack, so
 * {@code Damage} and capabilities are reachable, while raw {@code nbt held} keeps writing only
 * the stack's {@code tag}, as it always has.
 */
public final class NbtSubCommand implements SubCommand {

    private static final List<String> TARGETS = Arrays.asList("entity", "block", "held");
    private static final String PATH = "path=";
    /** A value longer than this is named by its length in chat; the record always has it. */
    private static final int CHAT_VALUE_LENGTH = 80;

    @Override
    public String getName() {
        return "nbt";
    }

    @Override
    public String getDescription() {
        return "Write raw NBT, or one value by path, for an entity, block, or held item to the log";
    }

    @Override
    public String getUsage() {
        return "nbt <entity <selector|uuid|entityId> | block <x> <y> <z> | held> [path=<path>]";
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
        int pathAt = pathIndex(target, args);
        boolean byPath = pathAt > 0;
        // Read before the target, so a mistyped path fails without a lookup half recorded.
        NbtPath path = byPath ? NbtTargets.parsePath(NbtTargets.joinFrom(args, pathAt).substring(PATH.length())) : null;
        LogRecord record = begin(sender, target, byPath ? EventType.NBT_VALUE : EventType.NBT);
        NbtTargets.Read read;
        if ("entity".equals(target)) {
            read = NbtTargets.entity(server, sender, args, record);
        } else if ("block".equals(target)) {
            read = NbtTargets.block(sender, args, record, true);
        } else if ("held".equals(target)) {
            read = NbtTargets.held(sender, record, byPath);
        } else {
            throw new WrongUsageException("/devtool " + getUsage());
        }

        if (byPath) {
            emitValue(sender, record, read.tag, path);
            return;
        }
        if (args.length > read.next) {
            // Raw NBT takes nothing after its target. A mistyped path= would otherwise write the
            // whole compound and look like an answer.
            throw new WrongUsageException("/devtool " + getUsage());
        }
        if (read.tag == null) {
            SessionStamp.apply(record);
            ToolkitLog.write(record);
            Replies.send(sender, "block".equals(target)
                    ? "[DevToolkit] No tile entity at that position. Recorded."
                    : "[DevToolkit] Main hand is empty. Recorded.");
            return;
        }
        emit(sender, record, read.tag);
    }

    /** Where {@code path=} starts: the argument after the target's words when it begins so, else -1. */
    private static int pathIndex(String target, String[] args) {
        int next = NbtTargets.words(target);
        return next > 0 && args.length > next && args[next].toLowerCase(Locale.ROOT).startsWith(PATH) ? next : -1;
    }

    /** The {@code NBT_VALUE} record: the lookup fields, the path, and what it found. */
    private void emitValue(ICommandSender sender, LogRecord record, NBTTagCompound tag, NbtPath path) {
        SessionStamp.apply(record);
        record.add("path", path.toString());
        NbtLookup.Result found = tag == null ? null
                : NbtLookup.resolve(tag, path, ToolkitConfigLoader.get().getMaxNbtOutputLength());
        NbtTargets.addValue(record, found);
        ToolkitLog.write(record);
        if (found == null) {
            Replies.send(sender, "[DevToolkit] Nothing to read the path from. Recorded.");
        } else if (!found.isFound()) {
            Replies.send(sender, "[DevToolkit] " + path + " not found (stopped at " + found.getMissingAt() + "). Recorded.");
        } else {
            String value = found.getValue();
            Replies.send(sender, "[DevToolkit] " + path + " = " + (value.length() <= CHAT_VALUE_LENGTH ? value
                    : "(" + value.length() + " chars, in the log)") + " (" + found.getType() + ").");
        }
    }

    private LogRecord begin(ICommandSender sender, String target, EventType type) {
        LogRecord record = RecordContext.stamp(LogRecord.of(type), sender);
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
        // Never silent. The flag is always emitted, not only when true, so its
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
        Replies.send(sender, message);
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
