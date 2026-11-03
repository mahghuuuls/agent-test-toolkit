package com.mahghuuuls.agenttesttoolkit.client.menu;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * One answer file of the client request channel, written under a temporary name and moved into
 * place, so a reader never sees half of it (REQ-216). The same shape as the command inbox's.
 */
final class MenuResult {

    static final int SCHEMA_VERSION = 1;
    static final String SUFFIX = ".result.json";
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final JsonObject root = new JsonObject();

    private MenuResult(MenuRequest request, String outcome, String reason) {
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.addProperty("id", request.getId());
        if (request.getLine() != null) {
            root.addProperty("request", request.getLine());
        }
        if (request.getStep() != null) {
            root.addProperty("step", request.getStep());
        }
        root.addProperty("outcome", outcome);
        if (reason != null) {
            root.addProperty("reason", reason);
        }
    }

    static MenuResult done(MenuRequest request) {
        return new MenuResult(request, "done", null);
    }

    static MenuResult failed(MenuRequest request, String reason) {
        return new MenuResult(request, "failed", reason);
    }

    static MenuResult refused(MenuRequest request, String reason) {
        return new MenuResult(request, "refused", reason);
    }

    MenuResult with(String name, Object value) {
        if (value instanceof Number) {
            root.addProperty(name, (Number) value);
        } else if (value instanceof Boolean) {
            root.addProperty(name, (Boolean) value);
        } else if (value != null) {
            root.addProperty(name, value.toString());
        }
        return this;
    }

    boolean writeTo(File directory) {
        if (directory == null) {
            return false;
        }
        String id = root.get("id").getAsString();
        File target = new File(directory, id + SUFFIX);
        File temporary = new File(directory, id + SUFFIX + ".tmp");
        try {
            Files.write(temporary.toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(root).getBytes(UTF8));
            try {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            ToolkitLog.error("Menu step result could not be written", id + ": " + e);
            try {
                Files.deleteIfExists(temporary.toPath());
            } catch (IOException | RuntimeException ignored) {
                // The error above already says the result is missing.
            }
            return false;
        }
    }
}
