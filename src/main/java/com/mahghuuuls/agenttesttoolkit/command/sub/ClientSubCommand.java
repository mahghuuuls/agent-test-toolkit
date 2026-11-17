package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.command.SubCommand;
import com.mahghuuuls.agenttesttoolkit.inbox.InboxGate;
import com.mahghuuuls.agenttesttoolkit.inbox.InputOptions;
import com.mahghuuuls.agenttesttoolkit.inbox.PlayerActionOptions;
import com.mahghuuuls.agenttesttoolkit.inbox.ScreenOptions;
import com.mahghuuuls.agenttesttoolkit.inspect.EntityTarget;
import com.mahghuuuls.agenttesttoolkit.net.InputRequest;
import com.mahghuuuls.agenttesttoolkit.net.PlayerActionRequest;
import com.mahghuuuls.agenttesttoolkit.net.ScreenRequest;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.net.ClientActionRequest;
import com.mahghuuuls.agenttesttoolkit.net.ClientCapabilities;
import com.mahghuuuls.agenttesttoolkit.net.ToolkitChannel;
import com.mahghuuuls.agenttesttoolkit.state.PendingRejoin;
import com.mahghuuuls.agenttesttoolkit.state.SessionStamp;
import com.mahghuuuls.agenttesttoolkit.state.ToolkitState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Asks the sender's own client for one lifecycle step (D18, ARC-022, REQ-202).
 *
 * <p>Always the sender: the player typing it, the caller of a bundle, the inbox operator through
 * the inbox. There is no player argument, so no one can send another player's client away, and
 * the console, a command block or anything else without a player is refused.
 *
 * <p>The server refuses what it can know at once, with a key a bundle can tolerate: a client
 * without the toolkit, or one that has not allowed actions in its own configuration. What only
 * the client can know, such as whether a death screen is open, comes back as a report.
 */
public final class ClientSubCommand implements SubCommand {

    /** Failure key for a sender whose client does not have the toolkit, 1.4.0 or later. */
    public static final String KEY_NO_CLIENT = "devtool.client.noClient";
    /** Failure key for a client that has not allowed actions, or has not said hello yet. */
    public static final String KEY_NOT_ALLOWED = "devtool.client.notAllowed";
    /** Failure key for a respawn asked of a player who is alive. */
    public static final String KEY_NOT_DEAD = "devtool.client.notDead";
    /** Failure key for any step asked while the sender's client is about to leave on a reconnect. */
    public static final String KEY_BUSY = "devtool.client.busy";
    /** Failure key for a player action that acts on the world, asked of a dead player. */
    public static final String KEY_DEAD = "devtool.client.dead";
    /** Failure key for a {@code face} whose entity or block is not loaded in the sender's dimension. */
    public static final String KEY_NO_TARGET = "devtool.client.noTarget";
    /** The reason in the server's record of that refusal. */
    static final String REASON_NO_TARGET = "noTarget";

    private static final SecureRandom RANDOM = new SecureRandom();
    /** The reconnect's last word: leave as Save and Quit does, logging the player out. */
    private static final String LOGOUT = "logout";

    @Override
    public String getName() {
        return "client";
    }

    @Override
    public String getDescription() {
        return "Ask your own client to reconnect, respawn, close the open screen, quit the game, "
                + "or take a small player action";
    }

    @Override
    public String getUsage() {
        return "client <reconnect [awaySeconds] [logout] | respawn | close | quit | select <0-8>"
                + " | use|attack [route=binding|event|queue] [keepHold=true]"
                + " | hold <use|attack> <ticks> | drop [all] | inventory"
                + " | tooltip <0-40> [advanced] [shift] [ctrl] [alt] [showTicks=<ticks>]"
                + " | key <binding> <press|hold <ticks>|release> [raw=true] [keepHold=true] | keys [prefix]"
                + " | look <yaw> <pitch> [over=<ticks>] | face <selector|uuid|block <x> <y> <z> [face]>"
                + " | view <first|back|front> | focus <lost|gained> | screen"
                + " | click <slot <n> [left|right|shift|key=<1-9>]|button <id|label=<text>>|at <x> <y> [left|right]>"
                + " | scroll <amount> | tooltip container <slot> [advanced] [shift] [ctrl] [alt]>"
                + " [expect=<screen class>] [delay=<ticks>] [screenshot=<label>]"
                + " [screenshotDelay=<ticks>] [screenshotHide=chat|hud]";
    }

