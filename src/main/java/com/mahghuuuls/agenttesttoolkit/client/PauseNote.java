package com.mahghuuuls.agenttesttoolkit.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mahghuuuls.agenttesttoolkit.config.ToolkitConfigLoader;
import com.mahghuuuls.agenttesttoolkit.inbox.InboxFiles;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;

/**
 * Keeps a note beside the command inbox while a single-player game is paused.
 *
 * <p>A screen that pauses single player (a book, the pause menu, many mods' own screens) stops
 * the integrated server's ticks, and the command inbox is read on those ticks, so a request
 * written then gets no answer and no record. Only this client can see the pause; its own ticks
 * go on. The note names the open screen, so the reason for a slow answer can be read at once.
 * It is written only when the pause starts or another screen pauses the game, and removed when
 * the game runs again, when the world is left, at the first tick of a launch and at every world
 * entry, so a note left by a game closed or crashed while paused never outlives it. A write that
 * fails, or finds no inbox folder, is not tried again until the next change: the server cannot
 * create the folder while the game is paused. A client of a dedicated server never pauses this
 * way and writes nothing.
 */
public final class PauseNote {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final PauseState state = new PauseState();
    private boolean started;
    private WorldClient world;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (!started || (mc.world != null && mc.world != world)) {
            // A new launch or a new world: whatever note is there belongs to a game that ended.
            started = true;
            state.update(false, null);
            remove();
        }
        world = mc.world;
        boolean paused = mc.world != null && mc.isSingleplayer() && mc.isGamePaused();
        String screen = mc.currentScreen == null ? null : mc.currentScreen.getClass().getName();
        PauseState.Change change = state.update(paused, screen);
        if (change == PauseState.Change.REMOVE) {
            remove();
        } else if (change == PauseState.Change.WRITE) {
            File directory = InboxFiles.inboxDirectory(ToolkitConfigLoader.getConfigDirectory());
            if (directory == null || !directory.isDirectory()) {
                return;
            }
            JsonObject root = new JsonObject();
            root.addProperty("schemaVersion", 1);
            root.addProperty("paused", true);
            root.addProperty("screen", screen);
            root.addProperty("sinceMillis", System.currentTimeMillis());
            root.addProperty("worldTick", mc.world.getTotalWorldTime());
            byte[] bytes = new GsonBuilder().setPrettyPrinting().create().toJson(root).getBytes(UTF8);
            if (!InboxFiles.writeAtomically(new File(directory, InboxFiles.PAUSE_NOTE), bytes)) {
                ToolkitLog.error("Pause note could not be written", directory.getPath());
            }
        }
    }

    private static void remove() {
        File directory = InboxFiles.inboxDirectory(ToolkitConfigLoader.getConfigDirectory());
        if (directory == null) {
            return;
        }
        try {
            Files.deleteIfExists(new File(directory, InboxFiles.PAUSE_NOTE).toPath());
        } catch (IOException | RuntimeException e) {
            ToolkitLog.error("Pause note could not be removed", String.valueOf(e));
        }
    }
}
