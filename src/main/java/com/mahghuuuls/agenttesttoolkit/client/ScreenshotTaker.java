package com.mahghuuuls.agenttesttoolkit.client;

import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.net.ScreenshotRequest;
import com.mahghuuuls.agenttesttoolkit.net.ToolkitChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ScreenShotHelper;
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
        if (event.phase != TickEvent.Phase.END || pending.isEmpty()) {
            return;
        }
        // One capture per frame: each is a synchronous file write.
        Iterator<Pending> it = pending.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (p.remaining <= 0) {
                it.remove();
                capture(p.request);
                return;
            }
        }
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
        try {
            File file = new File(new File(mc.gameDir, "screenshots"), name);
            long before = file.isFile() ? file.lastModified() : -1L;
            net.minecraft.util.text.ITextComponent said = ScreenShotHelper.saveScreenshot(
                    mc.gameDir, name, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
            if (said != null && mc.ingameGUI != null) {
                // The player is told, as F2 tells them.
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
