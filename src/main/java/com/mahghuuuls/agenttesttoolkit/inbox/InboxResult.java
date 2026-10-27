package com.mahghuuuls.agenttesttoolkit.inbox;

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
 * The answer to one claimed request: {@code <id>.result.json} beside it.
 *
 * <p>Exactly one per claimed request, written atomically: to a temporary name first and then
 * moved, so the agent never reads half a file under the final name. Outcomes are
 * {@code done}, {@code failed} and {@code refused}; a {@code run} is answered when its bundle
 * ends, with the bundle's own counters. The log stays the authority; this file exists so the
 * agent can wait on one file rather than scrape records.
 */
public final class InboxResult {

    public static final int SCHEMA_VERSION = 1;
    public static final String SUFFIX = ".result.json";

    public static final String DONE = "done";
    public static final String FAILED = "failed";
    public static final String REFUSED = "refused";

    /** Fixed reason tokens. A command's own message goes in {@code detail}, never in reason. */
    public static final String REASON_COMMAND_FAILED = "commandFailed";
    public static final String REASON_RUN_NOT_STARTED = "runNotStarted";
    public static final String REASON_BUNDLE_FAILED = "bundleFailed";
    public static final String REASON_SENDER_LOST = "senderLost";
    public static final String REASON_SERVER_STOPPED = "serverStopped";

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final JsonObject root = new JsonObject();

    private InboxResult(InboxRequest request, String outcome, String reason) {
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.addProperty("id", request.getId());
        root.addProperty("request", request.getLine());
        if (request.getVerb() != null) {
            root.addProperty("verb", request.getVerb());
        }
        root.addProperty("outcome", outcome);
        if (reason != null) {
            root.addProperty("reason", reason);
        }
    }

    public static InboxResult done(InboxRequest request) {
        return new InboxResult(request, DONE, null);
    }

    public static InboxResult failed(InboxRequest request, String reason) {
        return new InboxResult(request, FAILED, reason);
    }

    public static InboxResult refused(InboxRequest request, String reason) {
        return new InboxResult(request, REFUSED, reason);
    }

    /**
     * The result of a {@code run}, from the bundle's own counters, with the same names as
     * {@code BUNDLE_END}. {@code stoppedEarly} is true for a discarded run, as it is on the
     * end record written at server stop.
     */
    public static InboxResult run(InboxRequest request, String bundle, String hash, int executed,
                                  int failed, int tolerated, int total, boolean stoppedEarly,
                                  int durationTicks, boolean senderLost, boolean discarded,
                                  String firstFailure) {
        boolean clean = !discarded && failed == 0 && !senderLost;
        String reason = discarded ? REASON_SERVER_STOPPED
                : senderLost ? REASON_SENDER_LOST
                : failed > 0 ? REASON_BUNDLE_FAILED : null;
        InboxResult result = clean ? done(request) : failed(request, reason);
        return result.with("bundle", bundle)
                .with("hash", hash)
                .with("executed", executed)
                .with("failed", failed)
                .with("tolerated", tolerated)
                .with("total", total)
                .with("stoppedEarly", discarded || stoppedEarly)
                .with("durationTicks", durationTicks)
                .with("senderLost", senderLost ? Boolean.TRUE : null)
                .with("discardedOnServerStop", discarded ? Boolean.TRUE : null)
                .with("firstFailure", firstFailure);
    }

    /** Adds a field, skipping nulls so an absent value is absent rather than {@code null}. */
    public InboxResult with(String name, Object value) {
        if (value instanceof Number) {
            root.addProperty(name, (Number) value);
        } else if (value instanceof Boolean) {
            root.addProperty(name, (Boolean) value);
        } else if (value != null) {
            root.addProperty(name, value.toString());
        }
        return this;
    }

    public String render() {
        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

    /**
     * Writes the file. A failure to write is an {@code ERROR} record and nothing more: the
     * request's effect has already happened and must not be reported differently because a
     * disk was full.
     *
     * @return true when written
     */
    public boolean writeTo(File directory) {
        if (directory == null) {
            return false;
        }
        String id = root.get("id").getAsString();
        File target = new File(directory, id + SUFFIX);
        File temporary = new File(directory, id + SUFFIX + ".tmp");
        try {
            Files.write(temporary.toPath(), render().getBytes(UTF8));
            try {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            ToolkitLog.error("Inbox result could not be written", id + ": " + e);
            try {
                Files.deleteIfExists(temporary.toPath());
            } catch (IOException | RuntimeException ignored) {
                // The error above already says the result is missing.
            }
            return false;
        }
    }
}
