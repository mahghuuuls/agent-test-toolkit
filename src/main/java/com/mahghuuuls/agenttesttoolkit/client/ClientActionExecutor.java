package com.mahghuuuls.agenttesttoolkit.client;

import com.mahghuuuls.agenttesttoolkit.config.ToolkitConfigLoader;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.net.ClientActionReport;
import com.mahghuuuls.agenttesttoolkit.net.ClientActionRequest;
import com.mahghuuuls.agenttesttoolkit.net.ClientHello;
import com.mahghuuuls.agenttesttoolkit.net.ToolkitChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiWorldSelection;
import net.minecraft.client.gui.GuiScreenWorking;
import net.minecraft.client.gui.GuiSleepMP;
import net.minecraft.client.gui.GuiWinGame;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.storage.WorldSummary;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Takes the lifecycle steps a server asks for, on the client's main thread (ARC-022).
 *
 * <p>Requests arrive on the network thread and wait in a queue drained at the end of a client
 * tick, the shape {@link ScreenshotTaker} uses. Every step checks its preconditions first and
 * refuses, with a record and a report to the server, when it is not safe or would not do what
 * was asked. Client-only: registered through {@code ClientProxy} and never referenced from
 * shared code (rule 12).
 *
 * <p>Also answers the server's hello at every login: whether this client allows actions. The
 * server speaks first, and only a server new enough to read the answer does (ARC-022).
 */
public final class ClientActionExecutor implements ToolkitChannel.ActionReceiver {

    /** The death screen's buttons enable after this many ticks; pressing earlier is not faithful. */
    static final int DEATH_SCREEN_READY_TICKS = 20;
    /** A respawn waits at most this long for a death screen that is still opening. */
    static final int RESPAWN_WAIT_TICKS = 100;
    /**
     * A reconnect leaves this many ticks after its request, so the run that asked for it can end
     * and be answered first; in single player the stop would otherwise discard it.
     */
    static final int LEAVE_DELAY_TICKS = 5;
    /**
     * And at least this long: after a hitch the client runs several ticks in one frame, and the
     * server must have handled the report saying the client leaves before it sees the logout.
     */
    static final long LEAVE_DELAY_MS = 1000L;
    /**
     * Before it leaves, the client stops reading the connection and lets every packet already
     * received be handled while the world still exists. Handled after the leave, each would fail
     * against the unloaded world and fill the log with errors. Counting ticks is not enough: a
     * slow frame runs several ticks after one pass over the handler queue. So the client waits
     * this long for a read the channel had already asked for, then queues a marker from the
     * channel's own thread, which lands behind every handler queued before it, and leaves once
     * the marker has run.
     */
    static final long READ_SETTLE_MS = 100L;
    /** The marker is waited for at most this long; a closed channel never runs it. */
    static final long DRAIN_TIMEOUT_MS = 2000L;
    /** At least this many ticks at the menu before rejoining, so the server has let the old connection go. */
    static final int REJOIN_SETTLE_TICKS = 20;
    /** How long past the away time a rejoin may take before it is given up, in milliseconds. */
    static final long REJOIN_DEADLINE_MS = 60000L;
    /**
     * How much longer a single-player world that is still loading may take: a large modpack
     * world can spend most of a minute starting its integrated server.
     */
    static final long WORLD_LOAD_EXTRA_MS = 240000L;
    /** A single-player rejoin with no integrated server this many ticks after it began was aborted. */
    static final int LOAD_ABORT_TICKS = 40;

    /**
     * Set on the network thread when the server says hello; answered on the client thread once
     * the player and its connection exist, which can be a frame after the hello arrived. Cleared
     * at a disconnect, so a hello never carries over to another server.
     */
    private volatile boolean serverHello;

    private final ConcurrentLinkedQueue<ClientActionRequest> incoming = new ConcurrentLinkedQueue<ClientActionRequest>();

    /** Client thread only. A close whose result is read one tick later. */
    private ClientActionRequest afterClose;
    private String closedScreen;
    /** Client thread only. A respawn waiting for the death screen's buttons. */
    private ClientActionRequest respawn;
    private int respawnWaited;
    private int deathScreenTicks;
    private GuiScreen countedScreen;

    /** Client thread only. The one reconnect in progress, which outlives the leave. */
    private Reconnect reconnect;

    private enum Stage { LEAVING, AWAY, JOINING }

