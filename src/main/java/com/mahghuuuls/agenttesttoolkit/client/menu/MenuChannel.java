package com.mahghuuuls.agenttesttoolkit.client.menu;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mahghuuuls.agenttesttoolkit.client.ClientActionExecutor;
import com.mahghuuuls.agenttesttoolkit.config.ToolkitConfigLoader;
import com.mahghuuuls.agenttesttoolkit.inbox.InboxFiles;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiWorldSelection;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.storage.WorldSummary;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * The client request channel (D22, REQ-216 to REQ-221, ARC-028).
 *
 * <p>Reads {@code config/devtool/client-inbox/} every 10 client ticks while
 * {@code client.requestChannel} is true and the client is at a menu, and takes one menu step at a
 * time on the client thread. A request while a world is open is answered {@code inWorld}, one
 * while a step or a toolkit reconnect is under way {@code busy}. The requests waiting at the
 * first read of a launch were placed before this launch and are refused. Client-only: registered
 * from {@code ClientProxy} (rule 12).
 */
public final class MenuChannel {

    static final int INTERVAL_TICKS = 10;
    /** How long a create, open or connect may take to put the player in the world. */
    static final long STEP_DEADLINE_MS = 60000L;
    /** And how much longer while an integrated server is starting: a large world loads slowly. */
    static final long WORLD_LOAD_EXTRA_MS = 240000L;
    /** A create or open with no integrated server and no world this many ticks later was aborted. */
    static final int LOAD_ABORT_TICKS = 40;
    static final int DEFAULT_PORT = 25565;
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final ClientActionExecutor actions;
    private int ticks;
    private boolean started;
    /** The key as last seen, so turning it off and on again restarts the channel cleanly. */
    private Boolean lastKey;
    private long startedAtMillis;

    /** The one step under way, or null. */
    private Step step;
    /** A quit was answered: the game is closing. */
    private boolean quitting;

    private static final class Step {
        final MenuRequest request;
        final long startedAtMillis = System.currentTimeMillis();
        String folder;
        ServerData server;
        int idleTicks;

        Step(MenuRequest request) {
            this.request = request;
        }
    }

    public MenuChannel(ClientActionExecutor actions) {
        this.actions = actions;
    }

    /** The folder, beside the command inbox's; null before configuration loads. */
    public static File directory() {
        File config = ToolkitConfigLoader.getConfigDirectory();
        return config == null ? null : new File(config, "client-inbox");
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (step != null) {
            advance(mc);
        }
        if (++ticks < INTERVAL_TICKS) {
            return;
        }
        ticks = 0;
        File directory = directory();
        if (directory == null) {
            return;
        }
        boolean key = ToolkitConfigLoader.get().isClientRequestChannel();
        if (lastKey == null || lastKey.booleanValue() != key) {
            // A status file from an earlier launch, or from before the key changed, would say
            // something untrue. A channel turned on again starts as at launch.
            lastKey = Boolean.valueOf(key);
            started = false;
            new File(directory, "status.json").delete();
        }
        if (!key) {
            return;
        }
        InboxFiles files = new InboxFiles(directory);
        if (!started) {
            start(files);
            return;
        }
        poll(mc, files);
    }

    /** The first read of a launch: everything already waiting was placed before it. */
    private void start(InboxFiles files) {
        started = true;
        startedAtMillis = System.currentTimeMillis();
        files.ensureDirectory();
        for (String id : files.pendingIds()) {
            InboxFiles.Claim claim = files.claim(id);
            if (claim == null) {
                continue;
            }
            MenuRequest request = MenuRequest.parse(claim.getId(), claim.getContent());
            record("claimed", request, null, null);
            refuse(files, request, "placedBeforeStart");
        }
        writeStatus(files.getDirectory());
    }

    private void poll(Minecraft mc, InboxFiles files) {
        for (String id : files.pendingIds()) {
            if (quitting) {
                // The game is closing: the rest stay unread, and the next launch refuses them.
                return;
            }
            InboxFiles.Claim claim = files.claim(id);
            if (claim == null) {
                continue;
            }
            MenuRequest request = MenuRequest.parse(claim.getId(), claim.getContent());
            record("claimed", request, null, null);
            if (!request.isAccepted()) {
                refuse(files, request, request.getRefusal(), request.getDetail());
            } else if (step != null || actions.isOnReconnect()) {
                refuse(files, request, "busy");
            } else if (mc.world != null) {
                // D25 (a): in a world the command inbox is the channel.
                refuse(files, request, "inWorld");
            } else if (!isAtMenu(mc)) {
                refuse(files, request, "busy");
            } else {
                begin(mc, files, request);
            }
        }
    }

