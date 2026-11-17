package com.mahghuuuls.agenttesttoolkit.client.menu;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mahghuuuls.agenttesttoolkit.client.ClientActionExecutor;
import com.mahghuuuls.agenttesttoolkit.client.PauseNote;
import com.mahghuuuls.agenttesttoolkit.client.ScreenshotTaker;
import com.mahghuuuls.agenttesttoolkit.client.screen.ScreenClicker;
import com.mahghuuuls.agenttesttoolkit.client.screen.ScreenReader;
import com.mahghuuuls.agenttesttoolkit.config.ToolkitConfigLoader;
import com.mahghuuuls.agenttesttoolkit.inbox.InboxFiles;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.net.ScreenshotRequest;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiWorldSelection;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.storage.WorldSummary;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The client request channel: a folder an agent drops request files into to drive the client
 * where no world, and so no command inbox, is open.
 *
 * <p>Reads {@code config/devtool/client-inbox/} every 10 client ticks while
 * {@code client.requestChannel} is true, and takes one step at a time on the client thread: at a
 * menu, the menu steps, a description or a picture of the screen, and a mod's entry in the Mods
 * list; in a world, only closing a screen that pauses single player. Any other request while a
 * world is open is answered {@code inWorld}, one while a step or a toolkit reconnect is under way
 * {@code busy}. The requests waiting at the first read of a launch were placed before this launch
 * and are refused. Client-only: registered from {@code ClientProxy}, the one place the client
 * classes are referenced, so a dedicated server never loads them.
 */
public final class MenuChannel {

    static final int INTERVAL_TICKS = 10;
    /** How long a step may take: to put the player in the world, or to take its picture. */
    static final long STEP_DEADLINE_MS = 60000L;
    /** And how much longer while an integrated server is starting: a large world loads slowly. */
    static final long WORLD_LOAD_EXTRA_MS = 240000L;
    /** A create or open with no integrated server and no world this many ticks later was aborted. */
    static final int LOAD_ABORT_TICKS = 40;
    static final int DEFAULT_PORT = 25565;
    /** A step not finished by its deadline. */
    static final String REASON_TIMEOUT = "timeout";
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final ClientActionExecutor actions;
    private final ScreenshotTaker screenshots;
    private final PauseNote pause;
    private final QuestionWatch questions = new QuestionWatch();
    private int ticks;
    private boolean started;
    /** The key as last seen, so turning it off and on again restarts the channel cleanly. */
    private Boolean lastKey;
    private long startedAtMillis;

    /** The one step under way, or null. */
    private Step step;
    /** A quit was answered: the game is closing. */
    private boolean quitting;
    /** Reused ids already said once this launch. */
    private final java.util.Set<String> reportedDuplicates = new java.util.HashSet<String>();

    private static final class Step {
        final MenuRequest request;
        final long startedAtMillis = System.currentTimeMillis();
        /** A create, open or connect: done when the player joins. */
        final boolean joins;
        String folder;
        ServerData server;
        int idleTicks;
        /** For {@code mods}: the list being driven. */
        ModListDriver driver;

        Step(MenuRequest request, boolean joins) {
            this.request = request;
            this.joins = joins;
        }
    }