    private static final class Reconnect {
        final ClientActionRequest request;
        /** Single player: the save to reopen. */
        final String folder;
        final String worldName;
        /** Dedicated: the same object, so its resource-pack choice comes back with it. */
        final ServerData server;
        Stage stage = Stage.LEAVING;
        int ticks;
        final long askedAtMillis = System.currentTimeMillis();
        long leftAtMillis;
        /** JOINING: the world joined is the one this left. The server's hello finishes it. */
        boolean joined;
        /** JOINING, single player: ticks at a menu with no world starting. */
        int idleTicks;
        /** LEAVING: the connection is no longer read, since then. */
        boolean readStopped;
        long readStoppedAtMillis;
        /** LEAVING: the marker is queued behind the handlers, since then; it has run. */
        boolean markerQueued;
        long markerQueuedAtMillis;
        boolean drained;

        Reconnect(ClientActionRequest request, String folder, String worldName, ServerData server) {
            this.request = request;
            this.folder = folder;
            this.worldName = worldName;
            this.server = server;
        }
    }

    @Override
    public void receive(ClientActionRequest request) {
        incoming.add(request);
    }

    @Override
    public void serverHello() {
        serverHello = true;
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        serverHello = false;
    }

    /**
     * A join during a rejoin: into the world this left, or somewhere else. Judged here, since a
     * server without the toolkit never says hello; the hello then finishes a right one.
     */
    @SubscribeEvent
    public void onJoinWorld(EntityJoinWorldEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null || event.getEntity() != mc.player) {
            return;
        }
        if (reconnect != null && reconnect.stage == Stage.JOINING && !reconnect.joined) {
            if (isWhereItLeft(mc, reconnect)) {
                reconnect.joined = true;
            } else {
                giveUp(reconnect, "joinedElsewhere");
            }
        }
    }

    /** Same save folder in single player; the same server entry, the object rejoined, otherwise. */
    private static boolean isWhereItLeft(Minecraft mc, Reconnect r) {
        if (r.folder != null) {
            return mc.getIntegratedServer() != null && r.folder.equals(mc.getIntegratedServer().getFolderName());
        }
        return mc.getIntegratedServer() == null && mc.getCurrentServerData() == r.server;
    }

    /** Tells the server whether this client allows actions (REQ-203); back from a reconnect, says so. */
    private void answerHello(Minecraft mc) {
        ToolkitChannel.sendToServer(ClientHello.ofThisBuild(ToolkitConfigLoader.get().isClientAllowActions()));
        if (reconnect != null && reconnect.stage == Stage.JOINING) {
            if (!reconnect.joined && !isWhereItLeft(mc, reconnect)) {
                giveUp(reconnect, "joinedElsewhere");
                return;
            }
            // Back: the token tells the server this is the client it sent away (ARC-023).
            Reconnect done = reconnect;
            reconnect = null;
            long away = System.currentTimeMillis() - done.leftAtMillis;
            ToolkitLog.write(LogRecord.of(EventType.CLIENT_ACTION)
                    .add("side", "CLIENT")
                    .add("step", ClientActionRequest.RECONNECT)
                    .add("result", "done")
                    .add("awayMs", away)
                    .add("requestTick", done.request.getRequestTick()));
            ToolkitChannel.sendToServer(new ClientActionReport(ClientActionRequest.RECONNECT, "done", null,
                    null, null, done.request.getToken(), done.request.getRequestTick()));
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        // Counted per death screen: a new one, after another death, starts from one.
        if (mc.currentScreen instanceof GuiGameOver && mc.currentScreen == countedScreen) {
            deathScreenTicks++;
        } else {
            deathScreenTicks = mc.currentScreen instanceof GuiGameOver ? 1 : 0;
            countedScreen = mc.currentScreen;
        }

        if (serverHello && mc.player != null && mc.getConnection() != null) {
            serverHello = false;
            answerHello(mc);
        }
        if (afterClose != null) {
            ClientActionRequest done = afterClose;
            afterClose = null;
            // Read a tick later: another mod can cancel or replace the close.
            String showing = screenName(mc.currentScreen);
            if (showing.equals(closedScreen)) {
                // Another mod cancelled the close or opened the same screen again.
                finish(done, "failed", "stillOpen", closedScreen, showing);
            } else {
                finish(done, "done", null, closedScreen, showing);
            }
        }
        if (respawn != null) {
            advanceRespawn(mc);
        }
        if (reconnect != null) {
            advanceReconnect(mc);
        }

        ClientActionRequest request;
        while ((request = incoming.poll()) != null) {
            if (!ToolkitConfigLoader.get().isClientAllowActions()) {
                // The server checks the hello too; this holds if the setting changed since.
                finish(request, "refused", "notAllowed", screenName(mc.currentScreen), null);
            } else if (reconnect != null && !ClientActionRequest.RECONNECT.equals(request.getStep())) {
                // The server holds other steps back during a leave; this holds if one slipped by.
                finish(request, "refused", "busy", screenName(mc.currentScreen), null);
            } else if (ClientActionRequest.CLOSE.equals(request.getStep())) {
                close(mc, request);
            } else if (ClientActionRequest.RESPAWN.equals(request.getStep())) {
                startRespawn(mc, request);
            } else {
                startReconnect(mc, request);
            }
        }
    }

    private void close(Minecraft mc, ClientActionRequest request) {
        GuiScreen screen = mc.currentScreen;
        if (afterClose != null) {
            finish(request, "refused", "busy", screenName(screen), null);
            return;
        }
        String refusal = closeRefusal(mc.world != null, screen == null ? null : screen.getClass());
        if (refusal != null) {
            finish(request, "refused", refusal, screenName(screen), null);
            return;
        }
        closedScreen = screenName(screen);
        if (screen instanceof GuiContainer && mc.player != null) {
            // As Escape does in a container: tells the server the container is closed.
            mc.player.closeScreen();
        } else {
            mc.displayGuiScreen(null);
        }
        afterClose = request;
    }

    /**
     * Why closing a screen of this class would be wrong, or null. The death and bed screens
     * reopen themselves; closing the end credits without their own exit would leave the player
     * out of the world; connection and loading screens are not the player's to close. Takes the
     * class, so it is tested without building a screen.
     */
    static String closeRefusal(boolean inWorld, Class<?> screen) {
        if (!inWorld) {
            return "noWorld";
        }
        if (screen == null) {
            return "noScreen";
        }
        if (GuiGameOver.class.isAssignableFrom(screen)) {
            return "deathScreenUseRespawn";
        }
        if (GuiWinGame.class.isAssignableFrom(screen)) {
            return "credits";
        }
        if (GuiSleepMP.class.isAssignableFrom(screen)) {
            return "bedScreen";
        }
        if (GuiConnecting.class.isAssignableFrom(screen) || GuiDownloadTerrain.class.isAssignableFrom(screen)
                || GuiScreenWorking.class.isAssignableFrom(screen)) {
            return "loadingScreen";
        }
        return null;
    }

    private void startRespawn(Minecraft mc, ClientActionRequest request) {
        if (respawn != null) {
            finish(request, "refused", "busy", screenName(mc.currentScreen), null);
            return;
        }
        String refusal = respawnRefusal(mc);
        if (refusal != null && !isWaitable(refusal)) {
            finish(request, "refused", refusal, screenName(mc.currentScreen), null);
            return;
        }
        // A death screen may still be opening when a bundle kills and asks in one breath.
        respawn = request;
        respawnWaited = 0;
        advanceRespawn(mc);
    }

    private void advanceRespawn(Minecraft mc) {
        String refusal = respawnRefusal(mc);
        if (refusal == null && deathScreenTicks >= DEATH_SCREEN_READY_TICKS) {
            ClientActionRequest done = respawn;
            respawn = null;
            String screen = screenName(mc.currentScreen);
            // What the death screen's Respawn button does.
            mc.player.respawnPlayer();
            mc.displayGuiScreen(null);
            finish(done, "done", null, screen, null);
            return;
        }
        boolean waitable = refusal == null || isWaitable(refusal);
        if (!waitable || ++respawnWaited > RESPAWN_WAIT_TICKS) {
            ClientActionRequest given = respawn;
            respawn = null;
            finish(given, "refused", refusal == null ? "timeout" : refusal, screenName(mc.currentScreen), null);
        }
    }

    private static String respawnRefusal(Minecraft mc) {
        return respawnRefusal(mc.world != null && mc.player != null,
                mc.currentScreen instanceof GuiWinGame,
                mc.world != null && mc.world.getWorldInfo().isHardcoreModeEnabled(),
                mc.player != null && mc.player.getHealth() > 0.0F,
                mc.currentScreen instanceof GuiGameOver);
    }

    /**
     * Why a respawn cannot be pressed now, or null when the death screen is open for a dead
     * player. Pure, for tests.
     *
     * <p>The server refuses a living player before sending, since it knows the health at once;
     * the client hears of the death a tick later, so here a positive health, like a death screen
     * not open yet, is something to wait for ({@link #isWaitable}).
     */
    static String respawnRefusal(boolean inWorld, boolean credits, boolean hardcore, boolean healthAboveZero,
                                 boolean deathScreen) {
        if (!inWorld) {
            return "noWorld";
        }
        if (credits) {
            return "credits";
        }
        if (hardcore) {
            // There the same button means Spectate World.
            return "hardcore";
        }
        if (healthAboveZero) {
            return "notDeadYet";
        }
        if (!deathScreen) {
            return "noDeathScreen";
        }
        return null;
    }

    /** Refusals a respawn waits out, up to its limit: the death has not reached this client yet. */
    static boolean isWaitable(String refusal) {
        return "notDeadYet".equals(refusal) || "noDeathScreen".equals(refusal);
    }

    private void startReconnect(Minecraft mc, ClientActionRequest request) {
        if (reconnect != null || afterClose != null || respawn != null) {
            // A close or respawn still pending would be answered, or lost, during the leave.
            finish(request, "refused", "busy", screenName(mc.currentScreen), null);
            return;
        }
        String refusal = reconnectRefusal(mc.world != null, mc.isConnectedToRealms(),
                mc.isIntegratedServerRunning(),
                mc.getIntegratedServer() != null && mc.getIntegratedServer().getPublic(),
                mc.getCurrentServerData() != null);
        if (refusal != null) {
            finish(request, "refused", refusal, screenName(mc.currentScreen), null);
            return;
        }
        if (mc.isIntegratedServerRunning()) {
            reconnect = new Reconnect(request, mc.getIntegratedServer().getFolderName(),
                    mc.getIntegratedServer().getWorldName(), null);
        } else {
            reconnect = new Reconnect(request, null, null, mc.getCurrentServerData());
        }
        // Said before leaving, while the server can still hear it.
        finish(request, "left", null, screenName(mc.currentScreen), null);
    }

    /**
     * Why a reconnect cannot start, or null. Pure, for tests. A world open to LAN would log every
     * other player out when it stops; Realms is not a server this can rejoin.
     */
    static String reconnectRefusal(boolean inWorld, boolean realms, boolean singlePlayer, boolean openToLan,
                                   boolean hasServerData) {
        if (!inWorld) {
            return "noWorld";
        }
        if (realms) {
            return "realms";
        }
        if (singlePlayer) {
            return openToLan ? "openToLan" : null;
        }
        return hasServerData ? null : "noServerData";
    }

    private void advanceReconnect(Minecraft mc) {
        Reconnect r = reconnect;
        r.ticks++;
        if (r.stage == Stage.LEAVING) {
            if (r.ticks < LEAVE_DELAY_TICKS || System.currentTimeMillis() - r.askedAtMillis < LEAVE_DELAY_MS) {
                return;
            }
            if (mc.world == null) {
                // Left already, by some other route.
                r.stage = Stage.AWAY;
                r.ticks = 0;
                r.leftAtMillis = System.currentTimeMillis();
                return;
            }
            long now = System.currentTimeMillis();
            if (!r.readStopped) {
                if (mc.getConnection() != null) {
                    mc.getConnection().getNetworkManager().disableAutoRead();
                }
                r.readStopped = true;
                r.readStoppedAtMillis = now;
                return;
            }
            if (!r.markerQueued) {
                if (now - r.readStoppedAtMillis < READ_SETTLE_MS) {
                    return;
                }
                queueDrainMarker(mc, r);
                r.markerQueued = true;
                r.markerQueuedAtMillis = now;
                return;
            }
            if (!r.drained && now - r.markerQueuedAtMillis < DRAIN_TIMEOUT_MS) {
                return;
            }
            if (r.folder == null) {
                // The pause menu's Disconnect, for a remote server.
                mc.world.sendQuittingDisconnectingPacket();
                mc.loadWorld(null);
                mc.displayGuiScreen(new GuiMultiplayer(new GuiMainMenu()));
            } else {
                // Single player: no quitting packet first. Sending it lets the integrated server
                // notice its host left and stop its loop before the shutdown the client waits
                // for has run, which can hang (FQ35). loadWorld(null) saves and stops it.
                mc.loadWorld(null);
                mc.displayGuiScreen(new GuiMainMenu());
            }
            r.stage = Stage.AWAY;
            r.ticks = 0;
            r.leftAtMillis = System.currentTimeMillis();
            return;
        }
        long elapsed = System.currentTimeMillis() - r.leftAtMillis;
        long deadline = r.request.getAwaySeconds() * 1000L + REJOIN_DEADLINE_MS;
        if (r.stage == Stage.AWAY) {
            // Any menu: a modpack's own title screen replaces the vanilla one.
            boolean atMenu = mc.world == null && mc.getIntegratedServer() == null && mc.currentScreen != null
                    && isMenuScreen(mc.currentScreen.getClass());
            if (atMenu && r.ticks >= REJOIN_SETTLE_TICKS && elapsed >= r.request.getAwaySeconds() * 1000L) {
                rejoin(mc, r);
            } else if (elapsed > deadline) {
                giveUp(r, mc.world != null ? "joinedElsewhere" : "notAtMenu");
            }
            return;
        }
        // JOINING: the server's hello finishes it; a failed connection or the deadline gives it up.
        boolean worldLoading = r.folder != null && mc.getIntegratedServer() != null;
        if (mc.currentScreen instanceof GuiDisconnected) {
            giveUp(r, "connectFailed");
        } else if (r.folder != null && !worldLoading && mc.world == null && mc.currentScreen != null
                && isMenuScreen(mc.currentScreen.getClass())
                && !(mc.currentScreen instanceof net.minecraftforge.fml.client.GuiOldSaveLoadConfirm)
                && ++r.idleTicks >= LOAD_ABORT_TICKS) {
            // The world never started, or a Forge startup question was answered with cancel.
            giveUp(r, "loadAborted");
        } else if (elapsed > deadline + (worldLoading ? WORLD_LOAD_EXTRA_MS : 0L)) {
            giveUp(r, "timeout");
        }
    }

    /**
     * Queues the drain marker from the connection's own thread, the thread that queues packet
     * handlers, so it runs after all of them. Queued from the client thread it would run at
     * once. The marker only sets a flag: leaving inside it would hold the client's task queue
     * while the integrated server stops.
     */
    private static void queueDrainMarker(final Minecraft mc, final Reconnect r) {
        io.netty.channel.Channel channel = mc.getConnection() == null ? null
                : mc.getConnection().getNetworkManager().channel();
        if (channel == null || !channel.isOpen()) {
            r.drained = true;
            return;
        }
        channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                mc.addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        r.drained = true;
                    }
                });
            }
        });
    }

    /**
     * Whether a screen shown with no world is a menu to rejoin from: anything but the screens of
     * connecting, loading and a lost connection. Takes the class, so it is tested without one.
     */
    static boolean isMenuScreen(Class<?> screen) {
        return !(GuiConnecting.class.isAssignableFrom(screen) || GuiScreenWorking.class.isAssignableFrom(screen)
                || GuiDownloadTerrain.class.isAssignableFrom(screen) || GuiDisconnected.class.isAssignableFrom(screen));
    }

    private void rejoin(Minecraft mc, Reconnect r) {
        r.stage = Stage.JOINING;
        r.ticks = 0;
        if (r.server != null) {
            mc.displayGuiScreen(new GuiConnecting(new GuiMultiplayer(new GuiMainMenu()), mc, r.server));
            return;
        }
        WorldSummary summary = null;
        try {
            if (mc.getSaveLoader().canLoadWorld(r.folder)) {
                for (WorldSummary candidate : mc.getSaveLoader().getSaveList()) {
                    if (r.folder.equals(candidate.getFileName())) {
                        summary = candidate;
                        break;
                    }
                }
            }
        } catch (Exception unreadable) {
            summary = null;
        }
        if (summary == null) {
            giveUp(r, "saveNotFound");
            return;
        }
        // As the world list does; it falls back to level.dat_old and handles Forge's own prompts.
        FMLClientHandler.instance().tryLoadExistingWorld(new GuiWorldSelection(new GuiMainMenu()), summary);
    }

    /** The reconnect ends without a return; the server cannot be told, it is not reachable. */
    private void giveUp(Reconnect r, String reason) {
        reconnect = null;
        ToolkitLog.write(LogRecord.of(EventType.CLIENT_ACTION)
                .add("side", "CLIENT")
                .add("step", ClientActionRequest.RECONNECT)
                .add("result", "failed")
                .add("reason", reason)
                .add("folder", r.folder)
                .add("requestTick", r.request.getRequestTick()));
    }

    /** The open screen's class, bounded so a long modded name still fits a report. */
    private static String screenName(GuiScreen screen) {
        if (screen == null) {
            return "none";
        }
        String name = screen.getClass().getName();
        return name.length() <= 128 ? name : name.substring(name.length() - 128);
    }

    /** One record on the client, one report to the server. */
    private static void finish(ClientActionRequest request, String result, String reason, String screen,
                               String showing) {
        LogRecord record = LogRecord.of(EventType.CLIENT_ACTION)
                .add("side", "CLIENT")
                .add("step", request.getStep())
                .add("result", result)
                .add("reason", reason)
                .add("screen", screen)
                .add("showing", showing)
                .add("requestTick", request.getRequestTick());
        ToolkitLog.write(record);
        ToolkitChannel.sendToServer(new ClientActionReport(
                request.getStep(), result, reason, screen, showing, null, request.getRequestTick()));
    }
}
