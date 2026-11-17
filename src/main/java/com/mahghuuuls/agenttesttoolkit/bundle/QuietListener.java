package com.mahghuuuls.agenttesttoolkit.bundle;

import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandListener;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.rcon.RConConsoleSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.CommandBlockBaseLogic;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.lang.reflect.Field;

/**
 * Stands in for the game's command listener while one command of a quiet bundle run executes.
 *
 * <p>Vanilla command feedback, such as "Teleported ...", does not travel through the sender: a
 * command hands it to one static listener, which sends it to the sender, to the other operators
 * as a grey line, and to the server log. A sender wrapped to drop the line is not possible,
 * because commands require the real player object, so the listener is what is swapped, for the
 * span of one command only, and put back in a {@code finally}.
 *
 * <p>This one writes the server log line exactly when the game would (the {@code logAdminCommands}
 * rule), counts the chat lines it kept from the sender and the other operators, and sends nothing
 * to any player. A console sender still gets its own line, which is its log. Called on a thread
 * that is not inside a quiet run, it hands the notice to the listener it replaced, so a command
 * arriving at the same moment from elsewhere is told as usual.
 *
 * <p>The game has one listener slot, and a mod that wants to hear command feedback puts its own
 * listener there, usually passing the line on to the game's. During a quiet command that mod's
 * listener is not called: it is the one this replaces, and handing it the notice would send the
 * line to the chat a quiet run keeps clear, since it cannot be told apart from the game's own. It
 * is back in its slot, and called as usual, from the next command on, and for every command
 * outside a quiet run.
 *
 * <p>Chat a command sends by itself, rather than through the listener, is not stopped.
 */
public final class QuietListener implements ICommandListener {

    /** The game's own name for the listener field, remapped to the development name when needed. */
    private static final String LISTENER_FIELD = "field_71533_a";

    /** Where the game keeps its listener: the static field in the game, a plain holder in tests. */
    interface Slot {
        ICommandListener get();

        void set(ICommandListener listener);
    }

    /** Work that may fail as a command fails. */
    public interface Action<T> {
        T run() throws CommandException;
    }

    /**
     * What the listener reads from the server: the same questions the game's own listener asks,
     * apart from the server object, so the counting and the log line are tested without a game.
     */
    interface Server {
        /** Whether the server console's feedback is copied to the operators. */
        boolean broadcastConsoleToOps();

        /** Whether an RCON console's feedback is copied to the operators. */
        boolean broadcastRconToOps();

        /** The online operators other than the sender who would get the grey copy of the line. */
        int otherOperators(ICommandSender sender, ICommand command);

        /** A game rule of the first world; false while there is no world. */
        boolean rule(String name);

        /** Writes the line to the server log, as the server's own message does. */
        void log(ITextComponent line);
    }

    private static volatile Slot gameSlot;
    private static volatile boolean slotUnavailable;

    private final Server game;
    private final ICommandListener replaced;

    QuietListener(Server game, ICommandListener replaced) {
        this.game = game;
        this.replaced = replaced;
    }

    /**
     * Runs one command with this listener in the game's place, and puts the game's own back
     * however the command ends. When the game's listener cannot be reached, the command runs with
     * it unchanged and its feedback reaches the chat; the log says so once.
     */
    public static <T> T during(MinecraftServer server, Action<T> action) throws CommandException {
        Slot slot = gameSlot();
        if (slot == null) {
            return action.run();
        }
        return during(slot, server, action);
    }

    /** The swap itself, apart from the game's field, so the restore is tested without a game. */
    static <T> T during(Slot slot, MinecraftServer server, Action<T> action) throws CommandException {
        ICommandListener original = slot.get();
        slot.set(new QuietListener(server == null ? null : new GameServer(server), original));
        try {
            return action.run();
        } finally {
            slot.set(original);
        }
    }

    private static Slot gameSlot() {
        Slot slot = gameSlot;
        if (slot != null || slotUnavailable) {
            return slot;
        }
        try {
            final Field field = ObfuscationReflectionHelper.findField(CommandBase.class, LISTENER_FIELD);
            slot = new Slot() {
                @Override
                public ICommandListener get() {
                    try {
                        return (ICommandListener) field.get(null);
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException(e);
                    }
                }

                @Override
                public void set(ICommandListener listener) {
                    CommandBase.setCommandListener(listener);
                }
            };
            gameSlot = slot;
            return slot;
        } catch (RuntimeException e) {
            slotUnavailable = true;
            ToolkitLog.error("Quiet bundles cannot reach the command listener; command feedback stays in chat",
                    e.toString());
            return null;
        }
    }

    @Override
    public void notifyListener(ICommandSender sender, ICommand command, int flags, String translationKey,
                               Object... translationArgs) {
        if (!QuietScope.isQuiet()) {
            if (replaced != null) {
                replaced.notifyListener(sender, command, flags, translationKey, translationArgs);
            }
            return;
        }
        ITextComponent admin = new TextComponentTranslation("chat.type.admin",
                sender.getName(), new TextComponentTranslation(translationKey, translationArgs));
        admin.getStyle().setColor(TextFormatting.GRAY);
        admin.getStyle().setItalic(Boolean.TRUE);

        int kept = 0;
        boolean console = sender instanceof MinecraftServer || sender instanceof RConConsoleSource;
        boolean consoleToOps = (sender instanceof MinecraftServer && game.broadcastConsoleToOps())
                || (sender instanceof RConConsoleSource && game.broadcastRconToOps());
        if (sender.sendCommandFeedback() && (!console || consoleToOps)) {
            kept += game.otherOperators(sender, command);
        }

        if (!(sender instanceof MinecraftServer) && game.rule("logAdminCommands")) {
            // The line the log would have had; the server's own message goes to the log only.
            game.log(admin);
        }

        boolean feedback = game.rule("sendCommandFeedback");
        if (sender instanceof CommandBlockBaseLogic) {
            feedback = ((CommandBlockBaseLogic) sender).shouldTrackOutput();
        }
        if (sender instanceof MinecraftServer) {
            sender.sendMessage(new TextComponentTranslation(translationKey, translationArgs));
        } else if ((flags & 1) != 1 && feedback) {
            kept++;
        }
        QuietScope.suppress(kept);
    }

    /** The server as the game's listener reads it. */
    private static final class GameServer implements Server {
        private final MinecraftServer server;

        GameServer(MinecraftServer server) {
            this.server = server;
        }

        @Override
        public boolean broadcastConsoleToOps() {
            return server.shouldBroadcastConsoleToOps();
        }

        @Override
        public boolean broadcastRconToOps() {
            return server.shouldBroadcastRconToOps();
        }

        @Override
        public int otherOperators(ICommandSender sender, ICommand command) {
            if (server.getPlayerList() == null) {
                return 0;
            }
            int operators = 0;
            for (EntityPlayer player : server.getPlayerList().getPlayers()) {
                if (player != sender && server.getPlayerList().canSendCommands(player.getGameProfile())
                        && command.checkPermission(server, sender)) {
                    operators++;
                }
            }
            return operators;
        }

        @Override
        public boolean rule(String name) {
            return server.worlds != null && server.worlds.length > 0 && server.worlds[0] != null
                    && server.worlds[0].getGameRules().getBoolean(name);
        }

        @Override
        public void log(ITextComponent line) {
            server.sendMessage(line);
        }
    }
}
