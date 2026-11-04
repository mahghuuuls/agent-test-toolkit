package com.mahghuuuls.agenttesttoolkit.command;

import com.mahghuuuls.agenttesttoolkit.bundle.BundleCommand;
import com.mahghuuuls.agenttesttoolkit.bundle.BundleDefinition;
import com.mahghuuuls.agenttesttoolkit.bundle.BundleExecution;
import com.mahghuuuls.agenttesttoolkit.bundle.BundleRecorder;
import com.mahghuuuls.agenttesttoolkit.bundle.CommandDispatcher;
import com.mahghuuuls.agenttesttoolkit.bundle.CommandOutcome;
import com.mahghuuuls.agenttesttoolkit.bundle.ContextSource;
import com.mahghuuuls.agenttesttoolkit.bundle.RunOrigin;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who started a run, through which channel, and the subcommand suggestion.
 *
 * <p>Most sender types can be built here: a stub, an RCON source, a command block's logic, an
 * {@code /execute} wrapper. The two that cannot are a real player and the server itself, which
 * need a game; those are covered in game by Campaign A.
 */
class RunOriginTest {

    @BeforeEach
    void startCapture() {
        ToolkitLog.startCaptureForTesting();
    }

    @AfterEach
    void stopCapture() {
        ToolkitLog.stopCaptureForTesting();
        RunChannel.restore(null);
    }

    @Test
    @DisplayName("the channel follows the sender type, in a fixed order")
    void channelBySenderType() {
        assertEquals(RunOrigin.CHAT, RunChannel.classify(null, true, false, false));
        assertEquals(RunOrigin.CONSOLE, RunChannel.classify(null, false, true, false));
        assertEquals(RunOrigin.COMMAND_BLOCK, RunChannel.classify(null, false, false, true));
        assertEquals(RunOrigin.OTHER, RunChannel.classify(null, false, false, false));
    }

    /** A sender that speaks for no entity and is neither console nor command block. */
    private static final class StubSender implements net.minecraft.command.ICommandSender {
        @Override
        public String getName() {
            return "stub";
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
        public net.minecraft.server.MinecraftServer getServer() {
            return null;
        }
    }

    /** A command block's logic, with the abstract parts stubbed. */
    private static final class StubCommandBlock extends net.minecraft.tileentity.CommandBlockBaseLogic {
        @Override
        public void updateCommand() {
        }

        @Override
        public int getCommandBlockType() {
            return 0;
        }

        @Override
        public void fillInInfo(io.netty.buffer.ByteBuf buf) {
        }

        @Override
        public net.minecraft.util.math.BlockPos getPosition() {
            return net.minecraft.util.math.BlockPos.ORIGIN;
        }

        @Override
        public net.minecraft.util.math.Vec3d getPositionVector() {
            return net.minecraft.util.math.Vec3d.ZERO;
        }

        @Override
        public net.minecraft.world.World getEntityWorld() {
            return null;
        }

        @Override
        public net.minecraft.entity.Entity getCommandSenderEntity() {
            return null;
        }

        @Override
        public net.minecraft.server.MinecraftServer getServer() {
            return null;
        }
    }

    @Test
    @DisplayName("of() classifies the senders a unit test can build")
    void ofBySenderType() {
        assertEquals(RunOrigin.OTHER, RunChannel.of(new StubSender()));
        assertEquals(RunOrigin.CONSOLE, RunChannel.of(new net.minecraft.network.rcon.RConConsoleSource(null)));
        assertEquals(RunOrigin.COMMAND_BLOCK, RunChannel.of(new StubCommandBlock()));
    }

    @Test
    @DisplayName("a sender wrapped by /execute is never chat")
    void wrappedSenderIsOther() {
        assertEquals(RunOrigin.OTHER,
                RunChannel.of(net.minecraft.command.CommandSenderWrapper.create(new StubSender())));
        assertEquals(RunOrigin.OTHER,
                RunChannel.of(net.minecraft.command.CommandSenderWrapper.create(new StubCommandBlock())));
    }

    @Test
    @DisplayName("of() honours an override for the span of during(), then drops it")
    void duringScopesTheOverride() throws Exception {
        String inside = RunChannel.during(RunOrigin.INBOX, () -> RunChannel.of(new StubSender()));
        assertEquals(RunOrigin.INBOX, inside);
        assertEquals(RunOrigin.OTHER, RunChannel.of(new StubSender()));
    }

    @Test
    @DisplayName("during() restores the channel even when the action fails")
    void duringRestoresOnFailure() {
        assertThrows(net.minecraft.command.CommandException.class, () ->
                RunChannel.during(RunOrigin.INBOX, () -> {
                    throw new net.minecraft.command.CommandException("boom");
                }));
        assertEquals(RunOrigin.OTHER, RunChannel.of(new StubSender()));
    }

    @Test
    @DisplayName("the unknown-subcommand error names the closest real subcommand")
    void unknownSubcommandMessage() {
        net.minecraft.command.CommandException e = assertThrows(net.minecraft.command.CommandException.class,
                () -> new DevToolCommand().execute(null, new StubSender(), new String[]{"logging"}));
        assertTrue(e.getMessage().contains("Did you mean log?"), e.getMessage());
        net.minecraft.command.CommandException none = assertThrows(net.minecraft.command.CommandException.class,
                () -> new DevToolCommand().execute(null, new StubSender(), new String[]{"zzzz"}));
        assertFalse(none.getMessage().contains("Did you mean"), none.getMessage());
    }

