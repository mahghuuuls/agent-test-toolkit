package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.Tags;
import com.mahghuuuls.agenttesttoolkit.command.SubCommand;
import com.mahghuuuls.agenttesttoolkit.env.Capabilities;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.SessionStamp;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Reports what this build supports. REQ-082.
 *
 * <p>Everything reported is read from the live registry and the enums the rest of the code
 * uses, never from a hand-written list. That is the whole value: a capabilities command that
 * can drift from the build is worse than none, because it stays confidently wrong and an agent
 * has no independent way to check it.
 *
 * <p>Takes the registered subcommand names from the root command, the same way the help
 * subcommand does, so adding or removing a command changes this output automatically.
 */
public final class CapabilitiesSubCommand implements SubCommand {

    private final Collection<SubCommand> registered;

    public CapabilitiesSubCommand(Collection<SubCommand> registered) {
        this.registered = registered;
    }

    @Override
    public String getName() {
        return "capabilities";
    }

    @Override
    public String getDescription() {
        return "Report this build's version, commands, and logging categories";
    }

    @Override
    public String getUsage() {
        return "capabilities";
    }

    @Override
    public boolean requiresPlayer() {
        return false;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        List<String> commandNames = new ArrayList<String>();
        for (SubCommand sub : registered) {
            commandNames.add(sub.getName());
        }

        // Context first so side and worldTick lead the record, then the capability fields.
        LogRecord record = RecordContext.stamp(
                LogRecord.of(EventType.CAPABILITIES), sender);
        SessionStamp.apply(record);
        Capabilities.record(record, Tags.VERSION, commandNames, InspectSubCommand.TARGETS);
        ToolkitLog.write(record);

        sender.sendMessage(new TextComponentString("[DevToolkit] " + Tags.MOD_NAME + " "
                + Tags.VERSION + ", " + commandNames.size() + " commands. Written to log."));
    }
}
