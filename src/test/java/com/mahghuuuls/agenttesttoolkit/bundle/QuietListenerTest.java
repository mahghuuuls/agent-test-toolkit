package com.mahghuuuls.agenttesttoolkit.bundle;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandListener;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The swap of the game's command listener around one quiet command, and the reply helper.
 *
 * <p>The game's own field is reached by reflection only in a running game; here a plain holder
 * stands in for it, so what is checked is that the original is put back however the command
 * ends, which is what keeps one quiet command from silencing the server for good.
 */
class QuietListenerTest {

    /** The listener slot as a plain field. */
    private static final class HolderSlot implements QuietListener.Slot {
        ICommandListener current;
        ICommandListener seenDuringCommand;

        @Override
        public ICommandListener get() {
            return current;
        }

        @Override
        public void set(ICommandListener listener) {
            current = listener;
        }
    }

    /** A sender that keeps what it is sent. */
    static final class KeepingSender implements ICommandSender {
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

    private static final ICommandListener ORIGINAL = new ICommandListener() {
        @Override
        public void notifyListener(ICommandSender sender, ICommand command, int flags, String translationKey,
                                   Object... translationArgs) {
        }
    };

    @AfterEach
    void leaveAll() {
        while (QuietScope.depth() > 0) {
            QuietScope.exit();
        }
    }

    @Test
    @DisplayName("the game's listener is back after a command that throws, whatever it throws")
    void restoredAfterThrow() {
        final HolderSlot slot = new HolderSlot();
        slot.current = ORIGINAL;
        assertThrows(IllegalStateException.class, () -> QuietListener.during(slot, null,
                new QuietListener.Action<Void>() {
                    @Override
                    public Void run() {
                        slot.seenDuringCommand = slot.current;
                        throw new IllegalStateException("a mod's command misbehaved");
                    }
                }));
        assertTrue(slot.seenDuringCommand instanceof QuietListener, "the quiet listener was in place");
        assertSame(ORIGINAL, slot.current);

        assertThrows(CommandException.class, () -> QuietListener.during(slot, null,
                new QuietListener.Action<Void>() {
                    @Override
                    public Void run() throws CommandException {
                        throw new CommandException("commands.generic.exception");
                    }
                }));
        assertSame(ORIGINAL, slot.current);
    }

    @Test
    @DisplayName("a quiet command inside another puts back the listener it found")
    void nestedRestore() throws CommandException {
        final HolderSlot slot = new HolderSlot();
        slot.current = ORIGINAL;
        QuietListener.during(slot, null, new QuietListener.Action<Void>() {
            @Override
            public Void run() throws CommandException {
                final ICommandListener outer = slot.current;
                assertNotSame(ORIGINAL, outer);
                QuietListener.during(slot, null, new QuietListener.Action<Void>() {
                    @Override
                    public Void run() {
                        return null;
                    }
                });
                assertSame(outer, slot.current);
                return null;
            }
        });
        assertSame(ORIGINAL, slot.current);
    }

    @Test
    @DisplayName("outside a quiet run the stand-in hands the notice to the listener it replaced")
    void otherThreadsAreTold() {
        final List<String> told = new ArrayList<String>();
        ICommandListener original = new ICommandListener() {
            @Override
            public void notifyListener(ICommandSender sender, ICommand command, int flags, String translationKey,
                                       Object... translationArgs) {
                told.add(translationKey);
            }
        };
        new QuietListener(null, original).notifyListener(new KeepingSender(), null, 0, "commands.tp.success");
        assertEquals(1, told.size());
    }

    /** The server as the listener reads it, with numbers set by the test. */
    private static final class FakeServer implements QuietListener.Server {
        int operators;
        boolean logAdminCommands = true;
        boolean sendCommandFeedback = true;
        final List<ITextComponent> logged = new ArrayList<ITextComponent>();

        @Override
        public boolean broadcastConsoleToOps() {
            return true;
        }

        @Override
        public boolean broadcastRconToOps() {
            return true;
        }

        @Override
        public int otherOperators(ICommandSender sender, ICommand command) {
            return operators;
        }

        @Override
        public boolean rule(String name) {
            if ("logAdminCommands".equals(name)) {
                return logAdminCommands;
            }
            return "sendCommandFeedback".equals(name) && sendCommandFeedback;
        }

        @Override
        public void log(ITextComponent line) {
            logged.add(line);
        }
    }

