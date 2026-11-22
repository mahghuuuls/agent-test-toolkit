package com.mahghuuuls.agenttesttoolkit.command;

import com.mahghuuuls.agenttesttoolkit.net.ToolkitChannel;
import com.mahghuuuls.agenttesttoolkit.net.ToolkitNotice;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

/**
 * Says a quiet run's or a quiet request's failure: the one line that passes the quiet rule, so a
 * failure is never kept out of sight.
 *
 * <p>Sent to the chat in red, and to a player's client as a notice too, so a client that hides
 * its chat history can still show it.
 */
public final class FailureLine {

    /** Where the notice of one failure goes; null for a sender with no client of its own. */
    interface NoticeSink {
        void send(ToolkitNotice notice);
    }

    private FailureLine() {
    }

    /**
     * Whether a sender of this class has a client to send a notice to: a player. Asked of the
     * class so the rule is tested without building a player, which needs a running game.
     */
    static boolean getsNotices(Class<?> senderClass) {
        return EntityPlayerMP.class.isAssignableFrom(senderClass);
    }

    /** Sends the line in red, and as a notice to a player's client. */
    public static void announce(ICommandSender sender, String line) {
        announce(sender, line, noticesFor(sender));
    }

    static void announce(ICommandSender sender, String line, NoticeSink notices) {
        if (sender == null) {
            return;
        }
        TextComponentString text = new TextComponentString(line);
        text.getStyle().setColor(TextFormatting.RED);
        sender.sendMessage(text);
        if (notices != null) {
            notices.send(ToolkitNotice.failure(line));
        }
    }

    /** Where a sender's notices go: a player's client; null for anything else. */
    static NoticeSink noticesFor(ICommandSender sender) {
        if (sender == null || !getsNotices(sender.getClass())) {
            return null;
        }
        final EntityPlayerMP player = (EntityPlayerMP) sender;
        return new NoticeSink() {
            @Override
            public void send(ToolkitNotice notice) {
                // The channel sends only to a client that said hello at 1.10.0 or later; an older
                // one has no handler for a notice and would drop the connection on it.
                ToolkitChannel.send(notice, player);
            }
        };
    }
}
