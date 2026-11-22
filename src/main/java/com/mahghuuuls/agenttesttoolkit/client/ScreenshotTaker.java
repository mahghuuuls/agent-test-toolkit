package com.mahghuuuls.agenttesttoolkit.client;

import com.mahghuuuls.agenttesttoolkit.inbox.HideSet;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.net.ScreenshotReport;
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
 * Takes the screenshots the server asks for, the way F2 does, and the ones the client
 * request channel asks for at a menu.
 *
 * <p>Requests arrive on the network thread and wait in a queue; the delay is counted in client
 * ticks; the capture happens at the end of a render tick, on the main thread with the frame
 * just drawn, so an open GUI such as the inventory is in the picture. Client-only: registered
 * through {@code ClientProxy} and never referenced from shared code (rule 12).
 *
 * <p>A picture the server asked for with {@code devtool screenshot} is reported back after its
 * record, saved or failed, to a server that said hello with 1.10.0 or later, so a bundle can wait
 * for it. An older server has no handler for the report and would drop the connection on it.
 * Pictures taken after a client step and at a menu are not reported.
 */
public final class ScreenshotTaker implements ToolkitChannel.ClientReceiver {

    /**
     * At most this many waiting at once. Any server with the toolkit can send requests, and each
     * capture writes a file; more are refused with a record rather than queued without end.
     */
    static final int MAX_WAITING = 8;

    /** What became of a capture asked on this client; told to whoever asked, on the client thread. */
    public interface Taken {
        /**
         * @param saved  the file was written by this capture
         * @param file   the file, under the game folder ({@code screenshots/...}); null when none was tried
         * @param reason why it was not saved; null when it was
         */
        void taken(boolean saved, String file, String reason);
    }

    /** Tells which server version this connection said hello with, for the picture reports. */
    private final ClientActionExecutor server;
    private final ConcurrentLinkedQueue<Pending> incoming = new ConcurrentLinkedQueue<Pending>();
    /** Client thread only. */
    private final List<Pending> pending = new ArrayList<Pending>();
    /** The frame a capture with {@code hide} falls in; client thread only. */
    private final FrameHide frame = new FrameHide();
    private final ToastHide toasts = new ToastHide();
    /** The capture that frame was armed for, taken at the frame's end. */
    private Pending armedFor;
    /** Whether the toasts asked out of that frame could be reached; a record says when they could not. */
    private boolean toastsHidden;

    private static final class Pending {
        final ScreenshotRequest request;
        /** Asked on this client, at a menu: not tied to a world, so a leave does not end it. */
        final Taken local;
        /** Asked by the server's screenshot command, so the server is told how it went. */
        final boolean reported;
        int remaining;

        Pending(ScreenshotRequest request, Taken local, boolean reported) {
            this.request = request;
            this.local = local;
            this.reported = reported;
            this.remaining = request.getDelayTicks();
        }
    }

    public ScreenshotTaker(ClientActionExecutor server) {
        this.server = server;
    }

    /** A picture the server's screenshot command asked for; its outcome is reported back. */
    @Override
    public void receive(ScreenshotRequest request) {
        incoming.add(new Pending(request, null, true));
    }

    /** A picture a client step asked for after itself; its record is written, and nothing reported. */
    public void takeAfterStep(ScreenshotRequest request) {
        incoming.add(new Pending(request, null, false));
    }

