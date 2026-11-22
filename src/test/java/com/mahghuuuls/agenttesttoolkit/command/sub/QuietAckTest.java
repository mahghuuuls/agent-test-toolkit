package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.bundle.QuietScope;
import com.mahghuuuls.agenttesttoolkit.wait.OperatorCheckpoints;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whether an acknowledgement is answered in the chat, through the checkpoint command itself: an
 * arm or cue run inside a quiet run is remembered as quiet, and an acknowledgement of only quiet
 * prompts sends no reply, while anything typed loud keeps the reply. Run with no server and a
 * sender that is not a player, so nothing is shown to anyone and only the replies are seen.
 */
class QuietAckTest {

    /** A sender that keeps what it is sent, in a world whose tick is 0. */
    private static final class KeepingSender implements ICommandSender {
        final List<ITextComponent> received = new ArrayList<ITextComponent>();
        final net.minecraft.world.World world = tickZeroWorld();

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
            return world;
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

    /**
     * A world that answers only its tick, 0: built without its constructor, which needs a running
     * server. The checkpoints treat a tick below 0 as no tick at all, so a cue needs a real one.
     */
    private static net.minecraft.world.World tickZeroWorld() {
        try {
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            java.lang.reflect.Field field = unsafeType.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            Object unsafe = field.get(null);
            java.lang.reflect.Method allocate = unsafeType.getMethod("allocateInstance", Class.class);
            net.minecraft.world.World world = (net.minecraft.world.World) allocate.invoke(unsafe,
                    net.minecraft.world.WorldServer.class);
            java.lang.reflect.Field info = net.minecraft.world.World.class.getDeclaredField("worldInfo");
            info.setAccessible(true);
            info.set(world, allocate.invoke(unsafe, net.minecraft.world.storage.WorldInfo.class));
            return world;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A quiet bundle run, as the scope sees one. */
    private static final QuietScope.Run QUIET_RUN = new QuietScope.Run() {
        @Override
        public boolean isQuiet() {
            return true;
        }

        @Override
        public void noteSuppressed(int lines) {
        }
    };

    private final CheckpointSubCommand command = new CheckpointSubCommand();
    private final KeepingSender sender = new KeepingSender();

    @BeforeEach
    @AfterEach
    void reset() {
        OperatorCheckpoints.reset();
        while (QuietScope.depth() > 0) {
            QuietScope.exit();
        }
    }

    private void quietly(String... args) throws CommandException {
        QuietScope.enter(QUIET_RUN);
        try {
            command.execute(null, sender, args);
        } finally {
            QuietScope.exit();
        }
    }

    /** The replies one acknowledgement typed outside any run sends. */
    private int ackReplies() throws CommandException {
        int before = sender.received.size();
        command.execute(null, sender, new String[] {"ack"});
        return sender.received.size() - before;
    }

    @Test
    @DisplayName("a quiet arm, alone or with a quiet cue, and a quiet cue alone are acknowledged without a chat line")
    void allQuietIsSilent() throws CommandException {
        quietly("arm", "READY");
        assertEquals(0, sender.received.size(), "the arm's own reply is kept out by the quiet run");
        assertEquals(0, ackReplies(), "the panel closing is the confirmation");

        quietly("arm", "READY");
        quietly("cue", "NOW");
        assertEquals(0, ackReplies(), "the cue it also answers was quiet too");

        quietly("cue", "NOW");
        assertEquals(0, ackReplies(), "a quiet cue answered alone");
    }

    @Test
    @DisplayName("anything answered that was typed outside a quiet run keeps the acknowledgement line")
    void anyLoudReplies() throws CommandException {
        command.execute(null, sender, new String[] {"arm", "READY"});
        assertEquals(1, ackReplies(), "an arm a person typed");

        quietly("arm", "READY");
        command.execute(null, sender, new String[] {"cue", "NOW"});
        assertEquals(1, ackReplies(), "a quiet arm answered together with a typed cue");

        command.execute(null, sender, new String[] {"cue", "NOW"});
        int replies = ackReplies();
        assertEquals(1, replies, "a typed cue answered alone");
        assertTrue(sender.received.get(sender.received.size() - 1).getUnformattedText().startsWith("[DevToolkit] Acknowledged"));
    }
}
