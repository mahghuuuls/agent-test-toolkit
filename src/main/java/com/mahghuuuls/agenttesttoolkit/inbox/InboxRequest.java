package com.mahghuuuls.agenttesttoolkit.inbox;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * One request from the agent, parsed and checked against the allow-list.
 *
 * <p>This class is the inbox's security boundary, and the only place that decides what may
 * run. A request names one toolkit verb; anything else is refused before it can meet a command
 * manager. The handler receives the normalized command line this class built and never sees
 * the raw text, so a check elsewhere cannot be skipped or contradicted.
 *
 * <p>What a request cannot do, by construction: run a vanilla command, run another mod's
 * command, change the world except through a bundle the agent already wrote and the owner can
 * read, or carry more than one command. Bundles are the reviewable unit; the inbox only starts
 * them.
 */
public final class InboxRequest {

    /** Refused because the file was not one well-formed line. */
    public static final String REASON_MALFORMED = "malformed";
    /** Refused because the first word is not an allowed verb. */
    public static final String REASON_VERB_NOT_ALLOWED = "verbNotAllowed";
    /** Refused because the verb is allowed but its arguments are not. */
    public static final String REASON_BAD_ARGUMENTS = "badArguments";

    /** Longest accepted request line. A real request is a few dozen characters. */
    public static final int MAX_LINE_LENGTH = 512;

