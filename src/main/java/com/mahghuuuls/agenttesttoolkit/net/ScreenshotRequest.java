package com.mahghuuuls.agenttesttoolkit.net;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.regex.Pattern;

/**
 * Server to client: save a screenshot named after a label after a delay (ARC-017).
 *
 * <p>The label becomes a file name on the player's computer, so it is restricted to letters,
 * digits, underscore and hyphen: no path separator, no dot, no space. Both sides check it.
 */
public final class ScreenshotRequest implements IMessage {

    public static final int MAX_DELAY_TICKS = 600;
    private static final Pattern LABEL = Pattern.compile("[A-Za-z0-9_-]{1,48}");

    private String label;
    private int delayTicks;
    private long requestTick;

    /** For Forge, which builds the message before reading it. */
    public ScreenshotRequest() {
    }

    public ScreenshotRequest(String label, int delayTicks, long requestTick) {
        this.label = label;
        this.delayTicks = delayTicks;
        this.requestTick = requestTick;
    }

    public static boolean isValidLabel(String label) {
        return label != null && LABEL.matcher(label).matches();
    }

    public static boolean isValidDelay(int delayTicks) {
        return delayTicks >= 0 && delayTicks <= MAX_DELAY_TICKS;
    }

    /** True when both fields are acceptable; a message failing this is dropped by the client. */
    public boolean isValid() {
        return isValidLabel(label) && isValidDelay(delayTicks);
    }

    /** {@code <label>-<requestTick>.png}, the file under the client's {@code screenshots} folder. */
    public String fileName() {
        return label + "-" + requestTick + ".png";
    }

    public String getLabel() {
        return label;
    }

    public int getDelayTicks() {
        return delayTicks;
    }

    public long getRequestTick() {
        return requestTick;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        label = ByteBufUtils.readUTF8String(buf);
        delayTicks = buf.readInt();
        requestTick = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, label);
        buf.writeInt(delayTicks);
        buf.writeLong(requestTick);
    }

    /**
     * Runs on the network thread, so it only hands the request to the receiver the client
     * installed; the capture itself happens on the client's main thread. On a dedicated server
     * no receiver is installed and nothing is referenced from the client.
     */
    public static final class Handler implements IMessageHandler<ScreenshotRequest, IMessage> {
        @Override
        public IMessage onMessage(ScreenshotRequest message, MessageContext ctx) {
            ToolkitChannel.ClientReceiver receiver = ToolkitChannel.clientReceiver();
            if (receiver != null && message.isValid()) {
                receiver.receive(message);
            }
            return null;
        }
    }
}
