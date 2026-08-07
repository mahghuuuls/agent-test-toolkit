package com.mahghuuuls.agenttesttoolkit.bundle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns the JSON text of one bundle file into definitions.
 *
 * <p>Parsed element by element rather than bound to a POJO, for two reasons. The command list
 * is polymorphic, since an entry may be a bare string or an object carrying a delay, which
 * automatic binding handles badly. And error messages matter here: a malformed bundle file must
 * say which bundle and which entry is wrong, because the other files keep loading and an agent
 * needs to fix the one that failed.
 *
 * <p>Pure. No file access, no Minecraft. The file walking lives in {@link BundleRegistry}.
 */
public final class BundleParser {

    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_STOP_ON_FAILURE = "stopOnFailure";
    private static final String KEY_COMMANDS = "commands";
    private static final String KEY_COMMAND = "command";
    private static final String KEY_DELAY_TICKS = "delayTicks";

    /** Enabled unless the file says otherwise. */
    private static final boolean DEFAULT_STOP_ON_FAILURE = true;

    /**
     * U+FEFF, written by some editors at the start of a UTF-8 file.
     *
     * <p>Compared numerically rather than written as a literal. An invisible character in
     * source is exactly the fragility this constant exists to defend against, and the project
     * has already lost time to one.
     */
    private static final char BYTE_ORDER_MARK = 0xFEFF;

    private BundleParser() {
    }

