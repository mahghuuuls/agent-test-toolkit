package com.mahghuuuls.agenttesttoolkit.net;

import com.mahghuuuls.agenttesttoolkit.inbox.InputOptions;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.List;

/**
 * Server to client: one input step, such as a key binding pressed or the view turned.
 *
 * <p>A message of its own, so a client older than 1.9.0, which has no handler for it, is never
 * sent one: the server checks the client's hello first. The step travels as the parser's own
 * words and is parsed again on arrival, so the client acts only on what the server's parser
 * would have accepted.
 */
public final class InputRequest implements IMessage {

    /** More words than any step has; a longer list is not read. */
    static final int MAX_WORDS = 32;

    private String[] words;
    private InputOptions options;
    /** Tells this request's answer from another of the same step asked in the same tick. */
    private String token;
    private long requestTick;
    /** Where the server found a {@code face} target when the step was asked; unset otherwise. */
    private boolean aimed;
    private double aimX;
    private double aimY;
    private double aimZ;

    /** For Forge, which builds the message before reading it. */
    public InputRequest() {
    }

    public InputRequest(InputOptions options, String token, long requestTick) {
        this(toArray(options.toWords()), token, requestTick);
    }

    /** For tests: any words, valid or not. */
    InputRequest(String[] words, String token, long requestTick) {
        this.words = words;
        this.options = InputOptions.parse(words);
        this.token = token;
        this.requestTick = requestTick;
    }

    private static String[] toArray(List<String> words) {
        return words.toArray(new String[0]);
    }

    /** The point a {@code face} step turns to, as the server resolved it. */
    public InputRequest withAim(double x, double y, double z) {
        aimed = true;
        aimX = x;
        aimY = y;
        aimZ = z;
        return this;
    }

    public boolean isValid() {
        return options != null && ClientActionRequest.isValidToken(token)
                && (!aimed || (isFinite(aimX) && isFinite(aimY) && isFinite(aimZ)));
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    /** The parsed step; null when the words were not a step. */
    public InputOptions getOptions() {
        return options;
    }

    /** Whether the client can answer it, valid or not: its token tells the server which request it was. */
    public boolean isAnswerable() {
        return words != null && ClientActionRequest.isValidToken(token);
    }

    /** The step's name; for words that did not parse, their first word, at most 64 characters. */
    public String getStep() {
        if (options != null) {
            return options.getStep();
        }
        String first = words == null || words.length == 0 ? "" : words[0];
        return first.length() <= 64 ? first : first.substring(0, 64);
    }

    public String getToken() {
        return token;
    }

    public long getRequestTick() {
        return requestTick;
    }

    public boolean isAimed() {
        return aimed;
    }

    public double getAimX() {
        return aimX;
    }

    public double getAimY() {
        return aimY;
    }

    public double getAimZ() {
        return aimZ;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        words = readWords(buf);
        if (words == null) {
            options = null;
            return;
        }
        token = ByteBufUtils.readUTF8String(buf);
        requestTick = buf.readLong();
        aimed = buf.readBoolean();
        if (aimed) {
            aimX = buf.readDouble();
            aimY = buf.readDouble();
            aimZ = buf.readDouble();
        }
        options = words == null ? null : InputOptions.parse(words);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeWords(buf, words);
        ByteBufUtils.writeUTF8String(buf, token);
        buf.writeLong(requestTick);
        buf.writeBoolean(aimed);
        if (aimed) {
            buf.writeDouble(aimX);
            buf.writeDouble(aimY);
            buf.writeDouble(aimZ);
        }
    }

    /** The word count, then each word. Shared with the screen message, which has the same shape. */
    static void writeWords(ByteBuf buf, String[] words) {
        ByteBufUtils.writeVarInt(buf, words.length, 5);
        for (String word : words) {
            ByteBufUtils.writeUTF8String(buf, word);
        }
    }

    /**
     * The words; null when there are more than any step has. The bytes after such a list are not
     * read, since the message is dropped anyway.
     */
    static String[] readWords(ByteBuf buf) {
        int count = ByteBufUtils.readVarInt(buf, 5);
        if (count < 0 || count > MAX_WORDS) {
            buf.skipBytes(buf.readableBytes());
            return null;
        }
        String[] words = new String[count];
        for (int i = 0; i < count; i++) {
            words[i] = ByteBufUtils.readUTF8String(buf);
        }
        return words;
    }

    /** Network thread: hands the step to the client's receiver; it runs on the client thread. */
    public static final class Handler implements IMessageHandler<InputRequest, IMessage> {
        @Override
        public IMessage onMessage(InputRequest message, MessageContext ctx) {
            ToolkitChannel.InputReceiver receiver = ToolkitChannel.inputReceiver();
            // Also a step whose words do not parse: the client answers it badArguments, so the
            // server is not left waiting. Only a message without a usable token is dropped.
            if (receiver != null && message.isAnswerable()) {
                receiver.receive(message);
            }
            return null;
        }
    }
}
