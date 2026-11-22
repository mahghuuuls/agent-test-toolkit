package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.bundle.BundleExecution;
import com.mahghuuuls.agenttesttoolkit.bundle.Bundles;
import com.mahghuuuls.agenttesttoolkit.bundle.QuietScope;
import com.mahghuuuls.agenttesttoolkit.bundle.Replies;
import com.mahghuuuls.agenttesttoolkit.command.Senders;
import com.mahghuuuls.agenttesttoolkit.command.SubCommand;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.net.ClientCapabilities;
import com.mahghuuuls.agenttesttoolkit.net.PromptPanelMessage;
import com.mahghuuuls.agenttesttoolkit.net.ToolkitChannel;
import com.mahghuuuls.agenttesttoolkit.state.OperatorBinding;
import com.mahghuuuls.agenttesttoolkit.state.SessionStamp;
import com.mahghuuuls.agenttesttoolkit.state.ToolkitState;
import com.mahghuuuls.agenttesttoolkit.wait.OperatorCheckpoints;
import com.mahghuuuls.agenttesttoolkit.wait.PromptPanels;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.network.play.server.SPacketTitle;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * Talks to the human at a precise moment, and writes down when they answered.
 *
 * <p>Three actions. {@code arm} shows the label and pauses the bundle until the human types
 * {@code ack} or the timeout passes. {@code cue} shows the label prominently, with a sound if
 * asked, and does not pause. A client at 1.9.0 or later is shown both in the toolkit's own panel
 * by default, which stays until the acknowledgement or the end of the arm's wait; an older one as
 * before. {@code ack} records the tick and the latency since the human was last asked. That is
 * all an acknowledgement does: it never presses a key, sends a packet, or
 * touches the mod under test, and the log keeps the two apart.
 *
 * <p>The cue goes to the session's operator when one is bound, else to the calling player.
 * From the console with no operator there is nobody to show anything to, and the record is
 * written anyway so the log still says the cue happened.
 */
public final class CheckpointSubCommand implements SubCommand {

    private static final List<String> ACTIONS = Arrays.asList("arm", "cue", "ack");
    /** The toolkit's own panel on the right of the screen, drawn by a client at 1.9.0 or later. */
    static final String PANEL = "panel";
    private static final List<String> FORMS = Arrays.asList("chat", "actionbar", "title", PANEL);
    /** What an older client is shown for a cue, as before the panel existed. */
    static final String OLDER_CUE_FORM = "actionbar";
    /** What an older client is shown for an arm, as before the panel existed. */
    static final String OLDER_ARM_FORM = "title";
    /** The line under an armed checkpoint's label: how to answer it. */
    static final String ACK_LINE = "Type /devtool checkpoint ack";

    /** One minute. A human who has not answered in a minute is not at the keyboard. */
    public static final int DEFAULT_ARM_TIMEOUT_TICKS = 1200;
    public static final int MAX_ARM_TIMEOUT_TICKS = 12000;

    @Override
    public String getName() {
        return "checkpoint";
    }

    @Override
    public String getDescription() {
        return "Cue the operator, wait for their acknowledgement, and record the timing";
    }

    @Override
    public String getUsage() {
        return "checkpoint <arm <label> [timeoutTicks] | cue <label> [chat|actionbar|title|panel] [sound] | ack>";
    }

