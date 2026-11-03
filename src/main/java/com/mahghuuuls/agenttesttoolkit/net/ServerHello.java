package com.mahghuuuls.agenttesttoolkit.net;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server to client, at every login: this server understands the client-action messages
 * (ARC-022). The client answers with its {@link ClientHello}, and a client back from a
 * reconnect with its report.
 *
 * <p>The server speaks first because only the server sees the other side's mod list in 1.12.2:
 * the client's copy of the handshake list stays empty. It is sent only to a client whose toolkit
 * is new enough to read it ({@link ToolkitChannel#speaksActions}), and a client hears it only
 * from a server new enough to send it, so neither side is ever sent a message it cannot decode.
 */
public final class ServerHello implements IMessage {

    private String version;

    /** For Forge, which builds the message before reading it. */
    public ServerHello() {
    }

    public ServerHello(String version) {
        this.version = version == null ? "" : version;
    }

    public String getVersion() {
        return version;
    }

    boolean isValid() {
        return version != null && version.length() <= 64;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        version = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, version);
    }

    /** Runs on the network thread; the receiver queues it for the client thread. */
    public static final class Handler implements IMessageHandler<ServerHello, IMessage> {
        @Override
        public IMessage onMessage(ServerHello message, MessageContext ctx) {
            ToolkitChannel.ActionReceiver receiver = ToolkitChannel.actionReceiver();
            if (receiver != null && message.isValid()) {
                receiver.serverHello(message.getVersion());
            }
            return null;
        }
    }
}