    /**
     * No world, no integrated server, no connection, and a screen that is a menu: not one of
     * connecting or loading, and not one of Forge's questions waiting for a person. Unlike the
     * reconnect's rejoin, the screen of a lost connection counts as a menu here: it stays until
     * someone clicks, and the channel must not wait on it.
     */
    static boolean isAtMenu(Minecraft mc) {
        if (mc.world != null || mc.getIntegratedServer() != null || mc.getConnection() != null
                || mc.currentScreen == null) {
            return false;
        }
        Class<?> screen = mc.currentScreen.getClass();
        if (GuiDisconnected.class.isAssignableFrom(screen)) {
            return true;
        }
        return ClientActionExecutor.isMenuScreen(screen)
                && !net.minecraftforge.fml.client.GuiOldSaveLoadConfirm.class.isAssignableFrom(screen)
                && !net.minecraftforge.fml.client.GuiConfirmation.class.isAssignableFrom(screen)
                && !net.minecraftforge.fml.client.GuiNotification.class.isAssignableFrom(screen);
    }

    private void begin(Minecraft mc, InboxFiles files, MenuRequest request) {
        String kind = request.getStep();
        if (MenuRequest.QUIT.equals(kind)) {
            quitting = true;
            record("done", request, null, null);
            MenuResult.done(request).writeTo(files.getDirectory());
            new File(files.getDirectory(), "status.json").delete();
            mc.shutdown();
            return;
        }
        if (MenuRequest.CREATE.equals(kind)) {
            create(mc, files, request);
        } else if (MenuRequest.OPEN.equals(kind)) {
            open(mc, files, request);
        } else {
            connect(mc, files, request);
        }
    }

    private void create(Minecraft mc, InboxFiles files, MenuRequest request) {
        String name = request.getArgs().get(0);
        WorldOptions options = WorldOptions.parse(request.getArgs().subList(1, request.getArgs().size()));
        String unknown = options.checkRegistry(id -> Block.REGISTRY.containsKey(new ResourceLocation(id)),
                biome -> net.minecraft.world.biome.Biome.getBiome(biome) != null);
        if (unknown != null) {
            refuse(files, request, MenuRequest.REASON_BAD_ARGUMENTS, unknown);
            return;
        }
        String folder = SaveFolders.folderFor(name);
        File saves = new File(mc.gameDir, "saves");
        if (!SaveFolders.isFree(folder, f -> new File(saves, f).exists())) {
            refuse(files, request, "exists", null);
            return;
        }
        WorldType type = WorldType.byName(options.getType());
        GameType mode = GameType.getByName(options.getMode());
        if (type == null || mode == null) {
            // Checked by WorldOptions; said rather than created as something else.
            refuse(files, request, MenuRequest.REASON_BAD_ARGUMENTS, "unknownTypeOrMode");
            return;
        }
        Long seed = options.seed();
        WorldSettings settings = new WorldSettings(seed == null ? new Random().nextLong() : seed.longValue(),
                mode, options.isStructures(), false, type);
        if (options.getFlat() != null) {
            settings.setGeneratorOptions(options.getFlat());
        }
        if (options.isCheats()) {
            settings.enableCommands();
        }
        if (options.isBonusChest()) {
            settings.enableBonusChest();
        }
        step = new Step(request);
        step.folder = folder;
        record("started", request, null, folder);
        // As the Create button does: the world type's own hook, then the launch.
        try {
            type.onGUICreateWorldPress();
            mc.launchIntegratedServer(folder, name, settings);
        } catch (net.minecraftforge.fml.common.StartupQuery.AbortedException e) {
            // FML aborts a start this way; the open path catches the same inside FML. Anything
            // else is a real crash and goes on to the game's crash report.
            finish(step, "failed", "loadAborted");
        }
    }

    private void open(Minecraft mc, InboxFiles files, MenuRequest request) {
        String folder = request.getArgs().get(0);
        WorldSummary summary = null;
        try {
            if (mc.getSaveLoader().canLoadWorld(folder)) {
                for (WorldSummary candidate : mc.getSaveLoader().getSaveList()) {
                    if (folder.equals(candidate.getFileName())) {
                        summary = candidate;
                        break;
                    }
                }
            }
        } catch (Exception unreadable) {
            summary = null;
        }
        if (summary == null) {
            refuse(files, request, "notFound", null);
            return;
        }
        if (summary.askToOpenWorld()) {
            refuse(files, request, "newerVersion", null);
            return;
        }
        step = new Step(request);
        step.folder = folder;
        record("started", request, null, folder);
        FMLClientHandler.instance().tryLoadExistingWorld(new GuiWorldSelection(new GuiMainMenu()), summary);
    }

    private void connect(Minecraft mc, InboxFiles files, MenuRequest request) {
        String[] hostPort = splitHostPort(request.getArgs().get(0));
        if (hostPort == null) {
            refuse(files, request, MenuRequest.REASON_BAD_ARGUMENTS, null);
            return;
        }
        List<InetAddress> addresses;
        try {
            // Resolved once, here; the connection goes to this number, so no later lookup counts.
            addresses = Arrays.asList(InetAddress.getAllByName(hostPort[0]));
        } catch (UnknownHostException | SecurityException e) {
            refuse(files, request, "unknownHost", null);
            return;
        }
        if (!LocalAddresses.allLocal(addresses)) {
            refuse(files, request, "notLocal", null);
            return;
        }
        int port = Integer.parseInt(hostPort[1]);
        String literal = addresses.get(0).getHostAddress();
        step = new Step(request);
        record("started", request, null, null);
        mc.displayGuiScreen(new GuiConnecting(new GuiMultiplayer(new GuiMainMenu()), mc, literal, port));
        // After the screen's own loadWorld(null), which clears it: a later reconnect needs it.
        String shown = literal.indexOf(':') >= 0 ? "[" + literal + "]" : literal;
        step.server = new ServerData(hostPort[0], shown + ":" + port, false);
        mc.setServerData(step.server);
    }

