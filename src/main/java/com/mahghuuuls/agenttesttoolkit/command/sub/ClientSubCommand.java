package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.bundle.BundleExecution;
import com.mahghuuuls.agenttesttoolkit.bundle.Bundles;
import com.mahghuuuls.agenttesttoolkit.bundle.Replies;
import com.mahghuuuls.agenttesttoolkit.command.InboxClientFailures;
import com.mahghuuuls.agenttesttoolkit.command.SubCommand;
import com.mahghuuuls.agenttesttoolkit.inbox.ClientWaitOptions;
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
import com.mahghuuuls.agenttesttoolkit.wait.ClientActionWait;
import com.mahghuuuls.agenttesttoolkit.wait.ClientReports;
import com.mahghuuuls.agenttesttoolkit.wait.ClientWaitDecision;
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
    /** Failure key for an {@code expect} that matches several loaded entities in the sender's dimension. */
    public static final String KEY_AMBIGUOUS_TARGET = "devtool.client.ambiguousTarget";
    /** The reason in the server's record of that refusal. */
    static final String REASON_AMBIGUOUS_TARGET = "ambiguousTarget";
    /**
     * Failure key for {@code face ... expect=} asked of a client older than 1.10.0: such a client
     * does not report what the view faces after the turn, so the check could only ever fail.
     */
    public static final String KEY_EXPECT_NEEDS_NEW_CLIENT = "devtool.client.expectNeedsNewClient";
    /**
     * Failure key for {@code wait=}, {@code expect=} or {@code client await} outside a bundle: only a
     * bundle can pause for the answer, and a typed command must not block the server.
     */
    public static final String KEY_WAIT_OUTSIDE_BUNDLE = "devtool.client.waitOutsideBundle";

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
                + " [screenshotDelay=<ticks>] [screenshotHide=chat|hud|toasts[,...]]"
                + " [wait=started|done] [waitTicks=<1-1200>] [expect=<selector|uuid>]"
                + " | await <use|attack|binding> [waitTicks]";
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
        if (ClientWaitOptions.AWAIT.equals(args[0].toLowerCase(java.util.Locale.ROOT))) {
            executeAwait(sender, args);
            return;
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
        // A lifecycle step's reports carry no token, so it is marked without one.
        InboxClientFailures.markIfRequest(player.getUniqueID(), step, requestTick, "");
        if (ClientActionRequest.RECONNECT.equals(step) && InboxGate.isOperator(player.getUniqueID())) {
            // The inbox operator is about to leave on the toolkit's request: their next logout
            // suspends the inbox instead of closing it, until this token comes back (ARC-023).
            // It counts only once the client has said it is leaving (ARC-023).
            ToolkitState.setPendingRejoin(new PendingRejoin(player.getUniqueID(), token, requestTick,
                    System.currentTimeMillis()));
        }
        ToolkitChannel.send(new ClientActionRequest(step, away, token, requestTick, logout), player);
        Replies.send(sender, "[DevToolkit] Asked your client to " + step + ".");
    }

    /**
     * A small player action on the sender's own client, under the same sender rule and the same
     * server checks as the lifecycle steps.
     */
    private static void executePlayerAction(ICommandSender sender, ClientStepRoute route) throws CommandException {
        EntityPlayerMP player = requireOwnPlayer(sender);
        PlayerActionOptions action = route.action;
        String step = action.getStep();
        BundleExecution run = waitingRun(player, route);
        refuseIfNeeded(player, route.gateStep(false));

        long requestTick = player.world.getTotalWorldTime();
        // Tells this request's answer from another of the same step asked in the same tick.
        String token = newToken();
        LogRecord record = requested(sender, player, step);
        String expectUuid = expectedTarget(player.getServer(), player, route, record);
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
        addWait(record, route);
        record.add("requestTick", requestTick);
        ToolkitLog.write(record);

        ClientCapabilities.expectAction(player, step, requestTick, token);
        InboxClientFailures.markIfRequest(player.getUniqueID(), step, requestTick, token);
        ToolkitChannel.send(new PlayerActionRequest(action, token, requestTick), player);
        keepAndWait(run, player, route, step, token, requestTick, expectUuid);
        Replies.send(sender, asked(step, route));
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
        List<Entity> found = loadedTargets(server, player, options.getName());
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

    /**
     * Every entity a selector or UUID names that is loaded in the sender's dimension and not dead
     * (any entity: a mob, an item, a minecart). A mob in its death animation is found only by its
     * UUID: the UUID lookup returns it until it is removed, while a vanilla selector already skips
     * it, so a selector naming only a dying mob finds nothing.
     */
    private static List<Entity> loadedTargets(MinecraftServer server, EntityPlayerMP player, String name) {
        List<Entity> found = new ArrayList<Entity>();
        try {
            for (Entity entity : EntityTarget.matchAll(server, player, name)) {
                if (entity.world == player.world && !entity.isDead) {
                    found.add(entity);
                }
            }
        } catch (CommandException unmatched) {
            // A selector the engine cannot read finds nothing, the same as one that matches nothing.
        }
        return found;
    }

    /** One record of the refusal, then the key a bundle can tolerate. */
    private static void refuseNoTarget(EntityPlayerMP player, LogRecord record, int matched) throws CommandException {
        refuseTarget(player, record, matched, REASON_NO_TARGET, KEY_NO_TARGET);
    }

    private static void refuseTarget(EntityPlayerMP player, LogRecord record, int matched, String reason, String key)
            throws CommandException {
        record.add("action", "refused");
        record.add("result", "refused");
        record.add("reason", reason);
        record.add("matched", matched);
        // The tick the step was asked in, as on every other record of a request.
        record.add("requestTick", player.world.getTotalWorldTime());
        ToolkitLog.write(record);
        throw new CommandException(key, player.getName());
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
        BundleExecution run = waitingRun(player, route);
        ClientRequestGate.Step gateStep = route.gateStep(false);
        refuseIfNeeded(player, gateStep);
        String helloVersion = ClientCapabilities.helloVersion(player);
        String expectRefusal = expectRefusal(route, helloVersion);
        if (expectRefusal != null) {
            String detail = player.getName() + " (client toolkit " + (helloVersion == null ? "none" : helloVersion)
                    + ", face with expect needs 1.10.0)";
            ToolkitLog.error("Face with expect needs a newer toolkit on the client", detail);
            throw new CommandException(expectRefusal, detail);
        }

        long requestTick = player.world.getTotalWorldTime();
        String token = newToken();
        LogRecord record = requested(sender, player, step);
        StringBuilder options = new StringBuilder();
        for (int i = 1; i < words.size(); i++) {
            options.append(options.length() == 0 ? "" : " ").append(words.get(i));
        }
        record.add("options", options.toString());
        String expectUuid = expectedTarget(server, player, route, record);
        message.prepare(server, player, record);
        addWait(record, route);
        record.add("requestTick", requestTick);

        if (!message.send(player, token, requestTick)) {
            // The gate let through a client the channel will not send to; refused the same way,
            // before any request record, so the log never shows a request that was not sent.
            refuse(player, gateStep, ClientRequestGate.Refusal.CLIENT_TOO_OLD, ClientCapabilities.helloVersion(player),
                    ClientCapabilities.allowsActions(player));
        }
        ToolkitLog.write(record);
        ClientCapabilities.expectAction(player, step, requestTick, token);
        InboxClientFailures.markIfRequest(player.getUniqueID(), step, requestTick, token);
        keepAndWait(run, player, route, step, token, requestTick, expectUuid);
        Replies.send(sender, asked(step, route));
    }

    /**
     * The bundle run dispatching this step, which a waiting step needs; null outside a run. A step
     * that waits, or expects a target, is refused outside a run before anything is sent.
     */
    private static BundleExecution waitingRun(EntityPlayerMP player, ClientStepRoute route) throws CommandException {
        BundleExecution run = Bundles.scheduler().getCurrentExecution();
        if (route.waits() && run == null) {
            refuseOutsideBundle(player, route.step);
        }
        return run;
    }

    /**
     * The refusal's key for a step whose {@code expect} this client cannot answer: a {@code face}
     * with {@code expect}, asked of a client whose hello said a version older than 1.10.0, which does
     * not report what the view faces. Null when the step may be sent. A click or hold reports its
     * target from 1.7.0, so it is never refused here.
     */
    static String expectRefusal(ClientStepRoute route, String helloVersion) {
        if (!InputOptions.FACE.equals(route.step) || route.wait.getExpect().isEmpty()) {
            return null;
        }
        return helloVersion != null && ToolkitChannel.atLeast(helloVersion, ToolkitChannel.VERSION_1_10_0)
                ? null : KEY_EXPECT_NEEDS_NEW_CLIENT;
    }

    private static void refuseOutsideBundle(EntityPlayerMP player, String step) throws CommandException {
        ToolkitLog.error("A client wait needs a bundle (" + step + ")", player.getName());
        throw new CommandException(KEY_WAIT_OUTSIDE_BUNDLE, player.getName());
    }

    /**
     * Resolves {@code expect=} to exactly one loaded entity in the sender's dimension, as
     * {@code face} resolves its target, and names it in the request's record; null without
     * {@code expect}. None and several are refused at once, after the record says so.
     */
    private static String expectedTarget(MinecraftServer server, EntityPlayerMP player, ClientStepRoute route,
                                         LogRecord record) throws CommandException {
        if (route.wait.getExpect().isEmpty()) {
            return null;
        }
        List<Entity> found = loadedTargets(server, player, route.wait.getExpect());
        if (found.size() != 1) {
            refuseTarget(player, record, found.size(),
                    found.isEmpty() ? REASON_NO_TARGET : REASON_AMBIGUOUS_TARGET,
                    found.isEmpty() ? KEY_NO_TARGET : KEY_AMBIGUOUS_TARGET);
        }
        Entity entity = found.get(0);
        ResourceLocation type = EntityList.getKey(entity);
        record.add("expectUuid", entity.getUniqueID().toString());
        record.add("expectType", type == null ? entity.getClass().getSimpleName() : type.toString());
        return entity.getUniqueID().toString();
    }

    /** The wait a request asked for, in its record, so the request says what the bundle will do. */
    private static void addWait(LogRecord record, ClientStepRoute route) {
        if (route.wait.waits()) {
            record.add("wait", route.wait.getWait());
            record.add("waitTicks", route.wait.getWaitTicks());
        }
    }

    /**
     * Inside a run, keeps the step so its answer can be waited on: when it waits now, and for a
     * hold always, so a later {@code client await} can find it. Then parks the run on the answer
     * when the step waits. Outside a run nothing is kept, and the step is exactly the step it was
     * before waits existed.
     */
    private static void keepAndWait(BundleExecution run, EntityPlayerMP player, ClientStepRoute route, String step,
                                    String token, long requestTick, String expectUuid) {
        if (run == null || !route.keptInRun()) {
            return;
        }
        ClientReports.sent(run, player.getUniqueID(), token, step, requestTick, route.heldInput(), expectUuid);
        if (route.waits()) {
            run.await(ClientActionWait.forToken(player.getUniqueID(), token, route.wait.getWait(), null),
                    route.wait.getWaitTicks());
        }
    }

    /** The reply to a request, which says how long a waiting step's bundle pauses for the answer. */
    private static String asked(String step, ClientStepRoute route) {
        return "[DevToolkit] Asked your client to " + step + "."
                + (route.wait.waits() ? " Waiting up to " + route.wait.getWaitTicks() + " ticks for it to be "
                + route.wait.getWait() + "." : "");
    }

    /**
     * {@code client await <input> [waitTicks]}: parks the run until the latest hold of that input it
     * started ends, judged as a step's own {@code wait=done}. Sends nothing to the client.
     */
    private static void executeAwait(ICommandSender sender, String[] args) throws CommandException {
        EntityPlayerMP player = requireOwnPlayer(sender);
        String[] words = args.clone();
        words[0] = words[0].toLowerCase(java.util.Locale.ROOT);
        ClientWaitOptions.Await await = ClientWaitOptions.parseAwait(words);
        if (await == null) {
            throw new WrongUsageException("/devtool client await <use|attack|binding> [waitTicks]");
        }
        BundleExecution run = Bundles.scheduler().getCurrentExecution();
        if (run == null) {
            refuseOutsideBundle(player, ClientWaitOptions.AWAIT);
        }
        String token = ClientReports.latestHold(run, player.getUniqueID(), await.getInput());
        ClientActionWait wait = token == null ? null : ClientActionWait.forToken(player.getUniqueID(), token,
                ClientWaitDecision.MODE_DONE, await.getInput());
        if (wait == null) {
            ToolkitLog.error("No hold to await", await.getInput());
            throw new CommandException("[DevToolkit] No hold of " + await.getInput()
                    + " was started in this bundle, so there is none to await (only the last "
                    + ClientReports.MAX_STEPS + " steps a bundle sent are remembered).");
        }
        run.await(wait, await.getWaitTicks());
        Replies.send(sender, "[DevToolkit] Waiting up to " + await.getWaitTicks() + " ticks for the hold of "
                + await.getInput() + " to end.");
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
        ClientCapabilities.addIdentity(record, player);
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
            steps.add(ClientWaitOptions.AWAIT);
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
