package com.mahghuuuls.agenttesttoolkit.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server, after the server's hello: the local-join secret the client found in its own
 * game folder (D24, REQ-222, ARC-029). Sent only to a server at 1.5.0 or later (ARC-030); the
 * server compares it and may open the command inbox for that player.
 */
public final class LocalJoinProof implements IMessage {

    static final int MAX_LENGTH = 64;
    private String proof;

    /** For Forge, which builds the message before reading it. */
    public LocalJoinProof() {
    }

    public LocalJoinProof(String proof) {
        this.proof = proof == null ? "" : proof;
    }

    boolean isValid() {
        return proof != null && proof.length() <= MAX_LENGTH;
    }

    public String getProof() {
        return proof;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        proof = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, proof);
    }

    /** Runs on the network thread; the comparison and the opening on the server thread. */
    public static final class Handler implements IMessageHandler<LocalJoinProof, IMessage> {
        @Override
        public IMessage onMessage(final LocalJoinProof message, MessageContext ctx) {
            EntityPlayerMP sent = ctx.getServerHandler().player;
            final java.util.UUID player = sent == null ? null : sent.getUniqueID();
            final MinecraftServer server = sent == null ? null : sent.getServer();
            if (server == null || !message.isValid()) {
                return null;
            }
            server.addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    EntityPlayerMP current = server.getPlayerList().getPlayerByUUID(player);
                    if (current != null) {
                        com.mahghuuuls.agenttesttoolkit.inbox.LocalJoinListener.onProof(current, message.getProof());
                    }
                }
            });
            return null;
        }
    }
}