    /**
     * A capture asked on this client itself, where no world need be open, such as at the title
     * screen. Told to {@code taken} once it is saved or failed.
     */
    public void takeLocal(ScreenshotRequest request, Taken taken) {
        incoming.add(new Pending(request, taken, false));
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (Minecraft.getMinecraft().world == null) {
            // Left the world: a server's request for it must not fire at the menu or in the
            // next world. A capture asked here at a menu belongs to no world.
            Iterator<Pending> it = pending.iterator();
            while (it.hasNext()) {
                Pending p = it.next();
                if (p.local == null) {
                    it.remove();
                    failed(p, "disconnected");
                }
            }
        }
        // Counted before the new arrivals join, so a delay of n waits n whole ticks.
        for (Pending p : pending) {
            if (p.remaining > 0) {
                p.remaining--;
            }
        }
        Pending arrived;
        while ((arrived = incoming.poll()) != null) {
            if (pending.size() >= MAX_WAITING) {
                failed(arrived, "queueFull");
            } else {
                pending.add(arrived);
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
        boolean hidOk = toastsHidden;
        try {
            // Only a capture still waiting is taken: one the world's end already failed is not
            // taken late, even if its frame was armed.
            if (due != null && pending.remove(due)) {
                capture(due, hidOk);
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
        if (frame.hidesToasts()) {
            toastsHidden = toasts.hide(mc);
        }
        armedFor = due;
    }

    /** At the frame's end, after the capture: the player's own F1 state and the toasts come back. */
    private void disarmFrame() {
        if (frame.isArmed()) {
            Minecraft mc = Minecraft.getMinecraft();
            try {
                toasts.restore();
            } finally {
                mc.gameSettings.hideGUI = frame.disarm(mc.gameSettings.hideGUI);
            }
        }
        toastsHidden = false;
        armedFor = null;
    }

    private void failed(Pending p, String reason) {
        ToolkitLog.write(LogRecord.of(EventType.SCREENSHOT)
                .add("side", "CLIENT")
                .add("label", p.request.getLabel())
                .add("requestTick", p.request.getRequestTick())
                .add("action", "failed")
                .add("reason", reason));
        if (p.local != null) {
            p.local.taken(false, null, reason);
        }
        report(p, false, null, reason);
    }

    /**
     * Tells the server how a picture it asked for went, after the client's record: only for its
     * screenshot command's requests, and only to a server of this connection at 1.10.0 or later.
     * The frame's tick is the client world's, as the key steps' ticks are.
     */
    private void report(Pending p, boolean saved, String file, String reason) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!p.reported || mc.getConnection() == null
                || !ToolkitChannel.readsNoticesAndSendsReports(server.connectedServerVersion())) {
            return;
        }
        long frameTick = mc.world == null ? -1L : mc.world.getTotalWorldTime();
        ToolkitChannel.sendToServer(new ScreenshotReport(p.request.getLabel(), p.request.getRequestTick(), file,
                saved, frameTick, reason));
    }

    private void capture(Pending p, boolean toastsLeftOut) {
        ScreenshotRequest request = p.request;
        Minecraft mc = Minecraft.getMinecraft();
        String name = request.fileName();
        LogRecord record = LogRecord.of(EventType.SCREENSHOT)
                .add("side", "CLIENT")
                .add("label", request.getLabel())
                .add("requestTick", request.getRequestTick());
        boolean hidden = !ScreenshotRequest.HIDE_NONE.equals(request.getHide());
        if (hidden) {
            record.add("hide", request.getHide());
            if (HideSet.has(request.getHide(), HideSet.TOASTS) && !toastsLeftOut) {
                // Said rather than claimed: the toasts could not be reached, so they may be in it.
                record.add("hideFailed", HideSet.TOASTS);
            }
        }
        if (mc.currentScreen != null && (hidden || p.local != null)) {
            // An open screen is drawn whatever the frame leaves out; at a menu it is the picture.
            record.add("screen", mc.currentScreen.getClass().getName());
        }
        boolean saved = false;
        String file = null;
        String reason = null;
        try {
            File target = new File(new File(mc.gameDir, "screenshots"), name);
            long before = target.isFile() ? target.lastModified() : -1L;
            net.minecraft.util.text.ITextComponent said = ScreenShotHelper.saveScreenshot(
                    mc.gameDir, name, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
            if (said != null && mc.ingameGUI != null && !hidden && p.local == null) {
                // The player is told, as F2 tells them; not for a picture meant to be clean,
                // since the line would land in the next one, nor at a menu, where no one reads it.
                mc.ingameGUI.getChatGUI().printChatMessage(said);
            }
            file = "screenshots/" + name;
            // Checked rather than assumed: another mod's ScreenshotEvent can cancel or redirect
            // it, and a file left by an earlier run must not read as this capture.
            if (target.isFile() && target.lastModified() != before) {
                saved = true;
                record.add("action", "saved").add("file", file);
            } else {
                reason = "noFile";
                record.add("action", "failed").add("reason", reason).add("file", file);
            }
        } catch (RuntimeException e) {
            reason = e.getClass().getSimpleName();
            record.add("action", "failed").add("reason", reason);
        }
        ToolkitLog.write(record);
        if (p.local != null) {
            p.local.taken(saved, file, reason);
        }
        report(p, saved, file, reason);
    }
}