    @Override
    public boolean requiresPlayer() {
        return false;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (args.length == 0) {
            throw new WrongUsageException("/devtool " + getUsage());
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if ("arm".equals(action)) {
            arm(server, sender, args);
        } else if ("cue".equals(action)) {
            cue(server, sender, args);
        } else if ("ack".equals(action)) {
            ack(server, sender);
        } else {
            throw new WrongUsageException("/devtool " + getUsage());
        }
    }

    private void arm(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (args.length < 2) {
            throw new WrongUsageException("/devtool checkpoint arm <label> [timeoutTicks]");
        }
        String label = args[1];
        int timeout = args.length > 2
                ? CommandBase.parseInt(args[2], 1, MAX_ARM_TIMEOUT_TICKS) : DEFAULT_ARM_TIMEOUT_TICKS;

        final EntityPlayerMP target = cueTarget(server, sender);
        final java.util.UUID targetId = target == null ? null : target.getUniqueID();
        BooleanSupplier present = targetId == null ? null
                : () -> server.getPlayerList().getPlayerByUUID(targetId) != null;

        long worldTick = RecordContext.worldTick(sender);
        OperatorCheckpoints.Armed armed = OperatorCheckpoints.arm(label, worldTick, present, QuietScope.isQuiet());
        if (armed == null) {
            // Recorded as well as refused, so the log shows the second arm the way it shows
            // every other failure, rather than only as a chat line nobody kept.
            ToolkitLog.error("Checkpoint arm refused, one is already armed",
                    label + " while " + OperatorCheckpoints.getArmed().getLabel() + " is armed");
            throw new CommandException("[DevToolkit] A checkpoint is already armed: "
                    + OperatorCheckpoints.getArmed().getLabel() + ". Acknowledge it first.");
        }

        String form = formFor(null, ClientCapabilities.helloVersion(target), OLDER_ARM_FORM);
        LogRecord record = begin(sender, "arm", label);
        record.add("armedTick", worldTick);
        record.add("timeoutTicks", timeout);
        record.add("form", form);
        if (target != null) {
            record.add("operator", target.getName());
        }
        ToolkitLog.write(record);

        // The arm's panel keeps the arm's own timeout on the client too, so a client that missed
        // the hide still takes it away when the wait would have ended.
        String token = show(target, label, form, true, "READY? " + ACK_LINE, timeout);

        BundleExecution execution = Bundles.scheduler().getCurrentExecution();
        if (execution != null) {
            // Parks the bundle exactly as a chunk barrier does. When the wait ends for any
            // reason the condition releases the checkpoint, so the next arm is not refused
            // for a bundle that is no longer waiting.
            try {
                execution.await(new DisarmingCondition(armed, targetId, token), timeout);
            } catch (IllegalStateException alreadyWaiting) {
                OperatorCheckpoints.disarm(armed);
                PromptPanels.hide(targetId, token);
                throw new CommandException("[DevToolkit] " + alreadyWaiting.getMessage());
            }
            Replies.send(sender, "[DevToolkit] Armed '" + label
                    + "'. The bundle waits up to " + timeout + " ticks for /devtool checkpoint ack.");
        } else {
            Replies.send(sender, "[DevToolkit] Armed '" + label
                    + "'. Outside a bundle nothing waits; ack still records the latency.");
        }
    }

    private void cue(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (args.length < 2) {
            throw new WrongUsageException(
                    "/devtool checkpoint cue <label> [chat|actionbar|title|panel] [sound]");
        }
        String label = args[1];
        String asked = null;
        boolean sound = false;
        for (int i = 2; i < args.length; i++) {
            String option = args[i].toLowerCase(Locale.ROOT);
            if (FORMS.contains(option)) {
                asked = option;
            } else if ("sound".equals(option)) {
                sound = true;
            } else {
                throw new WrongUsageException(
                        "/devtool checkpoint cue <label> [chat|actionbar|title|panel] [sound]");
            }
        }

        long worldTick = RecordContext.worldTick(sender);
        OperatorCheckpoints.cued(label, worldTick, QuietScope.isQuiet());

        EntityPlayerMP target = cueTarget(server, sender);
        String form = formFor(asked, ClientCapabilities.helloVersion(target), OLDER_CUE_FORM);
        LogRecord record = begin(sender, "cue", label);
        record.add("cueTick", worldTick);
        record.add("form", form);
        record.add("sound", sound);
        if (target != null) {
            record.add("operator", target.getName());
        }
        ToolkitLog.write(record);

        // A cue is not waited on, and has no timeout of its own: its panel stays until the
        // acknowledgement, a reset of the checkpoint state, or the player leaving.
        show(target, label, form, sound, null, CUE_PANEL_TICKS);
        Replies.send(sender, "[DevToolkit] Cue '" + label + "' shown as "
                + form + (sound ? " with sound" : "")
                + (asked != null && !asked.equals(form) ? " (the panel needs toolkit "
                + ToolkitChannel.versionText(ToolkitChannel.VERSION_1_9_0) + " on the client)" : "") + ".");
    }

    private void ack(MinecraftServer server, ICommandSender sender) throws CommandException {
        OperatorBinding operator = ToolkitState.getOperator();
        EntityPlayerMP player = Senders.asServerPlayer(sender);
        if (operator != null && (player == null || !operator.isPlayer(player.getUniqueID()))) {
            // Only the operator answers for the operator. Anyone else acknowledging would
            // record a latency for a person who never saw the cue.
            throw new CommandException("[DevToolkit] Only the bound operator, "
                    + operator.getName() + ", may acknowledge.");
        }

        long worldTick = RecordContext.worldTick(sender);
        OperatorCheckpoints.Ack answered = OperatorCheckpoints.ack(worldTick);
        if (answered == null) {
            ToolkitLog.error("Checkpoint ack with nothing armed or cued");
            throw new CommandException("[DevToolkit] Nothing is armed or cued to acknowledge.");
        }

        LogRecord record = begin(sender, "ack", answered.label);
        record.add("askedTick", answered.askedTick);
        record.add("ackTick", answered.ackTick);
        record.add("latencyTicks", answered.latencyTicks());
        record.add("answered", answered.answeredArm ? "arm" : "cue");
        record.add("quiet", answered.allQuiet);
        ToolkitLog.write(record);
        // An acknowledgement answers the armed checkpoint and the last cue together, so nothing is
        // left to answer and every panel goes, whichever player it was shown to.
        PromptPanels.hideAll();

        if (repliesToAck(answered)) {
            Replies.send(sender, "[DevToolkit] Acknowledged '" + answered.label
                    + "' after " + answered.latencyTicks() + " ticks.");
        }
    }

    /**
     * Whether an acknowledgement is answered in the chat. Not when everything it answered was
     * asked by a run that keeps its chat to itself: the person typed it to let such a
     * run go on, and the panel closing is their confirmation. A refused acknowledgement is an
     * error, shown as one either way.
     */
    static boolean repliesToAck(OperatorCheckpoints.Ack answered) {
        return !answered.allQuiet;
    }

    /** The bound operator when online, else the calling player, else nobody. */
    private static EntityPlayerMP cueTarget(MinecraftServer server, ICommandSender sender) {
        OperatorBinding operator = ToolkitState.getOperator();
        if (operator != null) {
            EntityPlayerMP bound = server.getPlayerList().getPlayerByUUID(operator.getUuid());
            if (bound != null) {
                return bound;
            }
        }
        return Senders.asServerPlayer(sender);
    }

    /**
     * The form a prompt is shown in: the one asked, else the panel for a client that reads it,
     * else what an older client was shown before the panel. A panel asked of an older client is
     * shown in that older form too: the client has no handler for the panel's message and would
     * drop the connection on it, by the channel's own version rule.
     *
     * @param asked        the form named on the command, or null
     * @param helloVersion the version the target's client said hello with; null before any hello
     */
    static String formFor(String asked, String helloVersion, String olderForm) {
        boolean readsPanel = ToolkitChannel.readsNewMessages(helloVersion);
        if (asked == null || (PANEL.equals(asked) && !readsPanel)) {
            return readsPanel ? PANEL : olderForm;
        }
        return asked;
    }

    /** A cue's panel has no timeout: 0 keeps it until the server hides it. */
    static final int CUE_PANEL_TICKS = 0;

    /** Labels longer than this do not fit on screen at title size, so they go on the subtitle line. */
    static final int MAX_TITLE_LABEL_LENGTH = 12;

    /**
     * Puts the label in front of the human.
     *
     * <p>Every form but the panel is a packet the server already sends for its own purposes, so
     * it needs nothing on the client. A title is held for four seconds, long enough to read and
     * short enough not to hide the game behind it. The panel stays until it is hidden or its
     * ticks pass, away from the chat a quiet bundle keeps clear.
     *
     * @param subtitle   the instruction under the label, or null
     * @param panelTicks how long a panel may stay; 0 until it is hidden
     * @return the panel's token, or null when no panel was shown
     */
    private static String show(EntityPlayerMP target, String label, String form, boolean sound,
                               String subtitle, int panelTicks) {
        if (target == null) {
            return null;
        }
        String token = null;
        TextComponentString text = new TextComponentString(label);
        text.getStyle().setColor(TextFormatting.GOLD).setBold(true);
        if (PANEL.equals(form)) {
            token = showPanel(target, label, subtitle == null ? Collections.<String>emptyList()
                    : Collections.singletonList(ACK_LINE), panelTicks);
        } else if ("title".equals(form)) {
            target.connection.sendPacket(new SPacketTitle(10, 80, 20));
            if (label.length() <= MAX_TITLE_LABEL_LENGTH) {
                target.connection.sendPacket(new SPacketTitle(SPacketTitle.Type.TITLE, text));
                if (subtitle != null) {
                    target.connection.sendPacket(new SPacketTitle(SPacketTitle.Type.SUBTITLE,
                            new TextComponentString(subtitle)));
                }
            } else {
                // A title is drawn at four times chat size and a long label runs off both
                // edges. The label goes on the subtitle line, still gold and bold, under a
                // short word that says what kind of moment this is; the instruction moves
                // to chat so nothing is lost.
                TextComponentString heading = new TextComponentString(
                        subtitle == null ? "NOW" : "READY?");
                heading.getStyle().setColor(TextFormatting.GOLD).setBold(true);
                target.connection.sendPacket(new SPacketTitle(SPacketTitle.Type.TITLE, heading));
                target.connection.sendPacket(new SPacketTitle(SPacketTitle.Type.SUBTITLE, text));
                if (subtitle != null) {
                    target.sendMessage(new TextComponentString("[DevToolkit] " + subtitle));
                }
            }
        } else if ("actionbar".equals(form)) {
            target.sendStatusMessage(text, true);
        } else {
            target.sendMessage(new TextComponentString("[DevToolkit] ").appendSibling(text));
        }
        if (subtitle != null && !"title".equals(form) && !PANEL.equals(form)) {
            target.sendMessage(new TextComponentString("[DevToolkit] " + subtitle));
        }
        if (sound) {
            target.world.playSound(null, target.posX, target.posY, target.posZ,
                    SoundEvents.BLOCK_NOTE_PLING, SoundCategory.MASTER, 1.0F, 1.0F);
        }
        return token;
    }

    /** Shows the panel and remembers it as the player's one panel, replacing any shown before. */
    private static String showPanel(EntityPlayerMP target, String label, List<String> lines, int ticks) {
        String token = java.util.UUID.randomUUID().toString().replace("-", "");
        String shown = label.length() > PromptPanelMessage.MAX_LABEL
                ? label.substring(0, PromptPanelMessage.MAX_LABEL) : label;
        if (!ToolkitChannel.send(PromptPanelMessage.show(token, shown, lines, ticks), target)) {
            return null;
        }
        PromptPanels.shown(target.getUniqueID(), token);
        return token;
    }

    /**
     * Sends a panel's hide to its player, when online. Installed as the panel owner's way to
     * reach a screen; a player who has left needs nothing, their client drops the panel as it
     * disconnects.
     */
    public static void sendHide(java.util.UUID player, String token) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return;
        }
        if (!server.isCallingFromMinecraftThread()) {
            // A session stop can arrive from the remote console's thread; the player list and the
            // connection belong to the server thread, so the hide is sent from there.
            server.addScheduledTask(() -> sendHide(player, token));
            return;
        }
        EntityPlayerMP online = server.getPlayerList() == null ? null : server.getPlayerList().getPlayerByUUID(player);
        if (online != null) {
            ToolkitChannel.send(PromptPanelMessage.hide(token), online);
        }
    }

    /** Forgets the panel of a player who leaves, so a later hide is not sent to nobody. */
    public static final class PanelLogoutListener {
        @SubscribeEvent
        public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.player != null && event.player.world != null && !event.player.world.isRemote) {
                PromptPanels.forget(event.player.getUniqueID());
            }
        }
    }

    private static LogRecord begin(ICommandSender sender, String action, String label) {
        LogRecord record = RecordContext.stamp(LogRecord.of(EventType.CHECKPOINT), sender);
        SessionStamp.apply(record);
        return record.add("action", action).add("label", label);
    }

    /**
     * The armed checkpoint as a wait, plus forgetting it when the wait ends.
     *
     * <p>The execution only polls; it has no way to say "the wait is over" back to the
     * checkpoint registry. So the last poll that answers anything but pending also disarms.
     */
    private static final class DisarmingCondition implements com.mahghuuuls.agenttesttoolkit.bundle.WaitCondition {
        private final OperatorCheckpoints.Armed armed;
        /** The player the arm's panel was shown to, or null. */
        private final java.util.UUID player;
        /** The arm's panel, or null when it was shown another way. */
        private final String panel;

        DisarmingCondition(OperatorCheckpoints.Armed armed, java.util.UUID player, String panel) {
            this.armed = armed;
            this.player = player;
            this.panel = panel;
        }

        @Override
        public Poll poll(int elapsedTicks) {
            return armed.poll(elapsedTicks);
        }

        @Override
        public String kind() {
            return armed.kind();
        }

        @Override
        public void ended() {
            // Whatever ended the wait, the checkpoint is over. A timeout is decided by the
            // execution without a poll, so this is the only reliable release point, for the
            // checkpoint and for its panel.
            // A newer panel shown to the player since is left alone: its token differs.
            OperatorCheckpoints.disarm(armed);
            PromptPanels.hide(player, panel);
        }

        @Override
        public void describe(LogRecord record) {
            armed.describe(record);
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                          String[] args) {
        if (args.length <= 1) {
            return CommandBase.getListOfStringsMatchingLastWord(args, ACTIONS);
        }
        if (args.length == 3 && "cue".equalsIgnoreCase(args[0])) {
            return CommandBase.getListOfStringsMatchingLastWord(args, FORMS);
        }
        return Collections.emptyList();
    }
}
