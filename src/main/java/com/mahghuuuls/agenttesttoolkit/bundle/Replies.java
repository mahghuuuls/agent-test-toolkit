package com.mahghuuuls.agenttesttoolkit.bundle;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

/**
 * The one way the toolkit answers a command in chat.
 *
 * <p>Inside a quiet bundle run the line is counted against the run and dropped, so the toolkit's
 * own chatter cannot cover the screen an agent asked to keep clear; everywhere else it is sent as
 * before. Every reply goes through here, so the rule is in one place rather than at each call.
 * What a command asks to show the person on purpose, such as a checkpoint cue in chat, is not a
 * reply and does not come here.
 *
 * <p>The one line that passes the quiet rule, a quiet run's or a quiet request's failure, is
 * worded by {@link #failureLine} and sent by the command code, never through {@link #send}.
 */
public final class Replies {

    /** The longest failure detail a failure line shows; a toolkit message can be a sentence. */
    static final int MAX_FAILURE_DETAIL = 120;

    private Replies() {
    }

    /** Sends a plain reply, or counts and drops it inside a quiet run. */
    public static void send(ICommandSender sender, String text) {
        send(sender, new TextComponentString(text));
    }

    /**
     * Sends a reply, or counts and drops it inside a quiet run.
     *
     * @throws NullPointerException for a null sender, inside a quiet run too, as sending to it
     *                              directly always did, so a caller that lost its sender is found
     *                              rather than hidden by the quiet rule
     */
    public static void send(ICommandSender sender, ITextComponent message) {
        if (sender == null) {
            throw new NullPointerException("a reply needs a sender");
        }
        if (QuietScope.suppress(1)) {
            return;
        }
        sender.sendMessage(message);
    }

    /**
     * The failure line of quiet work: what failed, then its key or message, cut so the line stays
     * short. The one wording for a quiet run's command and for a single inbox request.
     *
     * @param what the failed work as the person reads it, such as {@code Bundle 'b' command 2}
     */
    public static String failureLine(String what, String detail) {
        String shown = detail == null || detail.isEmpty() ? "failed" : detail;
        if (shown.length() > MAX_FAILURE_DETAIL) {
            shown = shown.substring(0, MAX_FAILURE_DETAIL) + "...";
        }
        return "[DevToolkit] " + what + " failed: " + shown;
    }
}
