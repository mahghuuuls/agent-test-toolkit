package com.mahghuuuls.agenttesttoolkit.command;

import com.mahghuuuls.agenttesttoolkit.bundle.RunOrigin;
import net.minecraft.command.ICommandSender;
import net.minecraft.network.rcon.RConConsoleSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.CommandBlockBaseLogic;

/**
 * Decides which channel a top-level bundle run arrived through.
 *
 * <p>The sender's type answers it for chat, the console and command blocks. Only a player who
 * is the sender itself counts as chat: a sender wrapped by {@code /execute} or a function is
 * {@code other}, because the wrapper's entity says nothing about who typed it, and claiming
 * {@code chat} for a command block's {@code execute @p} would be a false authority claim.
 *
 * <p>The command inbox runs requests with a player as sender, so the type alone would say
 * {@code chat}; the inbox dispatch sets an override for the span of one request through
 * {@link #during}. The override is read on the server thread only: {@code devtool run} refuses
 * to start a bundle from any other thread, which is where RCON runs its commands.
 */
public final class RunChannel {

    private static String override;
    private static String requestIdOverride;

    private RunChannel() {
    }

    /** The channel for this sender, honouring an active override. */
    public static String of(ICommandSender sender) {
        boolean player = sender instanceof net.minecraft.entity.player.EntityPlayer;
        boolean console = sender instanceof MinecraftServer || sender instanceof RConConsoleSource;
        boolean commandBlock = sender instanceof CommandBlockBaseLogic;
        return classify(override, player, console, commandBlock);
    }

    /** The decision itself, separated from the type checks so it can be tested without a game. */
    static String classify(String override, boolean player, boolean console, boolean commandBlock) {
        if (override != null) {
            return override;
        }
        if (player) {
            return RunOrigin.CHAT;
        }
        if (console) {
            return RunOrigin.CONSOLE;
        }
        if (commandBlock) {
            return RunOrigin.COMMAND_BLOCK;
        }
        return RunOrigin.OTHER;
    }

    /**
     * Sets the channel for runs started until the matching {@link #restore}.
     *
     * @return the previous override, to hand back to {@link #restore}
     */
    static String push(String channel) {
        String previous = override;
        override = channel;
        return previous;
    }

    /** Puts back what {@link #push} returned. Call it in a {@code finally}. */
    static void restore(String previous) {
        override = previous;
    }

    /** Work that may fail as a command fails. */
    public interface Action<T> {
        T run() throws net.minecraft.command.CommandException;
    }

    /** The id of the inbox request being dispatched, or null outside one. Server thread only. */
    public static String requestId() {
        return requestIdOverride;
    }

    /**
     * Runs the action with the channel overridden and the dispatching request's id set, so a
     * run it starts can name the request in its records and result file. Both are restored.
     */
    public static <T> T during(String channel, String requestId, Action<T> action)
            throws net.minecraft.command.CommandException {
        String previous = requestIdOverride;
        requestIdOverride = requestId;
        try {
            return during(channel, action);
        } finally {
            requestIdOverride = previous;
        }
    }

    /** Runs the action with the channel overridden, and always restores it. */
    public static <T> T during(String channel, Action<T> action)
            throws net.minecraft.command.CommandException {
        String previous = push(channel);
        try {
            return action.run();
        } finally {
            restore(previous);
        }
    }
}