    /** A player-like sender whose feedback the game would send. */
    private static final class FeedbackSender extends ChatKeeper {
        @Override
        public boolean sendCommandFeedback() {
            return true;
        }
    }

    /** Keeps what it is sent, as {@link KeepingSender} does, open to a subclass. */
    private static class ChatKeeper implements ICommandSender {
        final List<ITextComponent> received = new ArrayList<ITextComponent>();

        @Override
        public String getName() {
            return "player";
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

    @Test
    @DisplayName("in a quiet run the feedback is counted for the sender and each other operator, logged, and sent to nobody")
    void quietBranchCountsAndLogs() {
        final List<String> told = new ArrayList<String>();
        ICommandListener original = new ICommandListener() {
            @Override
            public void notifyListener(ICommandSender sender, ICommand command, int flags, String translationKey,
                                       Object... translationArgs) {
                told.add(translationKey);
            }
        };
        FakeServer game = new FakeServer();
        game.operators = 2;
        FeedbackSender sender = new FeedbackSender();
        QuietScopeTest.CountingRun quiet = new QuietScopeTest.CountingRun(true);
        QuietListener listener = new QuietListener(game, original);
        QuietScope.enter(quiet);
        try {
            listener.notifyListener(sender, null, 0, "commands.tp.success", "player");
            assertEquals(3, quiet.suppressed, "the sender's line and the two operators' grey copies");
            assertEquals(1, game.logged.size(), "the log line is kept");
            assertEquals("chat.type.admin", ((TextComponentTranslation) game.logged.get(0)).getKey());

            // The command asked for no line to its own sender: only the operators' copies are kept back.
            listener.notifyListener(sender, null, 1, "commands.tp.success", "player");
            assertEquals(5, quiet.suppressed);

            // With the log rule off, nothing reaches the log either.
            game.logAdminCommands = false;
            listener.notifyListener(sender, null, 0, "commands.tp.success", "player");
            assertEquals(2, game.logged.size(), "no new log line");
            assertEquals(8, quiet.suppressed);
        } finally {
            QuietScope.exit();
        }
        assertEquals(new ArrayList<ITextComponent>(), sender.received, "nothing reached the sender's chat");
        assertEquals(new ArrayList<String>(), told, "the replaced listener was not handed a quiet line");
    }

    @Test
    @DisplayName("in a quiet run with the feedback rule off and no other operator, nothing is counted")
    void quietBranchNothingToKeep() {
        FakeServer game = new FakeServer();
        game.sendCommandFeedback = false;
        QuietScopeTest.CountingRun quiet = new QuietScopeTest.CountingRun(true);
        QuietScope.enter(quiet);
        try {
            new QuietListener(game, ORIGINAL).notifyListener(new FeedbackSender(), null, 0, "commands.tp.success");
        } finally {
            QuietScope.exit();
        }
        assertEquals(0, quiet.suppressed);
        assertEquals(1, game.logged.size(), "the log line does not depend on the chat");
    }

    @Test
    @DisplayName("a reply to a null sender fails, inside a quiet run too, rather than vanishing")
    void nullSenderFails() {
        assertThrows(NullPointerException.class, () -> Replies.send(null, "[DevToolkit] lost"));
        QuietScopeTest.CountingRun quiet = new QuietScopeTest.CountingRun(true);
        QuietScope.enter(quiet);
        try {
            assertThrows(NullPointerException.class, () -> Replies.send(null, "[DevToolkit] lost"));
        } finally {
            QuietScope.exit();
        }
        assertEquals(0, quiet.suppressed, "not counted as a kept-back line");
    }

    @Test
    @DisplayName("a reply is sent outside a quiet run, and counted and dropped inside one")
    void repliesCounted() {
        KeepingSender sender = new KeepingSender();
        Replies.send(sender, "[DevToolkit] loud");
        assertEquals(1, sender.received.size());

        QuietScopeTest.CountingRun quiet = new QuietScopeTest.CountingRun(true);
        QuietScope.enter(quiet);
        try {
            Replies.send(sender, "[DevToolkit] one");
            Replies.send(sender, "[DevToolkit] two");
        } finally {
            QuietScope.exit();
        }
        assertEquals(1, sender.received.size(), "nothing reached the chat");
        assertEquals(2, quiet.suppressed);

        QuietScope.enter(new QuietScopeTest.CountingRun(false));
        try {
            Replies.send(sender, "[DevToolkit] a loud run");
        } finally {
            QuietScope.exit();
        }
        assertEquals(2, sender.received.size());
    }
}