    public MenuChannel(ClientActionExecutor actions, ScreenshotTaker screenshots, PauseNote pause) {
        this.actions = actions;
        this.screenshots = screenshots;
        this.pause = pause;
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
        questions.tick(mc);
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

    /** The Mods list's details settle over frames, so its picture waits on them. */
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END && step != null && step.driver != null) {
            step.driver.frame(Minecraft.getMinecraft());
        }
    }

    /** Last of all listeners, so a question is seen as the screen that really opens. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onGuiOpen(GuiOpenEvent event) {
        questions.onGuiOpen(event);
    }

    /** The first read of a launch: everything already waiting was placed before it. */
    private void start(InboxFiles files) {
        started = true;
        startedAtMillis = System.currentTimeMillis();
        files.ensureDirectory();
        for (String id : files.pendingIds()) {
            InboxFiles.Claim claim = files.claim(id);
            if (claim == null) {
                reportDuplicate(files, id);
                continue;
            }
            MenuRequest request = MenuRequest.parse(claim.getId(), claim.getContent());
            record("claimed", request, null, null, null);
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
                reportDuplicate(files, id);
                continue;
            }
            MenuRequest request = MenuRequest.parse(claim.getId(), claim.getContent());
            record("claimed", request, null, null, null);
            String kind = request.getStep();
            if (!request.isAccepted()) {
                refuse(files, request, request.getRefusal(), request.getDetail());
            } else if (step != null || actions.isOnReconnect()) {
                refuse(files, request, "busy");
            } else if (mc.world != null) {
                if (MenuRequest.CLOSE.equals(kind)) {
                    // The one request taken in a world: a person's Esc on a screen that pauses
                    // the game, which stops the command inbox's own reading.
                    close(mc, files, request);
                } else {
                    // In a world the command inbox is the channel. The detail says whether a
                    // screen pauses the game, which is why a command-inbox request may get no answer.
                    refuse(files, request, "inWorld", pauseDetail(mc));
                }
            } else if (MenuRequest.CLOSE.equals(kind)) {
                refuse(files, request, ConfirmScreens.REASON_NOT_PAUSED, pauseDetail(mc));
            } else if (MenuRequest.MODS.equals(kind) && !ModListDriver.isLoaded(request.getArgs().get(0))) {
                refuse(files, request, ModListDriver.REASON_NOT_FOUND);
            } else if (isShown(kind) ? !canShow(mc) : !isAtMenu(mc)) {
                refuse(files, request, "busy");
            } else if (MenuRequest.MODS.equals(kind) && !(mc.currentScreen instanceof GuiMainMenu)) {
                refuse(files, request, ModListDriver.REASON_NOT_TITLE_SCREEN, "screen=" + screenName(mc.currentScreen));
            } else {
                begin(mc, files, request);
            }
        }
    }

    /** The steps that only read or picture the screen. */
    private static boolean isShown(String kind) {
        return MenuRequest.SCREEN.equals(kind) || MenuRequest.SCREENSHOT.equals(kind);
    }

    /** Whether the game is paused, and the screen open, for a refusal's detail. */
    private String pauseDetail(Minecraft mc) {
        return "paused=" + mc.isGamePaused()
                + (mc.currentScreen == null ? "" : " screen=" + mc.currentScreen.getClass().getName());
    }

    /**
     * As the command inbox: a reused id never runs, and is said once per launch, with no result
     * file, which would overwrite the earlier answer.
     */
    private void reportDuplicate(InboxFiles files, String id) {
        if (files.isAlreadyClaimed(id) && reportedDuplicates.add(id)) {
            ToolkitLog.write(LogRecord.of(EventType.MENU_STEP)
                    .add("side", "CLIENT")
                    .add("action", "refused")
                    .add("id", id)
                    .add("reason", "duplicateId"));
        }
    }

    /**
     * No world, no integrated server, no connection, and a screen that is a menu: not one of
     * connecting or loading, and not one of the questions a world asks as it opens, which wait
     * for a person. Unlike the reconnect's rejoin, the screen of a lost connection counts as a
     * menu here: it stays until someone clicks, and the channel must not wait on it.
     */
    static boolean isAtMenu(Minecraft mc) {
        if (!isOutsideWorld(mc) || mc.currentScreen == null) {
            return false;
        }
        Class<?> screen = mc.currentScreen.getClass();
        if (GuiDisconnected.class.isAssignableFrom(screen)) {
            return true;
        }
        return ClientActionExecutor.isMenuScreen(screen) && !ConfirmScreens.isQuestion(ScreenReader.hierarchy(screen));
    }

    /**
     * Where a screen may be read or pictured: at a menu, and also on the old save's question,
     * an ordinary screen where the game keeps ticking and drawing while it waits for a person.
     */
    private static boolean canShow(Minecraft mc) {
        return isAtMenu(mc) || (isOutsideWorld(mc) && mc.currentScreen != null
                && ConfirmScreens.isOldSave(ScreenReader.hierarchy(mc.currentScreen.getClass())));
    }

    /** No world, no integrated server and no connection. */
    private static boolean isOutsideWorld(Minecraft mc) {
        return mc.world == null && mc.getIntegratedServer() == null && mc.getConnection() == null;
    }

    private void begin(Minecraft mc, InboxFiles files, MenuRequest request) {
        String kind = request.getStep();
        if (MenuRequest.QUIT.equals(kind)) {
            quitting = true;
            record("done", request, null, null, null);
            MenuResult.done(request).writeTo(files.getDirectory());
            new File(files.getDirectory(), "status.json").delete();
            mc.shutdown();
            return;
        }
        if (MenuRequest.SCREEN.equals(kind)) {
            describe(mc, files, request);
        } else if (MenuRequest.SCREENSHOT.equals(kind)) {
            picture(mc, request);
        } else if (MenuRequest.MODS.equals(kind)) {
            mods(mc, request);
        } else if (MenuRequest.CREATE.equals(kind)) {
            create(mc, files, request);
        } else if (MenuRequest.OPEN.equals(kind)) {
            open(mc, files, request);
        } else {
            connect(mc, files, request);
        }
    }

    /** {@code SCREEN_INFO} and its buttons and slots, then the answer; changes nothing. */
    private void describe(Minecraft mc, InboxFiles files, MenuRequest request) {
        ScreenReader.Info info = ScreenReader.read(mc, mc.currentScreen);
        ScreenReader.write(info, "id", request.getId());
        Map<String, String> fields = new LinkedHashMap<String, String>();
        fields.put("screen", info.screenName());
        if (info.screen != null) {
            fields.putAll(ScreenReader.summary(info));
        }
        record("done", request, null, null, fields);
        MenuResult result = MenuResult.done(request);
        for (Map.Entry<String, String> field : fields.entrySet()) {
            result.with(field.getKey(), field.getValue());
        }
        result.writeTo(files.getDirectory());
    }

    /**
     * A picture of the menu on screen, by the screenshot command's rules; its file is numbered
     * by the wall clock, since no world tick runs here. The answer waits for the capture.
     */
    private void picture(Minecraft mc, MenuRequest request) {
        final Step s = new Step(request, false);
        step = s;
        final Map<String, String> fields = new LinkedHashMap<String, String>();
        fields.put("screen", screenName(mc.currentScreen));
        record("started", request, null, null, fields);
        ScreenshotRequest picture = new ScreenshotRequest(request.getArgs().get(0), 0, System.currentTimeMillis(),
                request.getHide());
        screenshots.takeLocal(picture, new ScreenshotTaker.Taken() {
            @Override
            public void taken(boolean saved, String file, String reason) {
                if (step != s) {
                    return;
                }
                if (file != null) {
                    fields.put("file", file);
                }
                finish(s, saved ? "done" : "failed", saved ? null : reason, fields);
            }
        });
    }

    /** A mod's entry in the Mods list; the driver answers once it is back at the title screen. */
    private void mods(Minecraft mc, MenuRequest request) {
        final Step s = new Step(request, false);
        step = s;
        record("started", request, null, null, null);
        s.driver = new ModListDriver(request, screenshots, new ModListDriver.Done() {
            @Override
            public void finished(String outcome, String reason, Map<String, String> fields) {
                if (step == s) {
                    finish(s, outcome, reason, fields);
                }
            }
        });
        s.driver.open(mc);
    }

    /**
     * Esc on a screen that pauses single player, as a person's key: the screen's own key handler
     * with Esc, so a screen that does something of its own on Esc does it. A screen that asks a
     * person to decide is left for them.
     */
    private void close(Minecraft mc, InboxFiles files, MenuRequest request) {
        GuiScreen screen = mc.currentScreen;
        List<String> hierarchy = screen == null ? Collections.<String>emptyList()
                : ScreenReader.hierarchy(screen.getClass());
        String refusal = ConfirmScreens.closeRefusal(pause.isPaused(), hierarchy, request.getArgs().get(0));
        if (refusal != null) {
            refuse(files, request, refusal, pauseDetail(mc));
            return;
        }
        if (!ScreenClicker.available()) {
            // Said as the screen steps say it, rather than failing on a handler that is not there.
            refuse(files, request, ConfirmScreens.REASON_ROUTE_UNAVAILABLE, String.valueOf(ScreenClicker.failure()));
            return;
        }
        String before = screenName(screen);
        Map<String, String> fields = new LinkedHashMap<String, String>();
        fields.put("screen", before);
        try {
            ScreenClicker.type(screen, (char) 0, ScreenClicker.KEY_ESCAPE);
        } catch (Exception failure) {
            fields.put("showing", screenName(mc.currentScreen));
            String reason = failure.getClass().getSimpleName();
            record("failed", request, reason, null, fields);
            MenuResult.failed(request, reason).with("screen", before).with("showing", fields.get("showing"))
                    .writeTo(files.getDirectory());
            return;
        }
        fields.put("showing", screenName(mc.currentScreen));
        record("done", request, null, null, fields);
        MenuResult.done(request).with("screen", before).with("showing", fields.get("showing"))
                .writeTo(files.getDirectory());
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
        step = new Step(request, true);
        step.folder = folder;
        record("started", request, null, folder, null);
        // As the Create button does: the world type's own hook, then the launch.
        try {
            type.onGUICreateWorldPress();
            mc.launchIntegratedServer(folder, name, settings);
        } catch (net.minecraftforge.fml.common.StartupQuery.AbortedException e) {
            // FML aborts a start this way; the open path catches the same inside FML. Anything
            // else is a real crash and goes on to the game's crash report.
            finish(step, "failed", "loadAborted", null);
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
        final Step s = new Step(request, true);
        step = s;
        s.folder = folder;
        record("started", request, null, folder, null);
        // Armed for this one open: a question it shows is reported or answered as it asks.
        questions.arm(request, new QuestionWatch.Owner() {
            @Override
            public void reported(String kind, String text) {
                if (step == s) {
                    Map<String, String> fields = new LinkedHashMap<String, String>();
                    fields.put("question", kind);
                    fields.put("text", text);
                    finish(s, "failed", QuestionWatch.REASON_QUESTION, fields);
                }
            }

            @Override
            public void cancelled(String kind) {
                if (step == s) {
                    Map<String, String> fields = new LinkedHashMap<String, String>();
                    fields.put("question", kind);
                    finish(s, "failed", QuestionWatch.REASON_CANCELLED, fields);
                }
            }
        });
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
        step = new Step(request, true);
        record("started", request, null, null, null);
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
        if (step == null || !step.joins || mc.player == null || event.getEntity() != mc.player) {
            return;
        }
        Step s = step;
        boolean expected;
        if (s.folder != null) {
            expected = mc.getIntegratedServer() != null && s.folder.equals(mc.getIntegratedServer().getFolderName());
        } else {
            expected = mc.getIntegratedServer() == null && mc.getCurrentServerData() == s.server;
        }
        finish(s, expected ? "done" : "failed", expected ? null : "joinedElsewhere", null);
    }

    private void advance(Minecraft mc) {
        Step s = step;
        long elapsed = System.currentTimeMillis() - s.startedAtMillis;
        if (s.driver != null) {
            s.driver.tick(mc);
            if (step != s) {
                return;
            }
        }
        if (!s.joins) {
            if (elapsed > STEP_DEADLINE_MS) {
                if (s.driver != null) {
                    // Through the driver, which puts the title screen back before it answers.
                    s.driver.end(mc, REASON_TIMEOUT);
                } else {
                    finish(s, "failed", REASON_TIMEOUT, null);
                }
            }
            return;
        }
        boolean worldLoading = s.folder != null && mc.getIntegratedServer() != null;
        if (mc.currentScreen instanceof GuiDisconnected) {
            finish(s, "failed", s.folder == null ? "connectFailed" : "loadFailed", null);
        } else if (mc.world == null && isAtMenu(mc) && ++s.idleTicks >= LOAD_ABORT_TICKS) {
            // Back at a menu with nothing starting: the world never started, a Forge question
            // was answered with cancel, or the connection was cancelled.
            finish(s, "failed", s.folder == null ? "connectAborted" : "loadAborted", null);
        } else if (elapsed > STEP_DEADLINE_MS + (worldLoading ? WORLD_LOAD_EXTRA_MS : 0L)) {
            // Said; a world or a connection still under way may yet finish, so the agent reads
            // the client's state before the next step.
            finish(s, "failed", REASON_TIMEOUT, null);
        }
    }

    /**
     * The step's record and answer. Also called from a question's listener while the world's
     * start holds the client thread, where no tick would come to write it.
     */
    private void finish(Step s, String outcome, String reason, Map<String, String> fields) {
        step = null;
        if (s.joins) {
            questions.disarm();
        }
        record(outcome, s.request, reason, s.folder, fields);
        MenuResult result = "done".equals(outcome) ? MenuResult.done(s.request) : MenuResult.failed(s.request, reason);
        result.with("folder", s.folder);
        if (fields != null) {
            for (Map.Entry<String, String> field : fields.entrySet()) {
                result.with(field.getKey(), field.getValue());
            }
        }
        result.writeTo(directory());
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

    private static void record(String action, MenuRequest request, String reason, String folder,
                               Map<String, String> fields) {
        LogRecord record = LogRecord.of(EventType.MENU_STEP)
                .add("side", "CLIENT")
                .add("action", action)
                .add("id", request.getId())
                .add("step", request.getStep())
                .add("request", request.getLine())
                .add("reason", reason)
                .add("folder", folder);
        if (fields != null) {
            for (Map.Entry<String, String> field : fields.entrySet()) {
                record.add(field.getKey(), field.getValue());
            }
        }
        ToolkitLog.write(record);
    }

    private static String screenName(GuiScreen screen) {
        return screen == null ? "none" : screen.getClass().getName();
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
