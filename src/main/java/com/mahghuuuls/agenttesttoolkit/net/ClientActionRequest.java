package com.mahghuuuls.agenttesttoolkit.net;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Server to client: take one lifecycle step (ARC-022, REQ-202).
 *
 * <p>Only the four steps of D18 exist, and both sides check the message, so a server cannot
 * name anything else. The token travels with a reconnect so the client can prove, on return,
 * that it is the client that was sent away (ARC-023).
 */
public final class ClientActionRequest implements IMessage {

    public static final String RECONNECT = "reconnect";
    public static final String RESPAWN = "respawn";
    public static final String CLOSE = "close";
    public static final List<String> STEPS = Arrays.asList(RECONNECT, RESPAWN, CLOSE);
    public static final int MAX_AWAY_SECONDS = 60;
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9]{0,64}");

    private String step;
    private int awaySeconds;
    private String token;
    private long requestTick;

    /** For Forge, which builds the message before reading it. */
    public ClientActionRequest() {
    }

    public ClientActionRequest(String step, int awaySeconds, String token, long requestTick) {
        this.step = step;
        this.awaySeconds = awaySeconds;
        this.token = token == null ? "" : token;
        this.requestTick = requestTick;
    }

    public static boolean isValidStep(String step) {
        return step != null && STEPS.contains(step);
    }

    public static boolean isValidAway(int awaySeconds) {
        return awaySeconds >= 0 && awaySeconds <= MAX_AWAY_SECONDS;
    }

    static boolean isValidToken(String token) {
        return token != null && TOKEN.matcher(token).matches();
    }

    /** True when every field is acceptable; a message failing this is dropped by the client. */
    public boolean isValid() {
        return isValidStep(step) && isValidAway(awaySeconds) && isValidToken(token);
    }

    public String getStep() {
        return step;
    }

    public int getAwaySeconds() {
        return awaySeconds;
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
        awaySeconds = buf.readInt();
        token = ByteBufUtils.readUTF8String(buf);
        requestTick = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, step);
        buf.writeInt(awaySeconds);
        ByteBufUtils.writeUTF8String(buf, token);
        buf.writeLong(requestTick);
    }

    /**
     * Runs on the network thread, so it only hands the request to the receiver the client
     * installed; every step runs on the client's main thread.
     */
    public static final class Handler implements IMessageHandler<ClientActionRequest, IMessage> {
        @Override
        public IMessage onMessage(ClientActionRequest message, MessageContext ctx) {
            ToolkitChannel.ActionReceiver receiver = ToolkitChannel.actionReceiver();
            if (receiver != null && message.isValid()) {
                receiver.receive(message);
            }
            return null;
        }
    }
}
