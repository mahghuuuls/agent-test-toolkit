package com.mahghuuuls.agenttesttoolkit.net;

import com.mahghuuuls.agenttesttoolkit.inbox.PlayerActionOptions;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server to client: take one small player action, such as a click.
 *
 * <p>A message of its own rather than another lifecycle step, so a client older than 1.7.0,
 * which has no handler for it, is simply never sent one: the server checks the client's hello
 * first. The client checks the action again with the same rules the server used.
 */
public final class PlayerActionRequest implements IMessage {

    private String step;
    private int slot;
    /** {@code use} or {@code attack} for a hold, {@code all} for a whole-stack drop; empty otherwise. */
    private String argument;
    private int ticks;
    private int delay;
    private String screenshot;
    private int screenshotDelay;
    /** Tells this request's answer from another of the same step asked in the same tick. */
    private String token;
    private long requestTick;

    /** For Forge, which builds the message before reading it. */
    public PlayerActionRequest() {
    }

    public PlayerActionRequest(PlayerActionOptions action, String token, long requestTick) {
        this(action.getStep(), action.getSlot(), action.getArgument(), action.getTicks(), token, requestTick);
        this.delay = action.getDelay();
        this.screenshot = action.getScreenshot();
        this.screenshotDelay = action.getScreenshotDelay();
    }

    /** For tests: any values, valid or not. */
    PlayerActionRequest(String step, int slot, String argument, int ticks, String token, long requestTick) {
        this.step = step;
        this.slot = slot;
        this.argument = argument;
        this.ticks = ticks;
        this.token = token;
        this.requestTick = requestTick;
        this.screenshot = "";
    }

    /** For tests: the options. */
    PlayerActionRequest withOptions(int delay, String screenshot, int screenshotDelay) {
        this.delay = delay;
        this.screenshot = screenshot;
        this.screenshotDelay = screenshotDelay;
        return this;
    }

    public boolean isValid() {
        return PlayerActionOptions.isValid(step, slot, argument, ticks) && ClientActionRequest.isValidToken(token)
                && PlayerActionOptions.isValidOptions(delay, screenshot, screenshotDelay)
                && (!isTooltip() || PlayerActionOptions.screenshotFitsTooltip(ticks, screenshot, screenshotDelay));
    }

    public int getDelay() {
        return delay;
    }

    /** The label of the screenshot taken after the action; empty for none. */
    public String getScreenshot() {
        return screenshot;
    }

    public int getScreenshotDelay() {
        return screenshotDelay;
    }

    public String getStep() {
        return step;
    }

    /** Which step, so the client need not know where the step names are kept. */
    public boolean isSelect() {
        return PlayerActionOptions.SELECT.equals(step);
    }

    public boolean isUse() {
        return PlayerActionOptions.USE.equals(step);
    }

    public boolean isAttack() {
        return PlayerActionOptions.ATTACK.equals(step);
    }

    public boolean isHold() {
        return PlayerActionOptions.HOLD.equals(step);
    }

    /** For a hold: the use input is held; otherwise the attack input. */
    public boolean holdsUse() {
        return PlayerActionOptions.USE.equals(argument);
    }

    public boolean isDrop() {
        return PlayerActionOptions.DROP.equals(step);
    }

    /** For a drop: the whole held stack. */
    public boolean dropsAll() {
        return PlayerActionOptions.ALL.equals(argument);
    }

    public boolean isInventory() {
        return PlayerActionOptions.INVENTORY.equals(step);
    }

    public boolean isTooltip() {
        return PlayerActionOptions.TOOLTIP.equals(step);
    }

    /** For a tooltip: the advanced (F3+H) form. */
    public boolean isAdvanced() {
        return isTooltip() && PlayerActionOptions.advancedOf(argument);
    }

    /** For a tooltip: the modifier keys read as held, {@code shift,ctrl,alt} in that order; empty for none. */
    public String modifierKeys() {
        return isTooltip() ? PlayerActionOptions.modifierKeysOf(argument) : "";
    }

    public int getSlot() {
        return slot;
    }

    public String getArgument() {
        return argument;
    }

    public int getTicks() {
        return ticks;
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
        slot = buf.readInt();
        argument = ByteBufUtils.readUTF8String(buf);
        ticks = buf.readInt();
        delay = buf.readInt();
        screenshot = ByteBufUtils.readUTF8String(buf);
        screenshotDelay = buf.readInt();
        token = ByteBufUtils.readUTF8String(buf);
        requestTick = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, step);
        buf.writeInt(slot);
        ByteBufUtils.writeUTF8String(buf, argument);
        buf.writeInt(ticks);
        buf.writeInt(delay);
        ByteBufUtils.writeUTF8String(buf, screenshot);
        buf.writeInt(screenshotDelay);
        ByteBufUtils.writeUTF8String(buf, token);
        buf.writeLong(requestTick);
    }

    /** Network thread: hands the action to the client's receiver; it runs on the client thread. */
    public static final class Handler implements IMessageHandler<PlayerActionRequest, IMessage> {
        @Override
        public IMessage onMessage(PlayerActionRequest message, MessageContext ctx) {
            ToolkitChannel.PlayerActionReceiver receiver = ToolkitChannel.playerActionReceiver();
            if (receiver != null && message.isValid()) {
                receiver.receive(message);
            }
            return null;
        }
    }
}
