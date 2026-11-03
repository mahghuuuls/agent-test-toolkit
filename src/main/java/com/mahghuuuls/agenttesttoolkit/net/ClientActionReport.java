package com.mahghuuuls.agenttesttoolkit.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server: how one lifecycle step went (ARC-022).
 *
 * <p>The client also writes its own record, but on a dedicated server that lands in the
 * client's log, so the server writes the report into its own. A report concerns only the player
 * whose connection carried it; nothing here names another player.
 */
public final class ClientActionReport implements IMessage {

    private static final int MAX_TEXT = 128;

    /** A step's final results, and {@code left} for the half of a reconnect sent before leaving. */
    static final java.util.List<String> RESULTS = java.util.Arrays.asList("done", "refused", "failed", "left");

    private String step;
    private String result;
    private String reason;
    private String screen;
    private String showing;
    private String token;
    private long requestTick;

    /** For Forge, which builds the message before reading it. */
    public ClientActionReport() {
    }

    public ClientActionReport(String step, String result, String reason, String screen, String showing,
                              String token, long requestTick) {
        this.step = nonNull(step);
        this.result = nonNull(result);
        this.reason = nonNull(reason);
        this.screen = nonNull(screen);
        this.showing = nonNull(showing);
        this.token = nonNull(token);
        this.requestTick = requestTick;
    }

    private static String nonNull(String text) {
        return text == null ? "" : text;
    }

    /** Lengths bounded and the step one of the four, so a report cannot flood the log. */
    boolean isValid() {
        return ClientActionRequest.isValidStep(step) && RESULTS.contains(result)
                && reason.length() <= MAX_TEXT && screen.length() <= MAX_TEXT
                && showing.length() <= MAX_TEXT && ClientActionRequest.isValidToken(token);
    }

    public String getStep() {
        return step;
    }

    public String getResult() {
        return result;
    }

    public String getReason() {
        return reason;
    }

    public String getScreen() {
        return screen;
    }

    public String getShowing() {
        return showing;
    }

    public String getToken() {
        return token;
    }

    public long getRequestTick() {
        return requestTick;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        step = ByteBufUtils.readUTF8String(buf);
        result = ByteBufUtils.readUTF8String(buf);
        reason = ByteBufUtils.readUTF8String(buf);
        screen = ByteBufUtils.readUTF8String(buf);
        showing = ByteBufUtils.readUTF8String(buf);
        token = ByteBufUtils.readUTF8String(buf);
        requestTick = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, step);
        ByteBufUtils.writeUTF8String(buf, result);
        ByteBufUtils.writeUTF8String(buf, reason);
        ByteBufUtils.writeUTF8String(buf, screen);
        ByteBufUtils.writeUTF8String(buf, showing);
        ByteBufUtils.writeUTF8String(buf, token);
        buf.writeLong(requestTick);
    }

    /** Runs on the network thread; the record is written on the server thread. */
    public static final class Handler implements IMessageHandler<ClientActionReport, IMessage> {
        @Override
        public IMessage onMessage(final ClientActionReport message, MessageContext ctx) {
            EntityPlayerMP sent = ctx.getServerHandler().player;
            final java.util.UUID player = sent == null ? null : sent.getUniqueID();
            final MinecraftServer server = sent == null ? null : sent.getServer();
            if (server == null || !message.isValid()) {
                return null;
            }
            server.addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    // The current player object: a respawn report usually arrives before the
                    // server has replaced the dead one.
                    EntityPlayerMP current = server.getPlayerList().getPlayerByUUID(player);
                    if (current != null) {
                        ClientCapabilities.report(current, message);
                    }
                }
            });
            return null;
        }
    }
}