    @Test
    @DisplayName("an override wins over the sender type, and restoring removes it")
    void overrideWinsAndRestores() {
        assertEquals(RunOrigin.INBOX, RunChannel.classify(RunOrigin.INBOX, true, false, false));
        String previous = RunChannel.push(RunOrigin.INBOX);
        assertNull(previous);
        RunChannel.restore(previous);
        // With the override gone, the type decides again; checked through push returning it.
        assertNull(RunChannel.push(null));
    }

    @Test
    @DisplayName("BUNDLE_START carries caller, channel and permission level when given")
    void startRecordCarriesOrigin() {
        BundleExecution execution = execution();
        execution.setOrigin(new RunOrigin("Developer", RunOrigin.CHAT, 2));
        BundleRecorder.recordStart(execution);

        String start = ToolkitLog.capturedForTesting().get(0);
        assertTrue(start.contains("[BUNDLE_START]"), start);
        assertTrue(start.contains("caller=Developer"), start);
        assertTrue(start.contains("channel=chat"), start);
        assertTrue(start.contains("permissionLevel=2"), start);
    }

    @Test
    @DisplayName("during() with a request id makes it readable inside, and drops it after, even on failure")
    void duringCarriesTheRequestId() throws Exception {
        assertNull(RunChannel.requestId());
        assertEquals("fca-001", RunChannel.during(RunOrigin.INBOX, "fca-001", RunChannel::requestId));
        assertNull(RunChannel.requestId());
        assertThrows(net.minecraft.command.CommandException.class, () ->
                RunChannel.during(RunOrigin.INBOX, "fca-002", () -> {
                    throw new net.minecraft.command.CommandException("boom");
                }));
        assertNull(RunChannel.requestId());
        assertEquals(RunOrigin.OTHER, RunChannel.of(new StubSender()));
    }

    @Test
    @DisplayName("a top-level run takes the dispatching request's id, a nested run its parent's")
    void requestIdForTopLevelAndNested() {
        RunOrigin parent = new RunOrigin("Developer", RunOrigin.INBOX, 2, "fca-001");
        assertEquals("fca-002", RunOrigin.requestIdFor(false, null, "fca-002"));
        assertNull(RunOrigin.requestIdFor(false, null, null), "a run from chat");
        assertEquals("fca-001", RunOrigin.requestIdFor(true, parent, null),
                "a child starts in a later tick, when no request is being dispatched");
        assertEquals("fca-001", RunOrigin.requestIdFor(true, parent, "fcb-009"),
                "the parent's id, never another request's");
        assertNull(RunOrigin.requestIdFor(true, new RunOrigin("Developer", RunOrigin.CHAT, 2), "fcb-009"));
        assertNull(RunOrigin.requestIdFor(true, null, "fcb-009"));
    }

    @Test
    @DisplayName("BUNDLE_START names the inbox request only when the origin has one")
    void startRecordCarriesRequestId() {
        BundleExecution inbox = execution();
        inbox.setOrigin(new RunOrigin("Developer", RunOrigin.INBOX, 2, "fca-001"));
        BundleRecorder.recordStart(inbox);
        BundleExecution chat = execution();
        chat.setOrigin(new RunOrigin("Developer", RunOrigin.CHAT, 2));
        BundleRecorder.recordStart(chat);

        List<String> captured = ToolkitLog.capturedForTesting();
        assertTrue(captured.get(0).contains("requestId=fca-001"), captured.get(0));
        assertFalse(captured.get(1).contains("requestId="), captured.get(1));
    }

    @Test
    @DisplayName("BUNDLE_START without an origin carries none of the three fields")
    void startRecordWithoutOrigin() {
        BundleRecorder.recordStart(execution());

        String start = ToolkitLog.capturedForTesting().get(0);
        assertFalse(start.contains("channel="), start);
        assertFalse(start.contains("caller="), start);
    }

    @Test
    @DisplayName("an origin must name its channel")
    void originNeedsChannel() {
        assertThrows(IllegalArgumentException.class, () -> new RunOrigin("x", null, 2));
    }

    @Test
    @DisplayName("a mistyped subcommand names the closest one, or nothing")
    void suggestions() {
        java.util.Collection<String> names = new DevToolCommand().subCommandNames();
        assertEquals("entities", CommandSuggestions.closest("ent", names));
        assertNull(CommandSuggestions.closest("in", names));
        assertNull(CommandSuggestions.closest("r", names));
        assertEquals("log", CommandSuggestions.closest("logging", names));
        assertEquals("session", CommandSuggestions.closest("sesion", names));
        assertEquals("inspect", CommandSuggestions.closest("inpsect", names));
        assertEquals("reload", CommandSuggestions.closest("RELOAD", names));
        assertNull(CommandSuggestions.closest("zzzz", names));
        assertNull(CommandSuggestions.closest("", names));
        assertNull(CommandSuggestions.closest("q", Collections.<String>emptyList()));
    }

    @Test
    @DisplayName("edit distance counts insertions, deletions and substitutions")
    void distance() {
        assertEquals(0, CommandSuggestions.distance("run", "run"));
        assertEquals(1, CommandSuggestions.distance("rn", "run"));
        assertEquals(2, CommandSuggestions.distance("inpsect", "inspect"));
        assertEquals(4, CommandSuggestions.distance("logging", "log"));
    }

    private static BundleExecution execution() {
        BundleDefinition definition = new BundleDefinition("origin", null, true,
                Collections.singletonList(new BundleCommand("mark", 0)));
        return new BundleExecution(definition, new CommandDispatcher() {
            @Override
            public CommandOutcome dispatch(String command) {
                return CommandOutcome.success();
            }
        }, new ContextSource() {
            @Override
            public RecordContext.Snapshot snapshot() {
                return new RecordContext.Snapshot(RecordContext.SERVER, 1L);
            }
        });
    }
}
