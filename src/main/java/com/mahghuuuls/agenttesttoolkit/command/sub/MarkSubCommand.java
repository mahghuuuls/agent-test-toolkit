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
 * They assert nothing and carry no verdict: the toolkit records facts and leaves conclusions
 * to the reader.
 *
 * <p>A marker never waits and never speaks to the human beyond confirming itself. The
 * checkpoint command is the one that cues the operator and can pause a bundle; keeping the
 * two apart is what lets "marker" keep meaning "bookmark".
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
        // builder quotes it when it contains whitespace.
        StringBuilder label = new StringBuilder(args[0]);
        for (int i = 1; i < args.length; i++) {
            label.append(' ').append(args[i]);
        }

        // Side and world tick lead every record, in that order.
        LogRecord record = RecordContext.stamp(LogRecord.of(EventType.MARK), sender);

        // The session name and tick are stamped only while a session is active. With none
        // active both fields are omitted entirely rather than filled with a placeholder, and
        // the marker still works, which is the point: a marker must never depend on a session.
        SessionStamp.apply(record);

        record.add("label", label.toString());

        ToolkitLog.write(record);
        sender.sendMessage(new TextComponentString(confirmation(label.toString())));
    }

    /**
     * The chat line sent back after the record is written.
     *
     * <p>Carries the label verbatim. A bundle written for a human in the loop encodes the
     * next manual action in the label, and a confirmation that omitted it left the operator
     * unable to tell which of several markers had just fired without reading the log, which
     * for a timed action is too late. Separated from {@link #execute} so the text can be
     * checked without a sender.
     */
    static String confirmation(String label) {
        return "[DevToolkit] Marker written to log: " + label;
    }
}
