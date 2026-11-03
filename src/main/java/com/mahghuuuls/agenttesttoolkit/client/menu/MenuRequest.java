package com.mahghuuuls.agenttesttoolkit.client.menu;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * One request line of the client request channel, parsed (D22, REQ-216, ARC-028).
 *
 * <p>Pure: no Minecraft class, so the rule deciding what the channel may do is unit-tested
 * (rule 18). Only the four menu steps exist; anything else is refused before the client acts.
 * The arguments of {@code create} are checked by {@link WorldOptions}.
 */
public final class MenuRequest {

    public static final String CREATE = "create";
    public static final String OPEN = "open";
    public static final String CONNECT = "connect";
    public static final String QUIT = "quit";

    public static final String REASON_MALFORMED = "malformed";
    public static final String REASON_VERB_NOT_ALLOWED = "verbNotAllowed";
    public static final String REASON_BAD_ARGUMENTS = "badArguments";

    public static final int MAX_LINE_LENGTH = 512;

    /** A new world's name, which is also its folder: letters, digits, `_` and `-`, at most 32, as the screen allows. */
    static final Pattern WORLD_NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    /** An existing save folder: also single spaces between words, as the default `New World` has. */
    static final Pattern SAVE_FOLDER = Pattern.compile("[A-Za-z0-9_-]+(?: [A-Za-z0-9_-]+)*");
    /** A host, an IPv4 address or a bracketed IPv6 address, with an optional port. */
    static final Pattern ADDRESS = Pattern.compile(
            "(?:[A-Za-z0-9.-]{1,253}|\\[[0-9A-Fa-f:.%A-Za-z]{2,64}\\])(?::[0-9]{1,5})?");

    private final String id;
    private final String line;
    private final String step;
    private final List<String> args;
    private final String refusal;
    private final String detail;

    private MenuRequest(String id, String line, String step, List<String> args, String refusal, String detail) {
        this.id = id;
        this.line = line;
        this.step = step;
        this.args = args;
        this.refusal = refusal;
        this.detail = detail;
    }

    public static MenuRequest parse(String id, String content) {
        if (content == null) {
            return refused(id, null, null, REASON_MALFORMED);
        }
        String text = content.startsWith("﻿") ? content.substring(1) : content;
        if (text.endsWith("\r\n")) {
            text = text.substring(0, text.length() - 2);
        } else if (text.endsWith("\n")) {
            text = text.substring(0, text.length() - 1);
        }
        String line = text.trim();
        if (line.isEmpty() || line.length() > MAX_LINE_LENGTH || !isPrintableAscii(text)) {
            return refused(id, line.length() > 80 ? line.substring(0, 80) : line, null, REASON_MALFORMED);
        }
        String[] words = line.split(" +");
        String verb = words[0].toLowerCase(Locale.ROOT);
        List<String> args = Collections.unmodifiableList(Arrays.asList(Arrays.copyOfRange(words, 1, words.length)));
        if (QUIT.equals(verb)) {
            return args.isEmpty() ? accepted(id, line, verb, args) : refused(id, line, verb, REASON_BAD_ARGUMENTS);
        }
        if (OPEN.equals(verb)) {
            // The folder is the rest of the line, so a name with spaces can be opened.
            String folder = String.join(" ", args);
            return !args.isEmpty() && folder.length() <= 64 && SAVE_FOLDER.matcher(folder).matches()
                    ? accepted(id, line, verb, Collections.singletonList(folder))
                    : refused(id, line, verb, REASON_BAD_ARGUMENTS);
        }
        if (CONNECT.equals(verb)) {
            return args.size() == 1 && ADDRESS.matcher(args.get(0)).matches()
                    ? accepted(id, line, verb, args) : refused(id, line, verb, REASON_BAD_ARGUMENTS);
        }
        if (CREATE.equals(verb)) {
            if (args.isEmpty() || !WORLD_NAME.matcher(args.get(0)).matches()) {
                return refused(id, line, verb, REASON_BAD_ARGUMENTS);
            }
            WorldOptions options = WorldOptions.parse(args.subList(1, args.size()));
            return options.isValid()
                    ? accepted(id, line, verb, args)
                    : new MenuRequest(id, line, verb, Collections.<String>emptyList(), REASON_BAD_ARGUMENTS,
                            options.getRefusal());
        }
        return refused(id, line, null, REASON_VERB_NOT_ALLOWED);
    }

    private static MenuRequest accepted(String id, String line, String step, List<String> args) {
        return new MenuRequest(id, line, step, args, null, null);
    }

    private static MenuRequest refused(String id, String line, String step, String reason) {
        return new MenuRequest(id, line, step, Collections.<String>emptyList(), reason, null);
    }

    private static boolean isPrintableAscii(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x20 || c > 0x7E) {
                return false;
            }
        }
        return true;
    }

    public boolean isAccepted() {
        return refusal == null;
    }

    public String getId() {
        return id;
    }

    /** The request line, or null when it could not be read. */
    public String getLine() {
        return line;
    }

    /** The step, or null when the line named none of the four. */
    public String getStep() {
        return step;
    }

    public List<String> getArgs() {
        return args;
    }

    public String getRefusal() {
        return refusal;
    }

    /** What exactly was wrong with a refused {@code create}, such as {@code unknownMode:survivor}. */
    public String getDetail() {
        return detail;
    }
}
