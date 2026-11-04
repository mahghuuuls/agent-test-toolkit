package com.mahghuuuls.agenttesttoolkit.results;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mahghuuuls.agenttesttoolkit.bundle.BundleExecution;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

/**
 * Renders one execution's records as a JSON document and writes it.
 *
 * <p>Every value in the file is copied from a record that already passed through the single
 * log writer, except what the collector was given when the run started and no record carries:
 * the operator bound at the start, used when the run binds none, and the wall-clock start and the
 * save folder, which say which launch and world the file came from.
 * The document groups the records by what they were, so tooling can read "which assertions
 * failed" without scraping, but it invents nothing: an assertion in the file is the
 * {@code ASSERT} record's fields, a failure is the {@code ERROR} record's fields.
 *
 * <p>A write that fails is an error record in the log and nothing more. The bundle's outcome
 * was decided before this ran, and a full disk must not turn a passed bundle into a failed
 * one, or throw into the server tick.
 */
public final class BundleResultWriter {

    /** Bumped when a field is renamed or removed. Adding a field keeps the version. */
    public static final int SCHEMA_VERSION = 1;

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private BundleResultWriter() {
    }

    /**
     * Writes {@code <bundle>-<startWorldTick>.json} under the directory.
     *
     * @param operatorAtStart the operator bound when the run began, used unless the run changed it
     * @param startedAtMillis the wall-clock time the run was accepted, or null
     * @param world           the save folder of the loaded world, or null
     * @return the written file's name, or null when the write failed
     */
    public static String write(File directory, BundleExecution execution, List<LogRecord> records,
                               String operatorAtStart, Long startedAtMillis, String world) {
        JsonObject document = render(execution, records, operatorAtStart, startedAtMillis, world);
        String name = execution.getBundleName() + "-" + startTick(records) + ".json";
        File file = new File(directory, name);
        try {
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IOException("could not create " + directory.getAbsolutePath());
            }
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(document);
            Files.write(file.toPath(), json.getBytes(UTF8));
            return file.getName();
        } catch (IOException e) {
            ToolkitLog.error("Bundle result could not be written",
                    file.getName() + ": " + e.getMessage());
        } catch (RuntimeException e) {
            ToolkitLog.error("Bundle result could not be written",
                    file.getName() + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return null;
    }

    /** The document. Package-visible so a test can check it without a disk. */
    static JsonObject render(BundleExecution execution, List<LogRecord> records,
                             String operatorAtStart, Long startedAtMillis, String world) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.addProperty("bundle", execution.getBundleName());
        root.addProperty("hash", execution.getHash());
        if (startedAtMillis != null) {
            root.addProperty("startedAtMillis", startedAtMillis);
        }
        if (world != null) {
            root.addProperty("world", world);
        }

        JsonArray assertions = new JsonArray();
        JsonArray failures = new JsonArray();
        JsonArray markers = new JsonArray();
        JsonArray checkpoints = new JsonArray();
        JsonArray awaits = new JsonArray();
        JsonArray steps = new JsonArray();
        JsonArray tolerated = new JsonArray();
        String session = null;
        String operator = operatorAtStart;

        for (LogRecord record : records) {
            Map<String, String> fields = record.getFields();
            if (session == null && fields.containsKey("session")) {
                session = fields.get("session");
            }
            switch (record.getEventType()) {
                case BUNDLE_START:
                    root.addProperty("startTick", fields.get("worldTick"));
                    // Who started the run, and from which inbox request, as the start record
                    // says, so the file and the log cannot disagree.
                    copy(root, fields, "channel");
                    copy(root, fields, "caller");
                    copy(root, fields, "requestId");
                    break;
                case BUNDLE_END:
                    root.addProperty("endTick", fields.get("worldTick"));
                    copy(root, fields, "executed");
                    copy(root, fields, "failed");
                    copy(root, fields, "tolerated");
                    copy(root, fields, "total");
                    copy(root, fields, "stoppedEarly");
                    copy(root, fields, "durationTicks");
                    copy(root, fields, "senderLost");
                    copy(root, fields, "discardedOnServerStop");
                    break;
                case ASSERT:
                    assertions.add(object(fields));
                    break;
                case ERROR:
                    failures.add(object(fields));
                    break;
                case MARK:
                    markers.add(object(fields));
                    break;
                case CHECKPOINT:
                    checkpoints.add(object(fields));
                    break;
                case AWAIT:
                    awaits.add(object(fields));
                    break;
                case BUNDLE_STEP:
                    steps.add(object(fields));
                    break;
                case TOLERATED:
                    tolerated.add(object(fields));
                    break;
                case OPERATOR:
                    // The last binding wins, and an unbinding clears it, so the file says who
                    // the operator was at the end of the run.
                    operator = "bound".equals(fields.get("action")) ? fields.get("name") : null;
                    break;
                default:
                    break;
            }
        }

        if (session != null) {
            root.addProperty("session", session);
        }
        if (operator != null) {
            root.addProperty("operator", operator);
        }
        root.add("assertions", assertions);
        root.add("failures", failures);
        root.add("markers", markers);
        root.add("checkpoints", checkpoints);
        root.add("awaits", awaits);
        root.add("toleratedSteps", tolerated);
        if (steps.size() > 0) {
            root.add("steps", steps);
        }
        return root;
    }

    private static String startTick(List<LogRecord> records) {
        for (LogRecord record : records) {
            if (record.getEventType() == EventType.BUNDLE_START) {
                String tick = record.getFields().get("worldTick");
                if (tick != null) {
                    return tick;
                }
            }
        }
        return "0";
    }

    private static void copy(JsonObject into, Map<String, String> fields, String key) {
        if (fields.containsKey(key)) {
            into.addProperty(key, fields.get(key));
        }
    }

    /** Every field of one record, verbatim, as strings. The log is the authority on types. */
    private static JsonObject object(Map<String, String> fields) {
        JsonObject out = new JsonObject();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            out.addProperty(field.getKey(), field.getValue());
        }
        return out;
    }
}
