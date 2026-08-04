package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.command.SubCommand;
import com.mahghuuuls.agenttesttoolkit.env.Environment;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

import java.util.List;

/**
 * Lists every loaded mod to the log. REQ-081.
 *
 * <p>Separate from {@code environment} because the intended runtimes carry 350 to 389 mods.
 * Folding several hundred entries into the environment summary would make the summary useless
 * for the quick question it exists to answer.
 *
 * <p>Log only, never chat. Several hundred chat lines would push everything else out of the
 * scroll history, destroying the context an operator was reading.
 */
public final class ModsSubCommand implements SubCommand {

    @Override
    public String getName() {
        return "mods";
    }

    @Override
    public String getDescription() {
        return "Write every loaded mod id and version to the log";
    }

    @Override
    public String getUsage() {
        return "mods";
    }

    @Override
    public boolean requiresPlayer() {
        return false;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        List<LogRecord> records = Environment.modList(sender);
        for (LogRecord record : records) {
            ToolkitLog.write(record);
        }
        sender.sendMessage(new TextComponentString(
                "[DevToolkit] " + records.size() + " mod records written to log."));
    }
}
