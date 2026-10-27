package com.mahghuuuls.agenttesttoolkit.inbox;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.InboxOpening;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * {@code status.json} in the inbox folder: present exactly while the inbox is open.
 *
 * <p>Lets the agent tell "not up yet" from "closed" from "open" without reading the log: no
 * file means closed or not started; the file names the opener, the world, and when it opened.
 * Deleted when the inbox closes and when the server stops.
 */
public final class InboxStatus {

    public static final String FILE_NAME = "status.json";

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private InboxStatus() {
    }

    public static void write(File directory, InboxOpening opening, String worldFolder) {
        if (directory == null || opening == null) {
            return;
        }
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("open", true);
        root.addProperty("operator", opening.getOperatorName());
        root.addProperty("operatorUuid", opening.getOperatorUuid().toString());
        if (worldFolder != null) {
            root.addProperty("world", worldFolder);
        }
        root.addProperty("openedWorldTick", opening.getOpenedWorldTick());
        root.addProperty("openedAtMillis", opening.getOpenedAtMillis());
        File target = new File(directory, FILE_NAME);
        File temporary = new File(directory, FILE_NAME + ".tmp");
        try {
            Files.write(temporary.toPath(),
                    new GsonBuilder().setPrettyPrinting().create().toJson(root).getBytes(UTF8));
            try {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            ToolkitLog.error("Inbox status could not be written", String.valueOf(e));
            try {
                Files.deleteIfExists(temporary.toPath());
            } catch (IOException | RuntimeException ignored) {
                // The error above already says the status file is missing.
            }
        }
    }

    public static void delete(File directory) {
        if (directory == null) {
            return;
        }
        File target = new File(directory, FILE_NAME);
        if (!target.exists()) {
            return;
        }
        // Retried once for a brief lock. A reader that keeps the file open defeats both tries;
        // the stale-status cleanup at server start covers that case.
        if (!target.delete() && !target.delete()) {
            ToolkitLog.error("Inbox status could not be deleted", target.getName());
        }
    }
}
