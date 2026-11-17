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
 */
public final class Replies {

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
}
