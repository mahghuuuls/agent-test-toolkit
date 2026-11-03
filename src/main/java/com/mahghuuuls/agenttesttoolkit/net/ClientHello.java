package com.mahghuuuls.agenttesttoolkit.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server, in answer to {@link ServerHello} at every login: the toolkit's version and
 * whether this client lets a server ask it for a lifecycle step (ARC-022, REQ-203).
 *
 * <p>Sent so the server can refuse a request it knows the client would refuse, with a key, at
 * once, instead of learning it a round trip later. The answer is kept per player and forgotten
 * at logout ({@link ClientCapabilities}).
 */
public final class ClientHello implements IMessage {

    private String version;
    private boolean allowActions;

    /** For Forge, which builds the message before reading it. */
    public ClientHello() {
    }

    public ClientHello(String version, boolean allowActions) {
        this.version = version == null ? "" : version;
        this.allowActions = allowActions;
    }

    /** This build's hello. */
    public static ClientHello ofThisBuild(boolean allowActions) {
        return new ClientHello(com.mahghuuuls.agenttesttoolkit.Tags.VERSION, allowActions);
    }

    /** A version string of reasonable length; the only free text a hello carries. */
    boolean isValid() {
        return version != null && version.length() <= 64;
    }

    public String getVersion() {
        return version;
    }

    public boolean isAllowActions() {
        return allowActions;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        version = ByteBufUtils.readUTF8String(buf);
        allowActions = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, version);
        buf.writeBoolean(allowActions);
    }

    /** Runs on the network thread; the answer is stored on the server thread. */
    public static final class Handler implements IMessageHandler<ClientHello, IMessage> {
        @Override
        public IMessage onMessage(final ClientHello message, MessageContext ctx) {
            EntityPlayerMP sent = ctx.getServerHandler().player;
            final java.util.UUID player = sent == null ? null : sent.getUniqueID();
            final MinecraftServer server = sent == null ? null : sent.getServer();
            if (server == null || !message.isValid()) {
                return null;
            }
            server.addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    // Looked up again on the server thread, so a player who respawned in the
                    // meantime is the current object.
                    EntityPlayerMP current = server.getPlayerList().getPlayerByUUID(player);
                    if (current != null) {
                        ClientCapabilities.hello(current, message.getVersion(), message.isAllowActions());
                    }
                }
            });
            return null;
        }
    }
}
