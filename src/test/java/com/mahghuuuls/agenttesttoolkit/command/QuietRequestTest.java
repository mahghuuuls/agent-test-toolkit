package com.mahghuuuls.agenttesttoolkit.command;

import com.mahghuuuls.agenttesttoolkit.bundle.CommandOutcome;
import com.mahghuuuls.agenttesttoolkit.bundle.QuietScope;
import com.mahghuuuls.agenttesttoolkit.bundle.Replies;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A single inbox request's chat: its replies are kept out, as a quiet run's are, and a failure
 * still reaches the sender as one red line. Success and failure come from the command's outcome.
 */
class QuietRequestTest {

    private static final class KeepingSender implements ICommandSender {
        final List<ITextComponent> received = new ArrayList<ITextComponent>();

        @Override
        public String getName() {
            return "keeper";
        }

        @Override
        public boolean canUseCommand(int permLevel, String commandName) {
            return true;
        }

        @Override
        public net.minecraft.world.World getEntityWorld() {
            return null;
        }

        @Override
        public MinecraftServer getServer() {
            return null;
        }

        @Override
        public void sendMessage(ITextComponent component) {
            received.add(component);
        }
    }

    @AfterEach
    void leaveAll() {
        while (QuietScope.depth() > 0) {
            QuietScope.exit();
        }
    }

    @Test
    @DisplayName("a request that succeeds sends no chat reply, and the scope is left after it")
    void successIsSilent() {
        final KeepingSender sender = new KeepingSender();
        CommandOutcome outcome = InboxDispatch.runQuietly(sender, "reload", new RunChannel.Action<CommandOutcome>() {
            @Override
            public CommandOutcome run() {
                Replies.send(sender, "[DevToolkit] Reloaded.");
                return CommandOutcome.success();
            }
        });
        assertTrue(outcome.succeeded());
        assertEquals(new ArrayList<ITextComponent>(), sender.received);
        assertEquals(0, QuietScope.depth(), "a reply after the request is not silenced by it");
        Replies.send(sender, "after");
        assertEquals(1, sender.received.size());
    }

    @Test
    @DisplayName("a failing request drops its own replies and sends one red failure line")
    void failureKeepsOneLine() {
        final KeepingSender sender = new KeepingSender();
        CommandOutcome outcome = InboxDispatch.runQuietly(sender, "client close", new RunChannel.Action<CommandOutcome>() {
            @Override
            public CommandOutcome run() {
                Replies.send(sender, "[DevToolkit] No client action: keeper has not allowed client actions");
                return CommandOutcome.failure("devtool.client.notAllowed");
            }
        });
        assertFalse(outcome.succeeded());
        assertEquals(1, sender.received.size(), "the reply is dropped, the failure line is not");
        assertEquals("[DevToolkit] Inbox request 'client close' failed: devtool.client.notAllowed",
                sender.received.get(0).getUnformattedText());
        assertEquals(TextFormatting.RED, sender.received.get(0).getStyle().getColor());
    }

    @Test
    @DisplayName("a single request runs in the request scope, a run does not")
    void singleRequestsRunQuietly() throws CommandException {
        final KeepingSender sender = new KeepingSender();
        final boolean[] inRequestScope = new boolean[1];
        RunChannel.Action<CommandOutcome> dispatch = new RunChannel.Action<CommandOutcome>() {
            @Override
            public CommandOutcome run() {
                inRequestScope[0] = QuietScope.isRequest();
                Replies.send(sender, "[DevToolkit] Reloaded.");
                return CommandOutcome.success();
            }
        };
        assertTrue(InboxDispatch.forRequest(false, sender, "reload", dispatch).run().succeeded());
        assertTrue(inRequestScope[0], "the command ran inside the request scope");
        assertEquals(new ArrayList<ITextComponent>(), sender.received, "its reply stayed out of the chat");

        InboxDispatch.forRequest(true, sender, "run", dispatch).run();
        assertFalse(inRequestScope[0], "a run decides its own quietness when it starts");
        assertEquals(1, sender.received.size());
    }

    @Test
    @DisplayName("a client request is named with its step, as its late failure line names it")
    void requestNames() {
        assertEquals("client close", InboxDispatch.requestName("client", "devtool client close"));
        assertEquals("client hold", InboxDispatch.requestName("client", "devtool client hold use 40 delay=5"));
        assertEquals("reload", InboxDispatch.requestName("reload", "devtool reload"));
        assertEquals("inspect", InboxDispatch.requestName("inspect", "devtool inspect player"));
        assertEquals("client", InboxDispatch.requestName("client", "devtool client"), "no step to name");
    }

    @Test
    @DisplayName("a player gets notices, a sender without a client of its own does not")
    void whoGetsNotices() {
        assertTrue(FailureLine.getsNotices(net.minecraft.entity.player.EntityPlayerMP.class));
        assertFalse(FailureLine.getsNotices(KeepingSender.class));
        assertEquals(null, FailureLine.noticesFor(new KeepingSender()));
    }

    @Test
    @DisplayName("a failure line also goes to the sender's client as a ten-second notice")
    void announceSendsNotice() {
        KeepingSender sender = new KeepingSender();
        final List<com.mahghuuuls.agenttesttoolkit.net.ToolkitNotice> notices =
                new ArrayList<com.mahghuuuls.agenttesttoolkit.net.ToolkitNotice>();
        FailureLine.announce(sender, "[DevToolkit] Inbox request 'client close' failed: x", new FailureLine.NoticeSink() {
            @Override
            public void send(com.mahghuuuls.agenttesttoolkit.net.ToolkitNotice notice) {
                notices.add(notice);
            }
        });
        assertEquals(1, sender.received.size(), "the red chat line");
        assertEquals(1, notices.size(), "and the notice");
        assertEquals("[DevToolkit] Inbox request 'client close' failed: x", notices.get(0).getText());
        assertEquals(200, notices.get(0).getTicks());
    }

    @Test
    @DisplayName("a command error thrown out of the request is a failure with its one line")
    void thrownErrorKeepsOneLine() {
        KeepingSender sender = new KeepingSender();
        CommandOutcome outcome = InboxDispatch.runQuietly(sender, "assert", new RunChannel.Action<CommandOutcome>() {
            @Override
            public CommandOutcome run() throws CommandException {
                throw new CommandException("devtool.assert.failed");
            }
        });
        assertFalse(outcome.succeeded());
        assertEquals(1, sender.received.size());
        assertEquals("[DevToolkit] Inbox request 'assert' failed: devtool.assert.failed",
                sender.received.get(0).getUnformattedText());
        assertEquals(0, QuietScope.depth());
    }
}