    @Override
    public boolean requiresPlayer() {
        return true;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1) {
            throw new WrongUsageException("/devtool " + getUsage());
        }
        ClientStepRoute route = ClientStepRoute.of(args);
        if (route == null) {
            throw new WrongUsageException("/devtool " + getUsage());
        }
        if (route.message == ClientStepRoute.Message.SCREEN) {
            executeNewStep(server, sender, route, route.screen.toWords(), new ScreenRequestFactory(route.screen));
            return;
        }
        if (route.message == ClientStepRoute.Message.INPUT) {
            executeNewStep(server, sender, route, route.input.toWords(), new InputRequestFactory(route.input));
            return;
        }
        if (route.message == ClientStepRoute.Message.PLAYER_ACTION) {
            executePlayerAction(sender, route);
            return;
        }
        String step = route.step;
        int away = 0;
        boolean logout = false;
        if (ClientActionRequest.RECONNECT.equals(step)) {
            int next = 1;
            if (args.length > next && !LOGOUT.equals(args[next])) {
                away = CommandBase.parseInt(args[next], 0, ClientActionRequest.MAX_AWAY_SECONDS);
                next++;
            }
            if (args.length > next && LOGOUT.equals(args[next])) {
                logout = true;
                next++;
            }
            if (args.length != next) {
                throw new WrongUsageException("/devtool client reconnect [awaySeconds] [logout]");
            }
        } else if (args.length != 1) {
            throw new WrongUsageException("/devtool client " + step);
        }

        EntityPlayerMP player = requireOwnPlayer(sender);
        refuseIfNeeded(player, route.gateStep(logout));

        long requestTick = player.world.getTotalWorldTime();
        // A remote client's leave always logs it out; only the single-player host can leave
        // without, which is the default there.
        boolean logsOut = logout || !player.connection.getNetworkManager().isLocalChannel();
        String token = ClientActionRequest.RECONNECT.equals(step) ? newToken() : "";
        // Written before sending: in single player a reconnect stops the very server writing it.
        LogRecord record = requested(sender, player, step);
        if (ClientActionRequest.RECONNECT.equals(step)) {
            record.add("awaySeconds", away);
            record.add("logout", logsOut);
        }
        record.add("requestTick", requestTick);
        ToolkitLog.write(record);

