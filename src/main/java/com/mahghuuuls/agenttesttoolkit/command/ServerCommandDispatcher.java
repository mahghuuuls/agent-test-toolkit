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

    /**
     * Set for a player caller; null for the console or a command block.
     *
     * <p>ARC-004: the sender is <b>re-resolved every time</b> rather than held. A bundle with
     * delays spans ticks, and a held {@code EntityPlayerMP} for a player who disconnected is
     * either a throw or, worse, a live-looking reference to a ghost that commands execute
     * against. Holding a UUID and looking it up costs a map read and cannot go stale.
     */
    private final java.util.UUID playerId;

    /** Only used when {@link #playerId} is null, where the caller cannot disconnect. */
    private final ICommandSender fixedSender;

    public ServerCommandDispatcher(MinecraftServer server, ICommandSender originalSender) {
        if (server == null) {
            throw new IllegalArgumentException("server must not be null");
        }
        this.server = server;
        if (originalSender instanceof net.minecraft.entity.player.EntityPlayer) {
            this.playerId = ((net.minecraft.entity.player.EntityPlayer) originalSender).getUniqueID();
            this.fixedSender = null;
        } else {
            this.playerId = null;
            this.fixedSender = originalSender;
        }
    }

    /** @return the caller as it is now, or null if they have gone. */
    private ICommandSender resolve() {
        if (playerId == null) {
            return fixedSender;
        }
        return server.getPlayerList() == null
                ? null : server.getPlayerList().getPlayerByUUID(playerId);
    }

    @Override
    public boolean isSenderAvailable() {
        return resolve() != null;
    }

    @Override
    public CommandOutcome dispatch(String command) {
        ICommandSender current = resolve();
        if (current == null) {
            return CommandOutcome.failure("caller is no longer available");
        }
        // Wrapped fresh each dispatch, since the underlying sender may be a different object
        // than last tick. The wrapper only observes; permissions come from the real sender.
        ObservingSender sender = new ObservingSender(current);
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
