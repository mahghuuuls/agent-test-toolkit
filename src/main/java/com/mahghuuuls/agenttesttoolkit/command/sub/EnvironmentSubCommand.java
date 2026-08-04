package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.command.SubCommand;
import com.mahghuuuls.agenttesttoolkit.env.Environment;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

/**
 * Reports what the toolkit is running inside. REQ-080.
 *
 * <p>The mod list is not included. The runtimes this targets carry several hundred mods, and
 * folding them in would make the summary unusable for the case it exists to serve. See
 * {@link ModsSubCommand}.
 */
public final class EnvironmentSubCommand implements SubCommand {

    @Override
    public String getName() {
        return "environment";
    }

    @Override
    public String getDescription() {
        return "Report Minecraft, Forge, dimension, difficulty, and position";
    }

    @Override
    public String getUsage() {
        return "environment";
    }

    @Override
    public boolean requiresPlayer() {
        // Useful from the console too; the position fields are simply omitted there.
        return false;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        ToolkitLog.write(Environment.environment(server, sender));
        sender.sendMessage(new TextComponentString("[DevToolkit] Environment written to log."));
    }
}
