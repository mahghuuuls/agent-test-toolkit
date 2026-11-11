package com.mahghuuuls.agenttesttoolkit.client;

import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.net.ScreenshotRequest;
import com.mahghuuuls.agenttesttoolkit.net.ToolkitChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ScreenShotHelper;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Takes the screenshots the server asks for, the way F2 does (ARC-017).
 *
 * <p>Requests arrive on the network thread and wait in a queue; the delay is counted in client
 * ticks; the capture happens at the end of a render tick, on the main thread with the frame
 * just drawn, so an open GUI such as the inventory is in the picture. Client-only: registered
 * through {@code ClientProxy} and never referenced from shared code (rule 12).
 */
public final class ScreenshotTaker implements ToolkitChannel.ClientReceiver {

    /**
     * At most this many waiting at once. Any server with the toolkit can send requests, and each
     * capture writes a file; more are refused with a record rather than queued without end.
     */
    static final int MAX_WAITING = 8;

    private final ConcurrentLinkedQueue<ScreenshotRequest> incoming = new ConcurrentLinkedQueue<ScreenshotRequest>();
    /** Client thread only. */
    private final List<Pending> pending = new ArrayList<Pending>();
    /** The frame a capture with {@code hide} falls in; client thread only. */
    private final FrameHide frame = new FrameHide();
    /** The capture that frame was armed for, taken at the frame's end. */
    private Pending armedFor;

    private static final class Pending {
        final ScreenshotRequest request;
        int remaining;

        Pending(ScreenshotRequest request) {
            this.request = request;
            this.remaining = request.getDelayTicks();
        }
    }

    @Override
    public void receive(ScreenshotRequest request) {
        incoming.add(request);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (Minecraft.getMinecraft().world == null) {
            // Left the world: a request for it must not fire at the menu or in the next world.
            for (Pending p : pending) {
                failed(p.request, "disconnected");
            }
            pending.clear();
        }
        // Counted before the new arrivals join, so a delay of n waits n whole ticks.
        for (Pending p : pending) {
            if (p.remaining > 0) {
                p.remaining--;
            }
        }
        ScreenshotRequest request;
        while ((request = incoming.poll()) != null) {
            if (pending.size() >= MAX_WAITING) {
                failed(request, "queueFull");
            } else {
                pending.add(new Pending(request));
            }
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            armFrame();
            return;
        }
        if (pending.isEmpty() && !frame.isArmed()) {
            return;
        }
        // One capture per frame: each is a synchronous file write. The capture armed at this
        // frame's start is the one taken, so what the frame left out is what the picture shows.
        Pending due = armedFor != null ? armedFor : firstDue();
        try {
            // Only a capture still waiting is taken: one the world's end already failed is not
            // taken late, even if its frame was armed.
            if (due != null && pending.remove(due)) {
                capture(due.request);
            }
        } finally {
            disarmFrame();
        }
    }

    /**
     * Leaves chat out of the frame being drawn while it is armed: for {@code chat}, and for
     * {@code hud}, which includes chat, also when an open screen keeps the HUD drawn. Forge's
     * chat overlay returns before it ends its profiler section when cancelled, which only shows
     * in the F3 profiler for that one frame.
     */
    @SubscribeEvent
    public void onChatOverlay(RenderGameOverlayEvent.Chat event) {
        if (frame.cancelsChat()) {
            event.setCanceled(true);
        }
    }

    /** The first waiting capture whose delay has run out; null when none is due. */
    private Pending firstDue() {
        Iterator<Pending> it = pending.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (p.remaining <= 0) {
                return p;
            }
        }
        return null;
    }

    /** At a frame's start: when the capture due in it asks {@code hide}, the frame is drawn without that. */
    private void armFrame() {
        // A frame whose end never came (the game recovering from an error) is put back first.
        disarmFrame();
        Pending due = firstDue();
        if (due == null || ScreenshotRequest.HIDE_NONE.equals(due.request.getHide())) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        mc.gameSettings.hideGUI = frame.arm(due.request.getHide(), mc.gameSettings.hideGUI);
        armedFor = due;
    }

    /** At the frame's end, after the capture: the player's own F1 state comes back. */
    private void disarmFrame() {
        if (frame.isArmed()) {
            Minecraft mc = Minecraft.getMinecraft();
            mc.gameSettings.hideGUI = frame.disarm(mc.gameSettings.hideGUI);
        }
        armedFor = null;
    }

    private static void failed(ScreenshotRequest request, String reason) {
        ToolkitLog.write(LogRecord.of(EventType.SCREENSHOT)
                .add("side", "CLIENT")
                .add("label", request.getLabel())
                .add("requestTick", request.getRequestTick())
                .add("action", "failed")
                .add("reason", reason));
    }

    private void capture(ScreenshotRequest request) {
        Minecraft mc = Minecraft.getMinecraft();
        String name = request.fileName();
        LogRecord record = LogRecord.of(EventType.SCREENSHOT)
                .add("side", "CLIENT")
                .add("label", request.getLabel())
                .add("requestTick", request.getRequestTick());
        boolean hidden = !ScreenshotRequest.HIDE_NONE.equals(request.getHide());
        if (hidden) {
            record.add("hide", request.getHide());
            if (mc.currentScreen != null) {
                // An open screen is drawn whatever the frame leaves out.
                record.add("screen", mc.currentScreen.getClass().getName());
            }
        }
        try {
            File file = new File(new File(mc.gameDir, "screenshots"), name);
            long before = file.isFile() ? file.lastModified() : -1L;
            net.minecraft.util.text.ITextComponent said = ScreenShotHelper.saveScreenshot(
                    mc.gameDir, name, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
            if (said != null && mc.ingameGUI != null && !hidden) {
                // The player is told, as F2 tells them; not for a picture meant to be clean,
                // since the line would land in the next one.
                mc.ingameGUI.getChatGUI().printChatMessage(said);
            }
            // Checked rather than assumed: another mod's ScreenshotEvent can cancel or redirect
            // it, and a file left by an earlier run must not read as this capture.
            if (file.isFile() && file.lastModified() != before) {
                record.add("action", "saved").add("file", "screenshots/" + name);
            } else {
                record.add("action", "failed").add("reason", "noFile").add("file", "screenshots/" + name);
            }
        } catch (RuntimeException e) {
            record.add("action", "failed").add("reason", e.getClass().getSimpleName());
        }
        ToolkitLog.write(record);
    }
}