    /**
     * @return the bundles defined in this file, keyed by name, in declaration order
     * @throws BundleParseException when the file cannot be understood at all
     */
    public static Map<String, BundleDefinition> parse(String json) throws BundleParseException {
        if (json == null) {
            throw new BundleParseException("file is empty");
        }
        json = stripByteOrderMark(json);
        if (json.trim().isEmpty()) {
            throw new BundleParseException("file is empty");
        }

        // Must run before Gson sees the text. Gson's object model is map-backed, so a name
        // repeated at the top level is silently collapsed to the last one and the earlier
        // definition disappears without trace. Copy a bundle block and forget to rename it,
        // and one of them quietly stops existing. A name defined in two files is refused
        // outright; the same mistake inside one file deserves the same treatment.
        rejectDuplicateBundleNames(json);

        JsonElement root;
        try {
            root = new JsonParser().parse(json);
        } catch (JsonSyntaxException e) {
            throw new BundleParseException("invalid JSON: " + rootCauseMessage(e), e);
        } catch (RuntimeException e) {
            throw new BundleParseException("invalid JSON: " + rootCauseMessage(e), e);
        }

        if (root == null || !root.isJsonObject()) {
            throw new BundleParseException(
                    "top level must be a JSON object mapping bundle names to definitions");
        }

        Map<String, BundleDefinition> parsed = new LinkedHashMap<String, BundleDefinition>();
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().entrySet()) {
            String name = entry.getKey();
            if (name == null || name.trim().isEmpty()) {
                throw new BundleParseException("a bundle name is empty");
            }
            parsed.put(name.trim(), parseBundle(name.trim(), entry.getValue()));
        }
        return parsed;
    }

    private static BundleDefinition parseBundle(String name, JsonElement element)
            throws BundleParseException {
        if (element == null || !element.isJsonObject()) {
            throw new BundleParseException("bundle '" + name + "' must be a JSON object");
        }
        JsonObject object = element.getAsJsonObject();

        String description = null;
        if (object.has(KEY_DESCRIPTION) && !object.get(KEY_DESCRIPTION).isJsonNull()) {
            JsonElement value = object.get(KEY_DESCRIPTION);
            if (!isString(value)) {
                throw new BundleParseException(
                        "bundle '" + name + "': " + KEY_DESCRIPTION + " must be a string");
            }
            description = value.getAsString();
        }

        boolean stopOnFailure = DEFAULT_STOP_ON_FAILURE;
        if (object.has(KEY_STOP_ON_FAILURE) && !object.get(KEY_STOP_ON_FAILURE).isJsonNull()) {
            JsonElement value = object.get(KEY_STOP_ON_FAILURE);
            if (!isBoolean(value)) {
                throw new BundleParseException(
                        "bundle '" + name + "': " + KEY_STOP_ON_FAILURE + " must be true or false");
            }
            stopOnFailure = value.getAsBoolean();
        }

        if (!object.has(KEY_COMMANDS)) {
            throw new BundleParseException("bundle '" + name + "': missing " + KEY_COMMANDS);
        }
        JsonElement commandsElement = object.get(KEY_COMMANDS);
        if (!commandsElement.isJsonArray()) {
            throw new BundleParseException(
                    "bundle '" + name + "': " + KEY_COMMANDS + " must be an array");
        }

        JsonArray array = commandsElement.getAsJsonArray();
        List<BundleCommand> commands = new ArrayList<BundleCommand>(array.size());
        for (int i = 0; i < array.size(); i++) {
            commands.add(parseCommand(name, i, array.get(i)));
        }

        return new BundleDefinition(name, description, stopOnFailure, commands);
    }

    /**
     * Accepts either a bare command string or an object carrying a delay.
     *
     * <p>The bare form keeps the common case readable, since most commands have no delay and
     * wrapping every one in an object would triple the size of a typical bundle for nothing.
     */
    private static BundleCommand parseCommand(String bundleName, int index, JsonElement element)
            throws BundleParseException {
        String where = "bundle '" + bundleName + "' command " + index;

        if (element == null || element.isJsonNull()) {
            throw new BundleParseException(where + ": entry is null");
        }

        if (isString(element)) {
            String command = element.getAsString();
            if (command.trim().isEmpty()) {
                throw new BundleParseException(where + ": command is blank");
            }
            return new BundleCommand(command, 0);
        }

        if (!element.isJsonObject()) {
            throw new BundleParseException(
                    where + ": must be a string or an object with a '" + KEY_COMMAND + "' field");
        }

        JsonObject object = element.getAsJsonObject();
        if (!object.has(KEY_COMMAND) || object.get(KEY_COMMAND).isJsonNull()) {
            throw new BundleParseException(where + ": missing " + KEY_COMMAND);
        }
        JsonElement commandValue = object.get(KEY_COMMAND);
        if (!isString(commandValue) || commandValue.getAsString().trim().isEmpty()) {
            throw new BundleParseException(where + ": " + KEY_COMMAND + " must be a non-empty string");
        }

        int delayTicks = 0;
        if (object.has(KEY_DELAY_TICKS) && !object.get(KEY_DELAY_TICKS).isJsonNull()) {
            JsonElement delayValue = object.get(KEY_DELAY_TICKS);
            if (!isNumber(delayValue)) {
                throw new BundleParseException(where + ": " + KEY_DELAY_TICKS + " must be a number");
            }
            delayTicks = delayValue.getAsInt();
            if (delayTicks < 0) {
                throw new BundleParseException(
                        where + ": " + KEY_DELAY_TICKS + " must not be negative, was " + delayTicks);
            }
        }

        return new BundleCommand(commandValue.getAsString(), delayTicks);
    }

    /**
     * Removes a leading byte order mark.
     *
     * <p>Some Windows editors write one, the JSON parser does not skip it, and {@code trim()}
     * will not remove it either because it sits above the space character. The resulting error
     * points at an invisible character on line one. This project has already lost time to a
     * BOM once, in a Java source file, and bundle files are hand edited far more often than
     * source is.
     */
    private static String stripByteOrderMark(String json) {
        return (!json.isEmpty() && json.charAt(0) == BYTE_ORDER_MARK) ? json.substring(1) : json;
    }

    /**
     * Fails when the same bundle name appears more than once at the top level of one file.
     *
     * <p>Streamed rather than checked on the parsed object, because by the time Gson has built
     * its object model the duplicate is already gone.
     */
    private static void rejectDuplicateBundleNames(String json) throws BundleParseException {
        com.google.gson.stream.JsonReader reader =
                new com.google.gson.stream.JsonReader(new java.io.StringReader(json));
        reader.setLenient(true);
        Set<String> seen = new HashSet<String>();
        try {
            if (reader.peek() != com.google.gson.stream.JsonToken.BEGIN_OBJECT) {
                // Not an object. The main parse reports that with a better message.
                return;
            }
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (!seen.add(name)) {
                    throw new BundleParseException("bundle name '" + name
                            + "' is defined more than once in this file");
                }
                reader.skipValue();
            }
            reader.endObject();
        } catch (java.io.IOException e) {
            // Malformed JSON. The main parse reports it with a clearer message.
            return;
        } catch (RuntimeException e) {
            return;
        } finally {
            try {
                reader.close();
            } catch (java.io.IOException ignored) {
                // Reading from a string; nothing to release.
            }
        }
    }

    private static boolean isString(JsonElement element) {
        return element.isJsonPrimitive() && element.getAsJsonPrimitive().isString();
    }

    private static boolean isBoolean(JsonElement element) {
        return element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean();
    }

    private static boolean isNumber(JsonElement element) {
        return element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber();
    }

    private static String rootCauseMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
