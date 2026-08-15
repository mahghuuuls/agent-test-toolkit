package com.mahghuuuls.agenttesttoolkit.command;

import com.mahghuuuls.agenttesttoolkit.bundle.CommandDispatcher;
import com.mahghuuuls.agenttesttoolkit.bundle.CommandOutcome;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

/**
 * Runs bundle commands as the original caller.
 *
 * <p>This class owns <b>who</b> a command runs as across the life of a bundle, which spans
 * ticks. {@link CommandRunner} owns <b>how</b> one command is run. Keeping them apart means the
 * sender-lifetime rule below is readable without also reading dispatch.
 *
 * <p>The caller's own object is passed through, never replaced. Permission context is preserved
 * by construction, and so is sender identity: a vanilla command that inspects its sender's
 * concrete type sees exactly what it would have seen had the caller typed the command.
 */
public final class ServerCommandDispatcher implements CommandDispatcher {

    private final MinecraftServer server;

    private final CommandRunner runner;

    /**
     * Set for a player caller; null for the console or a command block.
     *
     * <p>The sender is <b>re-resolved every time</b> rather than held. A bundle with
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
        this.runner = new CommandRunner(server);
        // Senders rather than a bare instanceof. The sender reaching a nested bundle is whatever
        // the parent was constructed with, and resolving it through one helper keeps this
        // correct without depending on what that happens to be. Getting it wrong would hold a
        // fixed sender whose UUID is never re-resolved, so isSenderAvailable would keep
        // answering true after the player had gone, which is what re-resolving exists to prevent.
        net.minecraft.entity.player.EntityPlayer player = Senders.asPlayer(originalSender);
        if (player != null) {
            this.playerId = player.getUniqueID();
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
        return dispatch(command, java.util.Collections.<String>emptySet());
    }

    @Override
    public CommandOutcome dispatch(String command, java.util.Collection<String> tolerated) {
        ICommandSender current = resolve();
        if (current == null) {
            return CommandOutcome.failure("caller is no longer available");
        }
        // The caller's own object, resolved fresh, handed straight to the runner. Nothing
        // wraps or substitutes it, which is what lets a command inspect its sender's concrete
        // type and see the truth.
        return runner.run(current, command, tolerated);
    }
}
