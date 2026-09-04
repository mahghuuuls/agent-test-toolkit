package com.mahghuuuls.agenttesttoolkit.bundle;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The fixed list of values a bundle command may name instead of spelling out.
 *
 * <p>Four forms and nothing else: {@code ${operator.name}}, {@code ${operator.uuid}},
 * {@code ${operator.selector}}, and {@code ${fixture.<handle>.uuid}}. This is a lookup, not
 * a language. There is no default value, no nesting, no expression, and the boundary is
 * deliberate: the moment a placeholder can compute something, a bundle stops being a list
 * of commands a reader can trust at a glance.
 *
 * <p>Two moments, two jobs. At parse time {@link #validate} rejects any {@code ${...}} that
 * is not one of the four forms, so a typo fails the file at reload rather than mid-test. At
 * dispatch time {@link #substitute} fills in values from a {@link PlaceholderSource}, and a
 * value that is missing or unsafe fails the command with a named key. Substitution cannot
 * happen at parse time, because the operator and the fixtures do not exist yet.
 *
 * <p>Values are checked, not only names. A name, a UUID, or a selector never contains a
 * space, a quote, or a brace, so a value that does is refused outright. That is what makes
 * {@code CustomName:"${operator.name}"} safe by rule rather than by luck.
 */
public final class Placeholders {

    private static final Pattern TOKEN = Pattern.compile("\\$\\{([^}]*)\\}");
    private static final Pattern HANDLE = Pattern.compile("[A-Za-z0-9_.\\-]+");

    private Placeholders() {
    }

    /** True when the text contains at least one {@code ${...}} token. Cheap pre-check. */
    public static boolean containsAny(String command) {
        return command != null && command.indexOf("${") >= 0;
    }

    /**
     * Names every placeholder that is not one of the allowed forms.
     *
     * @return problems, one per bad token, in order; empty when the command is acceptable
     */
    public static List<String> validate(String command) {
        List<String> problems = new ArrayList<String>();
        if (!containsAny(command)) {
            return problems;
        }
        Matcher matcher = TOKEN.matcher(command);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!isKnown(name)) {
                problems.add("unknown placeholder ${" + name + "}");
            }
        }
        return problems;
    }

    /**
     * Fills in every placeholder from the source.
     *
     * @throws PlaceholderException when a value is unavailable or unsafe. The command must
     *                              not run; nothing is substituted partially
     */
    public static String substitute(String command, PlaceholderSource source)
            throws PlaceholderException {
        if (!containsAny(command)) {
            return command;
        }
        if (source == null) {
            source = PlaceholderSource.EMPTY;
        }
        Matcher matcher = TOKEN.matcher(command);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            String token = "${" + name + "}";
            String value = lookup(name, source);
            if (value == null) {
                throw new PlaceholderException(PlaceholderException.KEY_UNBOUND, token,
                        "Placeholder " + token + " has no value: nothing is bound for it.");
            }
            if (!isSafe(value)) {
                throw new PlaceholderException(PlaceholderException.KEY_UNSAFE, token,
                        "Placeholder " + token + " has a value with whitespace, a quote or a brace, "
                                + "which is never substituted.");
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** Whether a name is one of the four allowed forms. */
    static boolean isKnown(String name) {
        if ("operator.name".equals(name) || "operator.uuid".equals(name)
                || "operator.selector".equals(name)) {
            return true;
        }
        String handle = fixtureHandle(name);
        return handle != null && HANDLE.matcher(handle).matches();
    }

    /** The handle in {@code fixture.<handle>.uuid}, or null when the name is not that form. */
    static String fixtureHandle(String name) {
        if (name == null || !name.startsWith("fixture.") || !name.endsWith(".uuid")) {
            return null;
        }
        String handle = name.substring("fixture.".length(), name.length() - ".uuid".length());
        return handle.isEmpty() ? null : handle;
    }

    private static String lookup(String name, PlaceholderSource source) {
        if ("operator.name".equals(name)) {
            return source.operatorName();
        }
        if ("operator.uuid".equals(name)) {
            return source.operatorUuid();
        }
        if ("operator.selector".equals(name)) {
            return source.operatorSelector();
        }
        String handle = fixtureHandle(name);
        return handle == null ? null : source.fixtureUuid(handle);
    }

    /** A value is safe when it cannot end an argument or break a quoted or braced context. */
    static boolean isSafe(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isWhitespace(c) || c == '"' || c == '\'' || c == '{' || c == '}') {
                return false;
            }
        }
        return !value.isEmpty();
    }
}
