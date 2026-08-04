package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.command.SubCommand;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.SessionStamp;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

/**
 * Emits a labelled marker record.
 *
 * <p>Markers are navigation aids: a human or an agent drops one immediately before the
 * action under test so the interesting region of {@code latest.log} can be found later.
 * They assert nothing and carry no verdict, per REQ-054 and the project's facts-not-conclusions
 * boundary.
 */
public final class MarkSubCommand implements SubCommand {

    @Override
    public String getName() {
        return "mark";
    }

    @Override
    public String getDescription() {
        return "Write a labelled marker to the log";
    }

    @Override
    public String getUsage() {
        return "mark <label>";
    }

    @Override
    public boolean requiresPlayer() {
        // A marker is a log entry, not a world action. The console and command blocks may
        // emit one, which is what lets a bundle drop markers around a setup sequence.
        return false;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            throw new WrongUsageException("/devtool " + getUsage());
        }

        // Join rather than take args[0], so a multi word label is preserved. The record
        // builder quotes it when it contains whitespace, per REQ-033.
        StringBuilder label = new StringBuilder(args[0]);
        for (int i = 1; i < args.length; i++) {
            label.append(' ').append(args[i]);
        }

        // REQ-041 and REQ-043: side and world tick lead every record, in that order.
        LogRecord record = RecordContext.stamp(LogRecord.of(EventType.MARK), sender);

        // REQ-053 and REQ-054: the session name and tick are stamped only while a session is
        // active. With none active both fields are omitted entirely rather than filled with a
        // placeholder, and the marker still works, which is the point of REQ-054.
        SessionStamp.apply(record);

        record.add("label", label.toString());

        ToolkitLog.write(record);
        sender.sendMessage(new TextComponentString("[DevToolkit] Marker written to log."));
    }
}