        if (ClientActionRequest.RECONNECT.equals(step)) {
            ClientCapabilities.expectReconnect(player, requestTick, token,
                    com.mahghuuuls.agenttesttoolkit.command.RunChannel.requestId(), logsOut);
        } else {
            ClientCapabilities.expect(player, step, requestTick);
        }
        if (ClientActionRequest.RECONNECT.equals(step) && InboxGate.isOperator(player.getUniqueID())) {
            // The inbox operator is about to leave on the toolkit's request: their next logout
            // suspends the inbox instead of closing it, until this token comes back (ARC-023).
            // It counts only once the client has said it is leaving (ARC-023).
            ToolkitState.setPendingRejoin(new PendingRejoin(player.getUniqueID(), token, requestTick,
                    System.currentTimeMillis()));
        }
        ToolkitChannel.send(new ClientActionRequest(step, away, token, requestTick, logout), player);
        sender.sendMessage(new TextComponentString("[DevToolkit] Asked your client to " + step + "."));
    }

    /**
     * A small player action on the sender's own client, under the same sender rule and the same
     * server checks as the lifecycle steps.
     */
    private static void executePlayerAction(ICommandSender sender, ClientStepRoute route) throws CommandException {
        EntityPlayerMP player = requireOwnPlayer(sender);
        PlayerActionOptions action = route.action;
        String step = action.getStep();
        refuseIfNeeded(player, route.gateStep(false));

        long requestTick = player.world.getTotalWorldTime();
        // Tells this request's answer from another of the same step asked in the same tick.
        String token = newToken();
        LogRecord record = requested(sender, player, step);
        if (action.getSlot() != PlayerActionOptions.NO_SLOT) {
            record.add("slot", action.getSlot());
        }
        if (PlayerActionOptions.HOLD.equals(step)) {
            record.add("input", action.getArgument());
            record.add("ticks", action.getTicks());
        }
        if (PlayerActionOptions.DROP.equals(step)) {
            record.add("all", PlayerActionOptions.ALL.equals(action.getArgument()));
        }
        if (PlayerActionOptions.TOOLTIP.equals(step)) {
            record.add("advanced", PlayerActionOptions.advancedOf(action.getArgument()));
            String keys = PlayerActionOptions.modifierKeysOf(action.getArgument());
            if (!keys.isEmpty()) {
                record.add("keys", keys);
            }
            record.add("showTicks", action.getTicks());
        }
        if (action.getDelay() > 0) {
            record.add("delay", action.getDelay());
        }
        if (!action.getScreenshot().isEmpty()) {
            record.add("screenshot", action.getScreenshot());
            record.add("screenshotDelay", action.getScreenshotDelay());
            if (!action.getScreenshotHide().isEmpty()) {
                record.add("screenshotHide", action.getScreenshotHide());
            }
        }
        record.add("requestTick", requestTick);
        ToolkitLog.write(record);

        ClientCapabilities.expectAction(player, step, requestTick, token);
        ToolkitChannel.send(new PlayerActionRequest(action, token, requestTick), player);
        sender.sendMessage(new TextComponentString("[DevToolkit] Asked your client to " + step + "."));
    }

    /**
     * Builds and sends the message of a step that only a 1.9.0 client reads, once its token is
     * chosen; false when the channel would not send it to this client.
     */
    private interface NewStepMessage {
        /**
         * What the server finds before sending, written into the request's record; refuses with
         * a key, after its own record, what the client could not act on.
         */
        void prepare(MinecraftServer server, EntityPlayerMP player, LogRecord record) throws CommandException;

        boolean send(EntityPlayerMP player, String token, long requestTick);
    }

    private static final class InputRequestFactory implements NewStepMessage {
        private final InputOptions options;
        /** The point a {@code face} turns to; null for every other step. */
        private double[] aim;

        InputRequestFactory(InputOptions options) {
            this.options = options;
        }

        @Override
        public void prepare(MinecraftServer server, EntityPlayerMP player, LogRecord record) throws CommandException {
            if (InputOptions.FACE.equals(options.getStep())) {
                aim = faceTarget(server, player, options, record);
            }
        }

        @Override
        public boolean send(EntityPlayerMP player, String token, long requestTick) {
            InputRequest request = new InputRequest(options, token, requestTick);
            if (aim != null) {
                request.withAim(aim[0], aim[1], aim[2]);
            }
            return ToolkitChannel.send(request, player);
        }
    }

    private static final class ScreenRequestFactory implements NewStepMessage {
        private final ScreenOptions options;

        ScreenRequestFactory(ScreenOptions options) {
            this.options = options;
        }

        @Override
        public void prepare(MinecraftServer server, EntityPlayerMP player, LogRecord record) {
            // A screen step names nothing the server must find.
        }

        @Override
        public boolean send(EntityPlayerMP player, String token, long requestTick) {
            return ToolkitChannel.send(new ScreenRequest(options, token, requestTick), player);
        }
    }

    /**
     * The point a {@code face} step turns to, found when it is asked: one loaded entity that is
     * not dead (any entity: a mob, an item, a minecart) in the sender's dimension, at its eyes' height, or a loaded block's centre or face centre. The
     * client turns to that point wherever the target moves afterward. Anything else is refused
     * here, so the client never turns toward a guess.
     */
    private static double[] faceTarget(MinecraftServer server, EntityPlayerMP player, InputOptions options,
                                       LogRecord record) throws CommandException {
        if (options.isFaceBlock()) {
            BlockPos pos = new BlockPos(options.getBlockX(), options.getBlockY(), options.getBlockZ());
            if (!player.world.isBlockLoaded(pos)) {
                refuseNoTarget(player, record, 0);
            }
            record.add("target", "block");
            record.addBlockPos("target", pos.getX(), pos.getY(), pos.getZ());
            return options.blockAimPoint();
        }
        List<Entity> found = new ArrayList<Entity>();
        try {
            for (Entity entity : EntityTarget.matchAll(server, player, options.getName())) {
                if (entity.world == player.world && !entity.isDead) {
                    found.add(entity);
                }
            }
        } catch (CommandException unmatched) {
            // A selector the engine cannot read finds nothing, the same as one that matches nothing.
        }
        if (found.size() != 1) {
            refuseNoTarget(player, record, found.size());
        }
        Entity entity = found.get(0);
        ResourceLocation type = EntityList.getKey(entity);
        record.add("target", "entity");
        record.add("entityUuid", entity.getUniqueID().toString());
        record.add("entityType", type == null ? entity.getClass().getSimpleName() : type.toString());
        return new double[] {entity.posX, entity.posY + entity.getEyeHeight(), entity.posZ};
    }

    /** One record of the refusal, then the key a bundle can tolerate. */
    private static void refuseNoTarget(EntityPlayerMP player, LogRecord record, int matched) throws CommandException {
        record.add("action", "refused");
        record.add("result", "refused");
        record.add("reason", REASON_NO_TARGET);
        record.add("matched", matched);
        // The tick the step was asked in, as on every other record of a request.
        record.add("requestTick", player.world.getTotalWorldTime());
        ToolkitLog.write(record);
        throw new CommandException(KEY_NO_TARGET, player.getName());
    }

    /**
     * A step of the input or screen message, under the same sender rule and server checks as the
     * player actions, sent only to a client that said hello with 1.9.0 or later. The server's
     * record names the step and its words, as the client will read them.
     */
    private static void executeNewStep(MinecraftServer server, ICommandSender sender, ClientStepRoute route,
                                       List<String> words, NewStepMessage message) throws CommandException {
        EntityPlayerMP player = requireOwnPlayer(sender);
        String step = route.step;
        ClientRequestGate.Step gateStep = route.gateStep(false);
        refuseIfNeeded(player, gateStep);

        long requestTick = player.world.getTotalWorldTime();
        String token = newToken();
        LogRecord record = requested(sender, player, step);
        StringBuilder options = new StringBuilder();
        for (int i = 1; i < words.size(); i++) {
            options.append(options.length() == 0 ? "" : " ").append(words.get(i));
        }
        record.add("options", options.toString());
        message.prepare(server, player, record);
        record.add("requestTick", requestTick);

        if (!message.send(player, token, requestTick)) {
            // The gate let through a client the channel will not send to; refused the same way,
            // before any request record, so the log never shows a request that was not sent.
            refuse(player, gateStep, ClientRequestGate.Refusal.CLIENT_TOO_OLD, ClientCapabilities.helloVersion(player),
                    ClientCapabilities.allowsActions(player));
        }
        ToolkitLog.write(record);
        ClientCapabilities.expectAction(player, step, requestTick, token);
        sender.sendMessage(new TextComponentString("[DevToolkit] Asked your client to " + step + "."));
    }

    /**
     * The sender itself, not the entity behind it: /execute wraps a sender with another entity,
     * and following that would act on someone else's client. Bundles and the inbox run as the
     * player object itself.
     */
    private static EntityPlayerMP requireOwnPlayer(ICommandSender sender) throws CommandException {
        if (!(sender instanceof EntityPlayerMP)) {
            throw new CommandException("[DevToolkit] client acts on your own client: run it as a player, not through /execute.");
        }
        return (EntityPlayerMP) sender;
    }

    /** Refuses at once, with a key, what the server already knows would fail. */
    private static void refuseIfNeeded(EntityPlayerMP player, ClientRequestGate.Step step) throws CommandException {
        Boolean allowed = ClientCapabilities.allowsActions(player);
        String helloVersion = ClientCapabilities.helloVersion(player);
        ClientRequestGate.Refusal refusal = ClientRequestGate.refusal(step, new ClientRequestGate.Facts(
                player.getHealth() <= 0.0F,
                ClientCapabilities.isBusy(player, com.mahghuuuls.agenttesttoolkit.config.ToolkitConfigLoader.get()
                        .getInboxRejoinWindowSeconds() * 1000L),
                ToolkitChannel.clientSpeaksActions(player),
                allowed,
                helloVersion));
        if (refusal != null) {
            refuse(player, step, refusal, helloVersion, allowed);
        }
    }

    /** Logs the refusal and fails the command with its key and the arguments its message shows. */
    private static void refuse(EntityPlayerMP player, ClientRequestGate.Step step, ClientRequestGate.Refusal refusal,
                               String helloVersion, Boolean allowed) throws CommandException {
        Object[] arguments = ClientRequestGate.arguments(refusal, player.getName(), helloVersion, step);
        ToolkitLog.error(refusal.message + " (" + step.name + ")",
                arguments[0] + (refusal == ClientRequestGate.Refusal.NOT_ALLOWED && allowed == null
                        ? " (no hello yet)" : ""));
        throw new CommandException(refusal.key, arguments);
    }

    /** The server's record of a request, before anything is sent. */
    private static LogRecord requested(ICommandSender sender, EntityPlayerMP player, String step) {
        LogRecord record = RecordContext.stamp(LogRecord.of(EventType.CLIENT_ACTION), sender);
        SessionStamp.apply(record);
        record.add("action", "requested");
        record.add("player", player.getName());
        record.add("step", step);
        return record;
    }

    private static String newToken() {
        StringBuilder out = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            out.append(Character.forDigit(RANDOM.nextInt(16), 16));
        }
        return out.toString();
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length <= 1) {
            java.util.List<String> steps = new java.util.ArrayList<String>(ClientActionRequest.STEPS);
            steps.addAll(PlayerActionOptions.STEPS);
            for (String step : InputOptions.STEPS) {
                if (!steps.contains(step)) {
                    steps.add(step);
                }
            }
            for (String step : ScreenOptions.STEPS) {
                if (!steps.contains(step)) {
                    steps.add(step);
                }
            }
            return CommandBase.getListOfStringsMatchingLastWord(args, steps);
        }
        return Collections.emptyList();
    }
}
