package com.mahghuuuls.agenttesttoolkit.command;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

import java.util.Collections;
import java.util.List;

/**
 * One {@code /devtool} subcommand.
 *
 * <p>Subcommands parse arguments and delegate. They do not contain behavior: that lives in
 * the owning subsystem, per the architecture's rule that nothing may depend on the command
 * package and that command classes own dispatch rather than logic.
 */
public interface SubCommand {

    /** The literal typed after {@code /devtool}. */
    String getName();

    /** One line shown by {@code /devtool help}. */
    String getDescription();

    /** Usage string, without the root command. */
    String getUsage();

    /**
     * Whether this subcommand needs a player sender.
     *
     * <p>REQ-005: a subcommand requiring a player must fail with an explicit error when
     * invoked from the console or a command block, rather than failing obscurely later.
     */
    boolean requiresPlayer();

    /**
     * @param args arguments after the subcommand name
     */
    void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException;

    /** Tab completion for this subcommand's own arguments. */
    default List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
