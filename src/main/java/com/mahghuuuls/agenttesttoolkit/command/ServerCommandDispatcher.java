package com.mahghuuuls.agenttesttoolkit.command;

import com.mahghuuuls.agenttesttoolkit.bundle.CommandDispatcher;
import com.mahghuuuls.agenttesttoolkit.bundle.CommandOutcome;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

/**
 * Runs bundle commands through the server's own command manager, as the original caller.
 *
 * <p>The caller is passed through rather than replaced, wrapped only for observation, so
 * permission context is preserved by construction. A bundle cannot run a command its caller
 * could not have typed, and no code here has to remember to check that.
 */
public final class ServerCommandDispatcher implements CommandDispatcher {

    private final MinecraftServer server;
    private final ObservingSender sender;

    public ServerCommandDispatcher(MinecraftServer server, ICommandSender originalSender) {
        if (server == null) {
            throw new IllegalArgumentException("server must not be null");
        }
        this.server = server;
        this.sender = new ObservingSender(originalSender);
    }

    @Override
    public CommandOutcome dispatch(String command) {
        sender.reset();
        try {
            int count = server.getCommandManager().executeCommand(sender, command);
            return CommandOutcomes.classify(count, sender.getLastTranslationKey());
        } catch (RuntimeException e) {
            // executeCommand swallows CommandException but not unchecked throwables, and the
            // Forge CommandEvent path rethrows them deliberately. A misbehaving command from
            // any mod would otherwise propagate into the server tick and take the world down,
            // which is a poor trade for a diagnostic tool. Reported as a failure instead.
            return CommandOutcome.failure(e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        }
    }
}