    /** {@code host}, {@code host:port} or {@code [v6]:port} into host and port, or null. */
    static String[] splitHostPort(String address) {
        String host;
        String port = String.valueOf(DEFAULT_PORT);
        if (address.startsWith("[")) {
            int end = address.indexOf(']');
            if (end < 0) {
                return null;
            }
            host = address.substring(1, end);
            String rest = address.substring(end + 1);
            if (!rest.isEmpty()) {
                if (!rest.startsWith(":")) {
                    return null;
                }
                port = rest.substring(1);
            }
        } else {
            int colon = address.indexOf(':');
            if (colon >= 0) {
                if (address.indexOf(':', colon + 1) >= 0) {
                    return null;
                }
                host = address.substring(0, colon);
                port = address.substring(colon + 1);
            } else {
                host = address;
            }
        }
        if (host.isEmpty() || port.isEmpty()) {
            return null;
        }
        int number;
        try {
            number = Integer.parseInt(port);
        } catch (NumberFormatException e) {
            return null;
        }
        if (number < 1 || number > 65535) {
            return null;
        }
        return new String[] {host, String.valueOf(number)};
    }

    /** The player in a world: the step that put them there is done, or joined somewhere else. */
    @SubscribeEvent
    public void onJoinWorld(EntityJoinWorldEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (step == null || mc.player == null || event.getEntity() != mc.player) {
            return;
        }
        Step s = step;
        boolean expected;
        if (s.folder != null) {
            expected = mc.getIntegratedServer() != null && s.folder.equals(mc.getIntegratedServer().getFolderName());
        } else {
            expected = mc.getIntegratedServer() == null && mc.getCurrentServerData() == s.server;
        }
        finish(s, expected ? "done" : "failed", expected ? null : "joinedElsewhere");
    }

    private void advance(Minecraft mc) {
        Step s = step;
        long elapsed = System.currentTimeMillis() - s.startedAtMillis;
        boolean worldLoading = s.folder != null && mc.getIntegratedServer() != null;
        if (mc.currentScreen instanceof GuiDisconnected) {
            finish(s, "failed", s.folder == null ? "connectFailed" : "loadFailed");
        } else if (mc.world == null && isAtMenu(mc) && ++s.idleTicks >= LOAD_ABORT_TICKS) {
            // Back at a menu with nothing starting: the world never started, a Forge question
            // was answered with cancel, or the connection was cancelled.
            finish(s, "failed", s.folder == null ? "connectAborted" : "loadAborted");
        } else if (elapsed > STEP_DEADLINE_MS + (worldLoading ? WORLD_LOAD_EXTRA_MS : 0L)) {
            // Said; a world or a connection still under way may yet finish, so the agent reads
            // the client's state before the next step.
            finish(s, "failed", "timeout");
        }
    }

    private void finish(Step s, String outcome, String reason) {
        step = null;
        record(outcome, s.request, reason, s.folder);
        MenuResult result = "done".equals(outcome) ? MenuResult.done(s.request) : MenuResult.failed(s.request, reason);
        result.with("folder", s.folder).writeTo(directory());
    }

    private void refuse(InboxFiles files, MenuRequest request, String reason) {
        refuse(files, request, reason, null);
    }

    private void refuse(InboxFiles files, MenuRequest request, String reason, String detail) {
        LogRecord record = LogRecord.of(EventType.MENU_STEP)
                .add("side", "CLIENT")
                .add("action", "refused")
                .add("id", request.getId())
                .add("step", request.getStep())
                .add("request", request.getLine())
                .add("reason", reason)
                .add("detail", detail);
        ToolkitLog.write(record);
        MenuResult.refused(request, reason).with("detail", detail).writeTo(files.getDirectory());
    }

    private static void record(String action, MenuRequest request, String reason, String folder) {
        ToolkitLog.write(LogRecord.of(EventType.MENU_STEP)
                .add("side", "CLIENT")
                .add("action", action)
                .add("id", request.getId())
                .add("step", request.getStep())
                .add("request", request.getLine())
                .add("reason", reason)
                .add("folder", folder));
    }

    /** Present while the channel listens, with the launch's start, so an agent knows when to write. */
    private void writeStatus(File directory) {
        JsonObject status = new JsonObject();
        status.addProperty("schemaVersion", MenuResult.SCHEMA_VERSION);
        status.addProperty("listening", true);
        status.addProperty("startedAtMillis", startedAtMillis);
        File target = new File(directory, "status.json");
        File temporary = new File(directory, "status.json.tmp");
        try {
            Files.write(temporary.toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(status).getBytes(UTF8));
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            ToolkitLog.error("Client request channel status could not be written", e.toString());
        }
    }
}
