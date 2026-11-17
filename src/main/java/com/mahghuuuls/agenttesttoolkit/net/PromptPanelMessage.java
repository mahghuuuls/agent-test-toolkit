package com.mahghuuuls.agenttesttoolkit.net;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Server to client: show the toolkit's prompt panel for a checkpoint, or take it away.
 *
 * <p>A show carries the checkpoint's token, its label, the lines to draw under it and how many
 * ticks the panel may stay; a hide carries only the token, so a late hide cannot remove a newer
 * panel. Only a client at 1.9.0 or later is sent one; an older one keeps its earlier prompt.
 */
public final class PromptPanelMessage implements IMessage {

    /** The longest label drawn. */
    public static final int MAX_LABEL = 128;
    /** The most lines under the label. */
    static final int MAX_LINES = 8;
    /** The longest line. */
    static final int MAX_LINE = 256;
    /** The longest a panel may be asked to stay, in ticks: one hour. */
    static final int MAX_TIMEOUT_TICKS = 72000;

    private boolean show;
    private String token;
    private String label = "";
    private List<String> lines = new ArrayList<String>();
    private int timeoutTicks;

    /** For Forge, which builds the message before reading it. */
    public PromptPanelMessage() {
    }

    private PromptPanelMessage(boolean show, String token, String label, List<String> lines, int timeoutTicks) {
        this.show = show;
        this.token = token;
        this.label = label == null ? "" : label;
        this.lines = lines == null ? new ArrayList<String>() : new ArrayList<String>(lines);
        this.timeoutTicks = timeoutTicks;
    }

    /** Shows the panel; 0 ticks keeps it until it is hidden. */
    public static PromptPanelMessage show(String token, String label, List<String> lines, int timeoutTicks) {
        return new PromptPanelMessage(true, token, label, lines, timeoutTicks);
    }

    /** Takes away the panel shown with this token. */
    public static PromptPanelMessage hide(String token) {
        return new PromptPanelMessage(false, token, "", null, 0);
    }

    /** Bounded, and free of control characters, so a panel cannot fill the screen or break a line. */
    public boolean isValid() {
        if (!ClientActionRequest.isValidToken(token) || token.isEmpty()) {
            return false;
        }
        if (!show) {
            return label.isEmpty() && lines.isEmpty() && timeoutTicks == 0;
        }
        if (label.isEmpty() || !isText(label, MAX_LABEL) || lines.size() > MAX_LINES
                || timeoutTicks < 0 || timeoutTicks > MAX_TIMEOUT_TICKS) {
            return false;
        }
        for (String line : lines) {
            if (!isText(line, MAX_LINE)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isText(String text, int max) {
        if (text == null || text.length() > max) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (Character.isISOControl(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public boolean isShow() {
        return show;
    }

    /**
     * Whether this message takes away the panel shown with this token. Only a hide for that very
     * panel does: a hide sent for an older panel arrives after the newer one replaced it and must
     * leave the newer one up.
     */
    public boolean hides(String shownToken) {
        return !show && token != null && token.equals(shownToken);
    }

    public String getToken() {
        return token;
    }

    public String getLabel() {
        return label;
    }

    public List<String> getLines() {
        return Collections.unmodifiableList(lines);
    }

    /** Ticks the panel may stay; 0 keeps it until it is hidden. */
    public int getTimeoutTicks() {
        return timeoutTicks;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        show = buf.readBoolean();
        token = ByteBufUtils.readUTF8String(buf);
        if (!show) {
            return;
        }
        label = ByteBufUtils.readUTF8String(buf);
        int count = ByteBufUtils.readVarInt(buf, 5);
        lines = new ArrayList<String>();
        if (count < 0 || count > MAX_LINES) {
            // Too many to be valid; the rest is not read, and the message is dropped.
            lines.add(null);
            buf.skipBytes(buf.readableBytes());
            return;
        }
        for (int i = 0; i < count; i++) {
            lines.add(ByteBufUtils.readUTF8String(buf));
        }
        timeoutTicks = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(show);
        ByteBufUtils.writeUTF8String(buf, token);
        if (!show) {
            return;
        }
        ByteBufUtils.writeUTF8String(buf, label);
        ByteBufUtils.writeVarInt(buf, lines.size(), 5);
        for (String line : lines) {
            ByteBufUtils.writeUTF8String(buf, line);
        }
        buf.writeInt(timeoutTicks);
    }

    /** Network thread: hands the panel to the client's receiver, when one is installed. */
    public static final class Handler implements IMessageHandler<PromptPanelMessage, IMessage> {
        @Override
        public IMessage onMessage(PromptPanelMessage message, MessageContext ctx) {
            ToolkitChannel.PanelReceiver receiver = ToolkitChannel.panelReceiver();
            if (receiver != null && message.isValid()) {
                receiver.receive(message);
            }
            return null;
        }
    }
}
