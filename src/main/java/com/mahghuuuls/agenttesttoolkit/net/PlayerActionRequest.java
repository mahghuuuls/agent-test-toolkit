package com.mahghuuuls.agenttesttoolkit.net;

import com.mahghuuuls.agenttesttoolkit.inbox.PlayerActionOptions;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server to client: take one small player action, such as a click.
 *
 * <p>A message of its own rather than another lifecycle step, so a client older than 1.7.0,
 * which has no handler for it, is simply never sent one: the server checks the client's hello
 * first. The client checks the action again with the same rules the server used.
 */
public final class PlayerActionRequest implements IMessage {

    private String step;
    private int slot;
    /** Tells this request's answer from another of the same step asked in the same tick. */
    private String token;
    private long requestTick;

    /** For Forge, which builds the message before reading it. */
    public PlayerActionRequest() {
    }

    public PlayerActionRequest(PlayerActionOptions action, String token, long requestTick) {
        this(action.getStep(), action.getSlot(), token, requestTick);
    }

    /** For tests: any values, valid or not. */
    PlayerActionRequest(String step, int slot, String token, long requestTick) {
        this.step = step;
        this.slot = slot;
        this.token = token;
        this.requestTick = requestTick;
    }

    public boolean isValid() {
        return PlayerActionOptions.isValid(step, slot) && ClientActionRequest.isValidToken(token);
    }

    public String getToken() {
        return token;
    }

    public String getStep() {
        return step;
    }

    /** Which step, so the client need not know where the step names are kept. */
    public boolean isSelect() {
        return PlayerActionOptions.SELECT.equals(step);
    }

    public boolean isUse() {
        return PlayerActionOptions.USE.equals(step);
    }

    public boolean isAttack() {
        return PlayerActionOptions.ATTACK.equals(step);
    }

    public int getSlot() {
        return slot;
    }

    public long getRequestTick() {
        return requestTick;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        step = ByteBufUtils.readUTF8String(buf);
        slot = buf.readInt();
        token = ByteBufUtils.readUTF8String(buf);
        requestTick = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, step);
        buf.writeInt(slot);
        ByteBufUtils.writeUTF8String(buf, token);
        buf.writeLong(requestTick);
    }

    /** Network thread: hands the action to the client's receiver; it runs on the client thread. */
    public static final class Handler implements IMessageHandler<PlayerActionRequest, IMessage> {
        @Override
        public IMessage onMessage(PlayerActionRequest message, MessageContext ctx) {
            ToolkitChannel.PlayerActionReceiver receiver = ToolkitChannel.playerActionReceiver();
            if (receiver != null && message.isValid()) {
                receiver.receive(message);
            }
            return null;
        }
    }
}
