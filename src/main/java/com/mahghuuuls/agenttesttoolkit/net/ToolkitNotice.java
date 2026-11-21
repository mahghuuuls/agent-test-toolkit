package com.mahghuuuls.agenttesttoolkit.net;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server to client: one line the toolkit also said in the chat, and how many ticks the client
 * may show it elsewhere. Only a client at 1.10.0 or later is sent one; an older one has no
 * handler for it and would drop the connection.
 */
public final class ToolkitNotice implements IMessage {

    /** The longest line, the same as a prompt-panel line, where the client may show it. */
    static final int MAX_TEXT = 256;
    /** The longest a notice may be asked to stay, in ticks: one hour. */
    static final int MAX_TICKS = 72000;

    private String text;
    private int ticks;

    /** For Forge, which builds the message before reading it. */
    public ToolkitNotice() {
    }

    public ToolkitNotice(String text, int ticks) {
        this.text = text;
        this.ticks = ticks;
    }

    /** A short line free of control characters, shown for a bounded time, so it cannot break the screen. */
    public boolean isValid() {
        if (text == null || text.isEmpty() || text.length() > MAX_TEXT || ticks < 1 || ticks > MAX_TICKS) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (Character.isISOControl(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public String getText() {
        return text;
    }

    /** Ticks the client may show the line. */
    public int getTicks() {
        return ticks;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        text = ByteBufUtils.readUTF8String(buf);
        ticks = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, text);
        buf.writeInt(ticks);
    }

    /** Network thread: hands the notice to the client's receiver, when one is installed. */
    public static final class Handler implements IMessageHandler<ToolkitNotice, IMessage> {
        @Override
        public IMessage onMessage(ToolkitNotice message, MessageContext ctx) {
            ToolkitChannel.NoticeReceiver receiver = ToolkitChannel.noticeReceiver();
            if (receiver != null && message.isValid()) {
                receiver.receive(message);
            }
            return null;
        }
    }
}
