package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.command.SubCommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

import java.util.Collection;

/**
 * Lists the subcommands this build actually provides.
 *
 * <p>REQ-006 requires help to reflect the installed build rather than the documentation.
 * That is why this reads the live registry instead of a hand maintained list: a feature
 * absent from the build cannot appear here, and a feature added cannot be forgotten.
 */
public final class HelpSubCommand implements SubCommand {

    private final Collection<SubCommand> registry;

    /**
     * @param registry a live view of the root command's registry. Held rather than copied so
     *                 that help lists every subcommand including itself. Safe because
     *                 registration happens once during construction on a single thread and
     *                 the registry is never mutated afterwards.
     */
    public HelpSubCommand(Collection<SubCommand> registry) {
        this.registry = registry;
    }

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public String getDescription() {
        return "List available subcommands";
    }

    @Override
    public String getUsage() {
        return "help";
    }

    @Override
    public boolean requiresPlayer() {
        return false;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        sender.sendMessage(new TextComponentString("[DevToolkit] Available subcommands:"));
        for (SubCommand sub : registry) {
            sender.sendMessage(new TextComponentString("  /devtool " + sub.getUsage() + "  -  " + sub.getDescription()));
        }
    }
}
