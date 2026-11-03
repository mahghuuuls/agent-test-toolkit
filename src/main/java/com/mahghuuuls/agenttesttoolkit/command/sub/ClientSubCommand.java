package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.command.SubCommand;
import com.mahghuuuls.agenttesttoolkit.inbox.InboxGate;
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

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public String getName() {
        return "client";
    }

    @Override
    public String getDescription() {
        return "Ask your own client to reconnect, respawn, or close the open screen";
    }

    @Override
    public String getUsage() {
        return "client <reconnect [awaySeconds] | respawn | close>";
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
        if (!ClientActionRequest.isValidStep(step)) {
            throw new WrongUsageException("/devtool " + getUsage());
        }
        int away = 0;
        if (ClientActionRequest.RECONNECT.equals(step)) {
            if (args.length > 2) {
                throw new WrongUsageException("/devtool client reconnect [awaySeconds]");
            }
            if (args.length == 2) {
                away = CommandBase.parseInt(args[1], 0, ClientActionRequest.MAX_AWAY_SECONDS);
            }
        } else if (args.length != 1) {
            throw new WrongUsageException("/devtool client " + step);
        }

        // The sender itself, not the entity behind it: /execute wraps a sender with another
        // entity, and following that would act on someone else's client. Bundles and the inbox
        // run as the player object itself.
        if (!(sender instanceof EntityPlayerMP)) {
            throw new CommandException("[DevToolkit] client acts on your own client: run it as a player, not through /execute.");
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        if (ClientActionRequest.RESPAWN.equals(step) && player.getHealth() > 0.0F) {
            // Known here at once; the client hears of the death a tick later.
            ToolkitLog.error("Client respawn refused: not dead", player.getName());
            throw new CommandException(KEY_NOT_DEAD, player.getName());
        }
        if (ClientCapabilities.isBusy(player,
                com.mahghuuuls.agenttesttoolkit.config.ToolkitConfigLoader.get().getInboxRejoinWindowSeconds() * 1000L)) {
            // A reconnect is under way: a second step would be lost in the leave, or answered in
            // place of the reconnect's own report.
            ToolkitLog.error("Client action refused: a reconnect is under way", player.getName());
            throw new CommandException(KEY_BUSY, player.getName());
        }
        if (!ToolkitChannel.clientSpeaksActions(player)) {
            // No toolkit, or one older than 1.4.0: it never says hello and could not read a request.
            ToolkitLog.error("Client action target has no toolkit 1.4.0 or later", player.getName());
            throw new CommandException(KEY_NO_CLIENT, player.getName());
        }
        Boolean allowed = ClientCapabilities.allowsActions(player);
        if (!Boolean.TRUE.equals(allowed)) {
            ToolkitLog.error("Client has not allowed actions",
                    player.getName() + (allowed == null ? " (no hello yet)" : ""));
            throw new CommandException(KEY_NOT_ALLOWED, player.getName());
        }

        long requestTick = player.world.getTotalWorldTime();
        String token = ClientActionRequest.RECONNECT.equals(step) ? newToken() : "";
        // Written before sending: in single player a reconnect stops the very server writing it.
        LogRecord record = RecordContext.stamp(LogRecord.of(EventType.CLIENT_ACTION), sender);
        SessionStamp.apply(record);
        record.add("action", "requested");
        record.add("player", player.getName());
        record.add("step", step);
        if (ClientActionRequest.RECONNECT.equals(step)) {
            record.add("awaySeconds", away);
        }
        record.add("requestTick", requestTick);
        ToolkitLog.write(record);

        ClientCapabilities.expect(player, step, requestTick);
        if (ClientActionRequest.RECONNECT.equals(step) && InboxGate.isOperator(player.getUniqueID())) {
            // The inbox operator is about to leave on the toolkit's request: their next logout
            // suspends the inbox instead of closing it, until this token comes back (ARC-023).
            // It counts only once the client has said it is leaving (ARC-023).
            ToolkitState.setPendingRejoin(new PendingRejoin(player.getUniqueID(), token, requestTick,
                    System.currentTimeMillis()));
        }
        ToolkitChannel.send(new ClientActionRequest(step, away, token, requestTick), player);
        sender.sendMessage(new TextComponentString("[DevToolkit] Asked your client to " + step + "."));
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
            return CommandBase.getListOfStringsMatchingLastWord(args, ClientActionRequest.STEPS);
        }
        return Collections.emptyList();
    }
}
