package com.mahghuuuls.agenttesttoolkit.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server: a picture the server asked for was saved, or could not be.
 *
 * <p>Carries the request's label and tick, so the server can tie it to the request it sent, the
 * file under the client's game folder (empty when none was written), whether it was saved, and
 * the client tick of the captured frame. Only a client at 1.10.0 or later sends one, and the
 * server ignores one from a client whose hello said an older version.
 */
public final class ScreenshotReport implements IMessage {

    /** The longest file path accepted; the client writes {@code screenshots/<label>-<tick>.png}. */
    static final int MAX_FILE = 128;

    private String label;
    private long requestTick;
    private String file = "";
    private boolean saved;
    private long frameClientTick;

    /** For Forge, which builds the message before reading it. */
    public ScreenshotReport() {
    }

    public ScreenshotReport(String label, long requestTick, String file, boolean saved, long frameClientTick) {
        this.label = label;
        this.requestTick = requestTick;
        this.file = file == null ? "" : file;
        this.saved = saved;
        this.frameClientTick = frameClientTick;
    }

    /**
     * The label follows the request's own rule, and the file is short and free of control
     * characters, so a report cannot write an unreadable record line.
     */
    public boolean isValid() {
        if (!ScreenshotRequest.isValidLabel(label) || file == null || file.length() > MAX_FILE) {
            return false;
        }
        for (int i = 0; i < file.length(); i++) {
            if (Character.isISOControl(file.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the server acts on this report from a client that said hello with this version:
     * only a valid one from a 1.10.0 client or later, since an older one was never asked to report.
     */
    boolean isAcceptedFrom(String helloVersion) {
        return isValid() && ToolkitChannel.readsNoticesAndSendsReports(helloVersion);
    }

    public String getLabel() {
        return label;
    }

    public long getRequestTick() {
        return requestTick;
    }

    /** The file under the client's game folder; empty when none was written. */
    public String getFile() {
        return file;
    }

    public boolean isSaved() {
        return saved;
    }

    /** The client tick of the frame that was captured. */
    public long getFrameClientTick() {
        return frameClientTick;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        label = ByteBufUtils.readUTF8String(buf);
        requestTick = buf.readLong();
        file = ByteBufUtils.readUTF8String(buf);
        saved = buf.readBoolean();
        frameClientTick = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, label);
        buf.writeLong(requestTick);
        ByteBufUtils.writeUTF8String(buf, file);
        buf.writeBoolean(saved);
        buf.writeLong(frameClientTick);
    }

    /**
     * Runs on the network thread; the report reaches the receiver on the server thread, with the
     * current player object. Dropped when invalid, when no receiver is installed, or when the
     * client's hello did not say 1.10.0 or later, since such a client was never asked to report.
     */
    public static final class Handler implements IMessageHandler<ScreenshotReport, IMessage> {
        @Override
        public IMessage onMessage(final ScreenshotReport message, MessageContext ctx) {
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
                    ToolkitChannel.ReportReceiver receiver = ToolkitChannel.reportReceiver();
                    if (current != null && receiver != null
                            && message.isAcceptedFrom(ClientCapabilities.helloVersion(current))) {
                        receiver.receive(current, message);
                    }
                }
            });
            return null;
        }
    }
}