    /** Verbs that take no argument. */
    private static final Set<String> NO_ARGUMENT = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList("reload", "save")));
    /** Verbs whose arguments are passed through to the toolkit subcommand of the same name. */
    private static final Set<String> WITH_ARGUMENTS = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList("mark", "inspect", "entities", "nbt")));

    private static final Pattern BUNDLE_NAME = Pattern.compile("[A-Za-z0-9_.\\-]{1,128}");
    /** A screenshot label: the file name on the operator's computer, so no path characters. */
    private static final Pattern SCREENSHOT_LABEL = Pattern.compile("[A-Za-z0-9_-]{1,48}");
    /** 0 to 600, the command's own bounds. */
    /** A reconnect's away time, 0 to 60 seconds. */
    private static final Pattern AWAY = Pattern.compile("(?:[0-9]|[1-5][0-9]|60)");
    private static final Pattern DELAY = Pattern.compile("[0-9]|[1-9][0-9]|[1-5][0-9][0-9]|600");
    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9_.\\-]{1,64}");

    private final String id;
    private final String line;
    private final String verb;
    private final String commandLine;
    private final String refusal;
    /** What a refusal adds for the writer, such as the accepted form; null for none. */
    private final String detail;

    private InboxRequest(String id, String line, String verb, String commandLine, String refusal, String detail) {
        this.id = id;
        this.line = line;
        this.verb = verb;
        this.commandLine = commandLine;
        this.refusal = refusal;
        this.detail = detail;
    }

    /** True when a file name stem may be a request id. Anything else is ignored, not refused. */
    public static boolean isValidId(String id) {
        return id != null && REQUEST_ID.matcher(id).matches();
    }

    /**
     * Parses a request file's content.
     *
     * @param id      the file's name without its extension, already checked by {@link #isValidId}
     * @param content the whole file, as read
     */
    public static InboxRequest parse(String id, String content) {
        if (content == null) {
            return refused(id, null, null, REASON_MALFORMED);
        }
        // A byte order mark is what Windows tools put in front of UTF-8; not part of the line.
        String text = content.startsWith("﻿") ? content.substring(1) : content;
        // One line, optionally followed by one line break; anything after it is a second line.
        if (text.endsWith("\r\n")) {
            text = text.substring(0, text.length() - 2);
        } else if (text.endsWith("\n")) {
            text = text.substring(0, text.length() - 1);
        }
        String line = text.trim();
        if (line.isEmpty() || line.length() > MAX_LINE_LENGTH || !isPrintableAscii(text)) {
            return refused(id, shorten(line), null, REASON_MALFORMED);
        }
        if (line.startsWith("/")) {
            line = line.substring(1).trim();
        }

        String[] words = line.split(" +");
        String verb = words[0].toLowerCase(Locale.ROOT);
        String[] args = Arrays.copyOfRange(words, 1, words.length);

        if ("run".equals(verb)) {
            if (args.length != 1 || !BUNDLE_NAME.matcher(args[0]).matches()) {
                return refused(id, line, verb, REASON_BAD_ARGUMENTS);
            }
            return accepted(id, line, verb, "devtool run " + args[0]);
        }
        if ("screenshot".equals(verb)) {
            // screenshot <label> [delayTicks], to the operator only: no player argument here.
            if (args.length < 1 || args.length > 2 || !SCREENSHOT_LABEL.matcher(args[0]).matches()
                    || (args.length == 2 && !DELAY.matcher(args[1]).matches())) {
                return refused(id, line, verb, REASON_BAD_ARGUMENTS);
            }
            return accepted(id, line, verb, "devtool screenshot " + join(args));
        }
        if ("client".equals(verb)) {
            // client reconnect [awaySeconds] | respawn | close | quit, or a player action, to the
            // operator only.
            boolean ok = args.length >= 1
                    && (("reconnect".equals(args[0]) && isReconnectArguments(args))
                    || (("respawn".equals(args[0]) || "close".equals(args[0]) || "quit".equals(args[0]))
                            && args.length == 1)
                    || PlayerActionOptions.parse(args) != null);
            if (!ok) {
                return refused(id, line, verb, REASON_BAD_ARGUMENTS);
            }
            return accepted(id, line, verb, "devtool client " + join(args));
        }
        if (NO_ARGUMENT.contains(verb)) {
            if (args.length != 0) {
                return refused(id, line, verb, REASON_BAD_ARGUMENTS);
            }
            return accepted(id, line, verb, "devtool " + verb);
        }
        if (WITH_ARGUMENTS.contains(verb)) {
            if (args.length == 0) {
                return refused(id, line, verb, REASON_BAD_ARGUMENTS);
            }
            return accepted(id, line, verb, "devtool " + verb + " " + join(args));
        }
        if ("devtool".equals(verb) && args.length > 0) {
            // The inbox adds the root itself; the same line without it is what was meant.
            return new InboxRequest(id, line, verb, null, REASON_VERB_NOT_ALLOWED, "use:" + join(args));
        }
        return refused(id, line, verb, REASON_VERB_NOT_ALLOWED);
    }

    /** After {@code reconnect}: an optional away time, then an optional {@code logout}, in that order. */
    private static boolean isReconnectArguments(String[] args) {
        int next = 1;
        if (args.length > next && AWAY.matcher(args[next]).matches()) {
            next++;
        }
        if (args.length > next && "logout".equals(args[next])) {
            next++;
        }
        return args.length == next;
    }

    /** Printable ASCII only, so no control character, tab, or second line can hide in a request. */
    private static boolean isPrintableAscii(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x20 || c > 0x7E) {
                return false;
            }
        }
        return true;
    }

    private static String join(String[] args) {
        StringBuilder out = new StringBuilder();
        for (String arg : args) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(arg);
        }
        return out.toString();
    }

    private static String shorten(String line) {
        if (line == null) {
            return null;
        }
        return line.length() <= 80 ? line : line.substring(0, 80);
    }

    private static InboxRequest accepted(String id, String line, String verb, String commandLine) {
        return new InboxRequest(id, line, verb, commandLine, null, null);
    }

    private static InboxRequest refused(String id, String line, String verb, String reason) {
        return new InboxRequest(id, line, verb, null, reason, null);
    }

    public String getId() {
        return id;
    }

    /** For a refusal, what the writer may need, such as {@code use:client respawn}; null for none. */
    public String getDetail() {
        return detail;
    }

    /** The request line as written, trimmed, or a shortened form when it was malformed. */
    public String getLine() {
        return line;
    }

    /** The first word, lower-cased; null when the request was malformed. */
    public String getVerb() {
        return verb;
    }

    public boolean isAccepted() {
        return refusal == null;
    }

    /** The command line to run, always beginning with {@code devtool }; null when refused. */
    public String getCommandLine() {
        return commandLine;
    }

    /** Why it was refused, one of the {@code REASON_} constants; null when accepted. */
    public String getRefusal() {
        return refusal;
    }
}
