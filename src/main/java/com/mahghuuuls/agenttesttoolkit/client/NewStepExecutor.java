package com.mahghuuuls.agenttesttoolkit.client;

import com.mahghuuuls.agenttesttoolkit.config.ToolkitConfigLoader;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.net.ClientActionReport;
import com.mahghuuuls.agenttesttoolkit.net.InputRequest;
import com.mahghuuuls.agenttesttoolkit.net.ScreenRequest;
import com.mahghuuuls.agenttesttoolkit.net.ToolkitChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Answers the input and screen steps on the client thread.
 *
 * <p>Every step is received, recorded and answered, so a server asking for one never waits for
 * an answer that does not come: words that do not parse are refused {@code badArguments}, and a
 * step this build does not carry out yet {@code notImplemented}. Client-only: registered through
 * {@code ClientProxy}.
 */
public final class NewStepExecutor implements ToolkitChannel.InputReceiver, ToolkitChannel.ScreenReceiver {

    /** One received step: its name, token and request tick, all a refusal needs, and whether it parsed. */
    private static final class Step {
        final String name;
        final String token;
        final long requestTick;
        final boolean parsed;

        Step(String name, String token, long requestTick, boolean parsed) {
            this.name = name;
            this.token = token;
            this.requestTick = requestTick;
            this.parsed = parsed;
        }
    }

    private final ConcurrentLinkedQueue<Step> incoming = new ConcurrentLinkedQueue<Step>();

    @Override
    public void receive(InputRequest request) {
        incoming.add(new Step(request.getStep(), request.getToken(), request.getRequestTick(), request.isValid()));
    }

    @Override
    public void receive(ScreenRequest request) {
        incoming.add(new Step(request.getStep(), request.getToken(), request.getRequestTick(), request.isValid()));
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Step step;
        while ((step = incoming.poll()) != null) {
            if (!step.parsed) {
                // Words this build's parser does not accept, as from a server newer than this client.
                finish(step, "refused", "badArguments");
            } else if (!ToolkitConfigLoader.get().isClientAllowActions()) {
                // The server checks the hello too; this holds if the setting changed since.
                finish(step, "refused", "notAllowed");
            } else {
                finish(step, "refused", "notImplemented");
            }
        }
    }

    /** One record on the client, one report to the server while it can still hear one. */
    private static void finish(Step step, String result, String reason) {
        Minecraft mc = Minecraft.getMinecraft();
        String screen = screenName(mc.currentScreen);
        ToolkitLog.write(LogRecord.of(EventType.CLIENT_ACTION)
                .add("side", "CLIENT")
                .add("step", step.name)
                .add("result", result)
                .add("reason", reason)
                .add("screen", screen)
                .add("requestTick", step.requestTick));
        if (mc.getConnection() != null) {
            ToolkitChannel.sendToServer(new ClientActionReport(step.name, result, reason, screen, null,
                    step.token, step.requestTick));
        }
    }

    private static String screenName(GuiScreen screen) {
        if (screen == null) {
            return "none";
        }
        String name = screen.getClass().getName();
        return name.length() <= 128 ? name : name.substring(name.length() - 128);
    }
}
