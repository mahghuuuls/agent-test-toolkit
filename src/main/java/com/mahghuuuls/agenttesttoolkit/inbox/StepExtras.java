package com.mahghuuuls.agenttesttoolkit.inbox;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The options every acting client step shares: a wait before it acts, and a screenshot after it.
 * The one owner of their keys, their words and their bounds, so no step can accept a value
 * another refuses.
 */
final class StepExtras {

    /** {@code delay=}: ticks the client waits before it acts. */
    static final String DELAY = "delay";
    /** {@code screenshot=}: the label of a screenshot taken after the step. */
    static final String SCREENSHOT = "screenshot";
    /** {@code screenshotDelay=}: ticks from the step to that screenshot. */
    static final String SCREENSHOT_DELAY = "screenshotDelay";
    /** {@code screenshotHide=}: what is left out of that screenshot, a set as {@link HideSet} reads it. */
    static final String SCREENSHOT_HIDE = "screenshotHide";
    /** The keys above, in the order their words are written. */
    static final List<String> KEYS = Collections.unmodifiableList(
            Arrays.asList(DELAY, SCREENSHOT, SCREENSHOT_DELAY, SCREENSHOT_HIDE));

    /** The longest wait before a step, in ticks: one minute. */
    static final int MAX_DELAY_TICKS = 1200;
    /** The longest wait from a step to its screenshot, in ticks: ten seconds. */
    static final int MAX_SCREENSHOT_DELAY_TICKS = 200;

    /**
     * A screenshot label: the screenshot command's rule, letters, digits, {@code _} and {@code -},
     * since it becomes a file name on the player's computer.
     */
    static final Pattern SCREENSHOT_LABEL = Pattern.compile("[A-Za-z0-9_-]{1,48}");
    /** 0 to 9999 without leading zeros; the bounds above are checked after. */
    static final Pattern TICKS = Pattern.compile("(?:0|[1-9][0-9]{0,3})");

    /** What one {@code key=value} word was to these options. */
    enum Take { TAKEN, NOT_MINE, BAD }

    int delay;
    String screenshot = "";
    int screenshotDelay;
    String screenshotHide = "";

    /** Whether a word's key is one of these options. */
    static boolean isKey(String key) {
        return KEYS.contains(key);
    }

    /** Reads one option word's key and value; a repeated key is the caller's to refuse. */
    Take take(String key, String value) {
        if (DELAY.equals(key)) {
            if (!TICKS.matcher(value).matches()) {
                return Take.BAD;
            }
            delay = Integer.parseInt(value);
            return Take.TAKEN;
        }
        if (SCREENSHOT.equals(key)) {
            if (!SCREENSHOT_LABEL.matcher(value).matches()) {
                return Take.BAD;
            }
            screenshot = value;
            return Take.TAKEN;
        }
        if (SCREENSHOT_DELAY.equals(key)) {
            if (!TICKS.matcher(value).matches()) {
                return Take.BAD;
            }
            screenshotDelay = Integer.parseInt(value);
            return Take.TAKEN;
        }
        if (SCREENSHOT_HIDE.equals(key)) {
            String set = HideSet.canonical(value);
            if (set == null || set.isEmpty()) {
                return Take.BAD;
            }
            screenshotHide = set;
            return Take.TAKEN;
        }
        return Take.NOT_MINE;
    }

    /** In bounds, and what to leave out of a picture only together with a picture. */
    boolean isValid() {
        return isValid(delay, screenshot, screenshotDelay) && (screenshotHide.isEmpty() || !screenshot.isEmpty());
    }

    /** Whether the values are in bounds; a screenshot delay needs a screenshot. */
    static boolean isValid(int delay, String screenshot, int screenshotDelay) {
        return delay >= 0 && delay <= MAX_DELAY_TICKS && screenshot != null
                && (screenshot.isEmpty() || SCREENSHOT_LABEL.matcher(screenshot).matches())
                && screenshotDelay >= 0 && screenshotDelay <= MAX_SCREENSHOT_DELAY_TICKS
                && (!screenshot.isEmpty() || screenshotDelay == 0);
    }

    /** The options as words, in a fixed order, leaving out what was not given. */
    void addWords(List<String> words) {
        if (delay != 0) {
            words.add(DELAY + "=" + delay);
        }
        if (!screenshot.isEmpty()) {
            words.add(SCREENSHOT + "=" + screenshot);
        }
        if (screenshotDelay != 0) {
            words.add(SCREENSHOT_DELAY + "=" + screenshotDelay);
        }
        if (!screenshotHide.isEmpty()) {
            words.add(SCREENSHOT_HIDE + "=" + screenshotHide);
        }
    }
}
