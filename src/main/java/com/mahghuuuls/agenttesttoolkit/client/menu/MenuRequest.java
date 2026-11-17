package com.mahghuuuls.agenttesttoolkit.client.menu;

import com.mahghuuuls.agenttesttoolkit.inbox.HideSet;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * One request line of the client request channel, parsed.
 *
 * <p>Pure: no Minecraft or Forge class, so the rule deciding what a file dropped into the folder
 * may make the client do is unit-tested without a game. Only the channel's own steps exist;
 * anything else is refused before the client acts. The arguments of {@code create} are checked
 * by {@link WorldOptions}, and what a picture leaves out by {@link HideSet}.
 */
public final class MenuRequest {

    public static final String CREATE = "create";
    public static final String OPEN = "open";
    public static final String CONNECT = "connect";
    public static final String QUIT = "quit";
    /** Describe the open screen: its buttons and slots. */
    public static final String SCREEN = "screen";
    /** Close a screen that pauses single player, as Esc does; the one step taken in a world. */
    public static final String CLOSE = "close";
    /** Save a picture of the menu on screen. */
    public static final String SCREENSHOT = "screenshot";
    /** Show one mod's entry in the Mods list, and picture it when asked. */
    public static final String MODS = "mods";

    /** {@code open}'s option: what to do when the world asks a question as it opens. */
    public static final String OPTION_ON_QUESTION = "onQuestion";
    /** End the step and leave the question for a person; the default. */
    public static final String REPORT = "report";
    /** Answer yes, as the question's own button does. */
    public static final String CONFIRM = "confirm";
    /** Answer no, as the question's own button does. */
    public static final String CANCEL = "cancel";
    static final List<String> ANSWERS = Collections.unmodifiableList(Arrays.asList(REPORT, CONFIRM, CANCEL));
    /** {@code screenshot}'s and {@code mods}' option: what the picture leaves out. */
    public static final String OPTION_HIDE = "hide";
    /** {@code mods}' option: the label of a picture of the entry. */
    public static final String OPTION_SCREENSHOT = "screenshot";

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

    /** A screen's class name, as Java writes it, nested classes with {@code $}. */
    static final Pattern CLASS_NAME = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*");
    static final int MAX_CLASS_NAME = 200;
    /** A screenshot label: a file name on this computer, so no path characters. */
    static final Pattern LABEL = Pattern.compile("[A-Za-z0-9_-]{1,48}");
    /** A mod id as a mod declares it. */
    static final Pattern MOD_ID = Pattern.compile("[A-Za-z0-9_.-]{1,64}");

    private final String id;
    private final String line;
    private final String step;
    private final List<String> args;
    private final String refusal;
    private final String detail;
    /** {@code open}: what to do at a question; {@code report} by default. */
    private String onQuestion = REPORT;
    /** {@code screenshot}, {@code mods}: what the picture leaves out, written one way; empty for nothing. */
    private String hide = "";
    /** {@code mods}: the picture's label; empty for none. */
    private String screenshot = "";

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
            // The folder is the rest of the line, so a name with spaces can be opened; the
            // answer to a question, when given, is the last word.
            String answer = REPORT;
            List<String> folderWords = args;
            if (!folderWords.isEmpty() && folderWords.get(folderWords.size() - 1).startsWith(OPTION_ON_QUESTION + "=")) {
                answer = folderWords.get(folderWords.size() - 1).substring(OPTION_ON_QUESTION.length() + 1);
                folderWords = folderWords.subList(0, folderWords.size() - 1);
                if (!ANSWERS.contains(answer)) {
                    return refused(id, line, verb, REASON_BAD_ARGUMENTS);
                }
            }
            String folder = String.join(" ", folderWords);
            if (folderWords.isEmpty() || folder.length() > 64 || !SAVE_FOLDER.matcher(folder).matches()) {
                return refused(id, line, verb, REASON_BAD_ARGUMENTS);
            }
            MenuRequest request = accepted(id, line, verb, Collections.singletonList(folder));
            request.onQuestion = answer;
            return request;
        }
        if (SCREEN.equals(verb)) {
            return args.isEmpty() ? accepted(id, line, verb, args) : refused(id, line, verb, REASON_BAD_ARGUMENTS);
        }
        if (CLOSE.equals(verb)) {
            return args.size() == 1 && args.get(0).length() <= MAX_CLASS_NAME
                    && CLASS_NAME.matcher(args.get(0)).matches()
                    ? accepted(id, line, verb, args) : refused(id, line, verb, REASON_BAD_ARGUMENTS);
        }
        if (SCREENSHOT.equals(verb)) {
            if (args.isEmpty() || !LABEL.matcher(args.get(0)).matches()) {
                return refused(id, line, verb, REASON_BAD_ARGUMENTS);
            }
            return withOptions(id, line, verb, args, false);
        }
        if (MODS.equals(verb)) {
            if (args.isEmpty() || !MOD_ID.matcher(args.get(0)).matches()) {
                return refused(id, line, verb, REASON_BAD_ARGUMENTS);
            }
            return withOptions(id, line, verb, args, true);
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

    /**
     * The options after the first argument of {@code screenshot} ({@code hide=}) and {@code mods}
     * ({@code screenshot=}, {@code hide=}), each at most once; for {@code mods} what to leave out
     * of a picture only together with a picture.
     */
    private static MenuRequest withOptions(String id, String line, String verb, List<String> args, boolean mods) {
        String hide = "";
        String label = "";
        Set<String> seen = new HashSet<String>();
        for (String word : args.subList(1, args.size())) {
            int eq = word.indexOf('=');
            String key = eq <= 0 ? "" : word.substring(0, eq);
            String value = eq <= 0 ? "" : word.substring(eq + 1);
            if (!seen.add(key)) {
                return refused(id, line, verb, REASON_BAD_ARGUMENTS);
            }
            if (OPTION_HIDE.equals(key)) {
                hide = HideSet.canonical(value);
                if (hide == null || hide.isEmpty()) {
                    return refused(id, line, verb, REASON_BAD_ARGUMENTS);
                }
            } else if (mods && OPTION_SCREENSHOT.equals(key) && LABEL.matcher(value).matches()) {
                label = value;
            } else {
                return refused(id, line, verb, REASON_BAD_ARGUMENTS);
            }
        }
        if (mods && !hide.isEmpty() && label.isEmpty()) {
            return refused(id, line, verb, REASON_BAD_ARGUMENTS);
        }
        MenuRequest request = accepted(id, line, verb, Collections.singletonList(args.get(0)));
        request.hide = hide;
        request.screenshot = label;
        return request;
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

    /** The step, or null when the line named none of the channel's steps. */
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

    /** {@code open}: {@code report}, {@code confirm} or {@code cancel}. */
    public String getOnQuestion() {
        return onQuestion;
    }

    /** {@code screenshot}, {@code mods}: what the picture leaves out, a comma list in a fixed order; empty for nothing. */
    public String getHide() {
        return hide;
    }

    /** {@code mods}: the picture's label; empty for no picture. */
    public String getScreenshot() {
        return screenshot;
    }
}
