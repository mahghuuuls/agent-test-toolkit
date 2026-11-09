package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.command.SubCommand;
import com.mahghuuuls.agenttesttoolkit.inbox.InboxGate;
import com.mahghuuuls.agenttesttoolkit.inbox.PlayerActionOptions;
import com.mahghuuuls.agenttesttoolkit.net.PlayerActionRequest;
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
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

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
        return "client <reconnect [awaySeconds] [logout] | respawn | close | quit | select <0-8> | use | attack"
                + " | hold <use|attack> <ticks> | drop [all] | inventory | tooltip <0-40> [advanced] [shift] [ctrl] [alt] [showTicks=<ticks>]>"
                + " [delay=<ticks>] [screenshot=<label>]"
                + " [screenshotDelay=<ticks>]";
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
        String step = args[0].toLowerCase(Locale.ROOT);
        if (PlayerActionOptions.isStep(step)) {
            String[] words = args.clone();
            words[0] = step;
            PlayerActionOptions action = PlayerActionOptions.parse(words);
            if (action == null) {
                throw new WrongUsageException("/devtool " + getUsage());
            }
            executePlayerAction(sender, action);
            return;
        }
        if (!ClientActionRequest.isValidStep(step)) {
            throw new WrongUsageException("/devtool " + getUsage());
        }
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
        refuseIfNeeded(player, step, logout);

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
    private static void executePlayerAction(ICommandSender sender, PlayerActionOptions action) throws CommandException {
        EntityPlayerMP player = requireOwnPlayer(sender);
        String step = action.getStep();
        refuseIfNeeded(player, step, false);

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
        }
        record.add("requestTick", requestTick);
        ToolkitLog.write(record);

        ClientCapabilities.expectAction(player, step, requestTick, token);
        ToolkitChannel.send(new PlayerActionRequest(action, token, requestTick), player);
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
    private static void refuseIfNeeded(EntityPlayerMP player, String step, boolean logout) throws CommandException {
        Boolean allowed = ClientCapabilities.allowsActions(player);
        ClientRequestGate.Refusal refusal = ClientRequestGate.refusal(step, logout, new ClientRequestGate.Facts(
                player.getHealth() <= 0.0F,
                ClientCapabilities.isBusy(player, com.mahghuuuls.agenttesttoolkit.config.ToolkitConfigLoader.get()
                        .getInboxRejoinWindowSeconds() * 1000L),
                ToolkitChannel.clientSpeaksActions(player),
                allowed,
                ClientCapabilities.helloVersion(player)));
        if (refusal != null) {
            ToolkitLog.error(refusal.message + " (" + step + ")",
                    player.getName() + (refusal == ClientRequestGate.Refusal.NOT_ALLOWED && allowed == null
                            ? " (no hello yet)" : ""));
            throw new CommandException(refusal.key, player.getName());
        }
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
            return CommandBase.getListOfStringsMatchingLastWord(args, steps);
        }
        return Collections.emptyList();
    }
}
