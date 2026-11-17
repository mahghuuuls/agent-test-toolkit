package com.mahghuuuls.agenttesttoolkit.net;

import com.mahghuuuls.agenttesttoolkit.inbox.ScreenOptions;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server to client: one step on the open screen, such as a slot click or a description of the
 * screen.
 *
 * <p>Like {@link InputRequest}, a message of its own that only a client at 1.9.0 or later is
 * sent, carrying the parser's own words and parsed again on arrival.
 */
public final class ScreenRequest implements IMessage {

    private String[] words;
    private ScreenOptions options;
    private String token;
    private long requestTick;

    /** For Forge, which builds the message before reading it. */
    public ScreenRequest() {
    }

    public ScreenRequest(ScreenOptions options, String token, long requestTick) {
        this(options.toWords().toArray(new String[0]), token, requestTick);
    }

    /** For tests: any words, valid or not. */
    ScreenRequest(String[] words, String token, long requestTick) {
        this.words = words;
        this.options = ScreenOptions.parse(words);
        this.token = token;
        this.requestTick = requestTick;
    }

    public boolean isValid() {
        return options != null && ClientActionRequest.isValidToken(token);
    }

    /** The parsed step; null when the words were not a step. */
    public ScreenOptions getOptions() {
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

    @Override
    public void fromBytes(ByteBuf buf) {
        words = InputRequest.readWords(buf);
        if (words == null) {
            options = null;
            return;
        }
        token = ByteBufUtils.readUTF8String(buf);
        requestTick = buf.readLong();
        options = ScreenOptions.parse(words);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        InputRequest.writeWords(buf, words);
        ByteBufUtils.writeUTF8String(buf, token);
        buf.writeLong(requestTick);
    }

    /** Network thread: hands the step to the client's receiver; it runs on the client thread. */
    public static final class Handler implements IMessageHandler<ScreenRequest, IMessage> {
        @Override
        public IMessage onMessage(ScreenRequest message, MessageContext ctx) {
            ToolkitChannel.ScreenReceiver receiver = ToolkitChannel.screenReceiver();
            // Also a step whose words do not parse: the client answers it badArguments, so the
            // server is not left waiting. Only a message without a usable token is dropped.
            if (receiver != null && message.isAnswerable()) {
                receiver.receive(message);
            }
            return null;
        }
    }
}
