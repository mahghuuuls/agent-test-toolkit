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

    /**
     * A step's final results, {@code left} for the half of a reconnect sent before leaving, and
     * {@code started} for the half of a hold sent when the input goes down.
     */
    static final java.util.List<String> RESULTS = java.util.Arrays.asList("done", "refused", "failed", "left", "started");

    private String step;
    private String result;
    private String reason;
    private String screen;
    private String showing;
    private String token;
    private long requestTick;
    /**
     * What a player action adds: what was looked at, the held item. Appended after the fields
     * every version reads, and only by a 1.7.0 client answering a 1.7.0 server's action, so an
     * older server never meets the extra bytes.
     */
    private final java.util.LinkedHashMap<String, String> fields = new java.util.LinkedHashMap<String, String>();
    /** More fields arrived than a report may carry; the report is dropped. */
    private boolean tooManyFields;

    /** A report carries at most this many extra fields. */
    static final int MAX_FIELDS = 32;
    /** And each value at most this many characters. */
    static final int MAX_FIELD_VALUE = 256;
    /**
     * The extra fields a client may report. Fixed, so a client can neither overwrite a field the
     * server writes itself nor fill the record with names of its own.
     */
    static final java.util.Set<String> FIELD_NAMES = java.util.Collections.unmodifiableSet(fieldNames());

    private static java.util.Set<String> fieldNames() {
        java.util.Set<String> names = new java.util.HashSet<String>(java.util.Arrays.asList(
                    "slot", "target", "targetX", "targetY", "targetZ", "face", "targetBlock",
                    "entityId", "entityType", "entityUuid", "held", "heldCount", "logout", "awayMs",
                    "focus", "input", "ticks", "heldTicks", "released", "all", "delay", "screenshot",
                    "item", "count", "advanced", "keys", "keysSeen", "lines", "closed", "showTicks"));
        // A tooltip's lines, as many as the client reports; its own record has more.
        for (int i = 1; i <= 20; i++) {
            names.add("line" + i);
        }
        return names;
    }

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

    /** Adds the fields of a player action, in the order given. Unknown names make the report invalid. */
    public ClientActionReport withFields(java.util.Map<String, String> extra) {
        if (extra != null) {
            fields.putAll(extra);
        }
        return this;
    }

    /** The extra fields, in the order the client wrote them. */
    public java.util.Map<String, String> getFields() {
        return java.util.Collections.unmodifiableMap(fields);
    }

    /** Lengths bounded and the step a known one, so a report cannot flood the log. */
    boolean isValid() {
        return (ClientActionRequest.isValidStep(step)
                || com.mahghuuuls.agenttesttoolkit.inbox.PlayerActionOptions.isStep(step))
                && RESULTS.contains(result)
                && reason.length() <= MAX_TEXT && screen.length() <= MAX_TEXT
                && showing.length() <= MAX_TEXT && ClientActionRequest.isValidToken(token)
                && fieldsValid();
    }

    private boolean fieldsValid() {
        if (tooManyFields || fields.size() > MAX_FIELDS) {
            return false;
        }
        for (java.util.Map.Entry<String, String> field : fields.entrySet()) {
            String value = field.getValue();
            if (!FIELD_NAMES.contains(field.getKey()) || value == null || value.length() > MAX_FIELD_VALUE
                    || hasControlCharacter(value)) {
                return false;
            }
        }
        return true;
    }

    /** A line break in a value would start a second log line that only looks like a record. */
    private static boolean hasControlCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return true;
            }
        }
        return false;
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
        fields.clear();
        if (buf.isReadable()) {
            int count = buf.readUnsignedByte();
            if (count > MAX_FIELDS) {
                tooManyFields = true;
                return;
            }
            for (int i = 0; i < count; i++) {
                String name = ByteBufUtils.readUTF8String(buf);
                fields.put(name, ByteBufUtils.readUTF8String(buf));
            }
        }
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
        // Nothing is appended without fields, so every report an older server can be sent
        // keeps the layout it reads.
        if (!fields.isEmpty()) {
            buf.writeByte(fields.size());
            for (java.util.Map.Entry<String, String> field : fields.entrySet()) {
                ByteBufUtils.writeUTF8String(buf, field.getKey());
                ByteBufUtils.writeUTF8String(buf, field.getValue());
            }
        }
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
