package com.mahghuuuls.agenttesttoolkit.inbox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The words of one step on the open screen, parsed and bounded: what the screen shows, a click
 * on a slot, a button or a position, a scroll, and the tooltip of a slot in an open container.
 *
 * <p>Pure, beside {@link PlayerActionOptions} and {@link InputOptions} for the same reason: the
 * command, the inbox's {@code client} verb and the network message's own check read the same
 * words.
 */
public final class ScreenOptions {

    /** Describe the open screen, its buttons and its slots. */
    public static final String SCREEN = "screen";
    /** Click a slot, a button or a position on the open screen. */
    public static final String CLICK = "click";
    /** Turn the mouse wheel over the open screen. */
    public static final String SCROLL = "scroll";
    /** With {@link #CONTAINER}: the tooltip of a slot in the open container. */
    public static final String TOOLTIP = PlayerActionOptions.TOOLTIP;

    /** The steps, in the order the usage text lists them. */
    public static final List<String> STEPS = Collections.unmodifiableList(Arrays.asList(SCREEN, CLICK, SCROLL, TOOLTIP));

    /** The word after {@code tooltip} that makes it this step rather than the inventory view. */
    public static final String CONTAINER = "container";
    /** What a click names. */
    public static final String SLOT = "slot";
    public static final String BUTTON = "button";
    public static final String AT = "at";

    /** The ways a slot is clicked; {@code key=<1-9>} is the fourth, kept as {@link #KEY}. */
    public static final String LEFT = "left";
    public static final String RIGHT = "right";
    public static final String SHIFT = "shift";
    public static final String KEY = "key";

    public static final String OPTION_EXPECT = "expect";
    /** A button named by its label rather than its id. */
    public static final String OPTION_LABEL = "label";

    /** The highest slot number taken; a large modded container stays well below it. */
    public static final int MAX_SLOT = 999;
    /** The farthest position taken, in the screen's GUI units. */
    public static final int MAX_POSITION = 8192;
    /** The most notches one scroll turns, either way. */
    public static final int MAX_SCROLL = 50;
    /** The longest button label. */
    public static final int MAX_LABEL = 128;
    /** The longest screen class name for {@code expect}. */
    public static final int MAX_CLASS_NAME = 200;

    private static final Pattern NUMBER = Pattern.compile("(?:0|[1-9][0-9]{0,3})");
    private static final Pattern SIGNED_NUMBER = Pattern.compile("-?(?:0|[1-9][0-9]{0,9})");
    private static final Pattern HOTBAR_KEY = Pattern.compile("key=[1-9]");
    private static final Pattern CLASS_NAME = Pattern.compile(
            "[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*");

    private final String step;
    /** For a click: {@code slot}, {@code button} or {@code at}; empty otherwise. */
    private String target = "";
    /** The slot for a slot click or a container tooltip. */
    private int slot = -1;
    /** For a slot click: left, right, shift or key; for a click at a position: left or right. */
    private String mouse = LEFT;
    /** For a slot click with a number key: 1 to 9. */
    private int hotbarKey;
    /** For a button: its id, when it is not named by its label. */
    private int buttonId;
    /** For a button: its exact label; empty when named by id. */
    private String label = "";
    private int x;
    private int y;
    private int amount;
    /** For a container tooltip: {@code advanced}, then the modifier keys, comma separated. */
    private String tooltipWords = "";
    /** The screen class the step expects to find open; empty for any. */
    private String expect = "";
    private final StepExtras extras = new StepExtras();

    private ScreenOptions(String step) {
        this.step = step;
    }

    /** True for a word that may start a screen step; {@code tooltip} is one only with {@code container}. */
    public static boolean isStep(String word) {
        return word != null && STEPS.contains(word);
    }

    /**
     * Parses the words after {@code client}. Null when they do not form one screen step,
     * including a {@code tooltip} without {@code container}, which is the inventory view's.
     */
    public static ScreenOptions parse(String[] words) {
        if (words == null || words.length == 0 || !isStep(words[0])) {
            return null;
        }
        ScreenOptions options = new ScreenOptions(words[0]);
        int next = options.readPositional(words);
        if (next < 0) {
            return null;
        }
        Set<String> seen = new HashSet<String>();
        for (int i = next; i < words.length; i++) {
            int eq = words[i].indexOf('=');
            if (eq <= 0) {
                return null;
            }
            String key = words[i].substring(0, eq);
            if (!seen.add(key) || !options.readOption(key, words[i].substring(eq + 1))) {
                return null;
            }
        }
        return options.extras.isValid() ? options : null;
    }

    private int readPositional(String[] words) {
        if (SCREEN.equals(step)) {
            return 1;
        }
        if (SCROLL.equals(step)) {
            if (words.length < 2 || !SIGNED_NUMBER.matcher(words[1]).matches()) {
                return -1;
            }
            long value = Long.parseLong(words[1]);
            if (value == 0 || Math.abs(value) > MAX_SCROLL) {
                return -1;
            }
            amount = (int) value;
            return 2;
        }
        if (TOOLTIP.equals(step)) {
            if (words.length < 3 || !CONTAINER.equals(words[1]) || !isSlot(words[2])) {
                return -1;
            }
            slot = Integer.parseInt(words[2]);
            Set<String> given = new HashSet<String>();
            int next = 3;
            while (next < words.length && PlayerActionOptions.TOOLTIP_WORDS.contains(words[next])) {
                if (!given.add(words[next])) {
                    return -1;
                }
                next++;
            }
            StringBuilder out = new StringBuilder();
            for (String word : PlayerActionOptions.TOOLTIP_WORDS) {
                if (given.contains(word)) {
                    out.append(out.length() == 0 ? "" : ",").append(word);
                }
            }
            tooltipWords = out.toString();
            return next;
        }
        // click
        if (words.length < 2) {
            return -1;
        }
        target = words[1];
        if (SLOT.equals(target)) {
            return readSlotClick(words);
        }
        if (BUTTON.equals(target)) {
            return readButton(words);
        }
        if (AT.equals(target)) {
            if (words.length < 4 || !NUMBER.matcher(words[2]).matches() || !NUMBER.matcher(words[3]).matches()) {
                return -1;
            }
            x = Integer.parseInt(words[2]);
            y = Integer.parseInt(words[3]);
            if (x > MAX_POSITION || y > MAX_POSITION) {
                return -1;
            }
            if (words.length > 4 && (LEFT.equals(words[4]) || RIGHT.equals(words[4]))) {
                mouse = words[4];
                return 5;
            }
            return 4;
        }
        return -1;
    }

    private int readSlotClick(String[] words) {
        if (words.length < 3 || !isSlot(words[2])) {
            return -1;
        }
        slot = Integer.parseInt(words[2]);
        if (words.length == 3) {
            return 3;
        }
        String how = words[3];
        if (LEFT.equals(how) || RIGHT.equals(how) || SHIFT.equals(how)) {
            mouse = how;
            return 4;
        }
        if (HOTBAR_KEY.matcher(how).matches()) {
            mouse = KEY;
            hotbarKey = how.charAt(4) - '0';
            return 4;
        }
        // An option word, or something wrong; the option reader tells which.
        return 3;
    }

    /** {@code button <id>} or {@code button label=<text>}, the label running over several words. */
    private int readButton(String[] words) {
        if (words.length < 3) {
            return -1;
        }
        if (SIGNED_NUMBER.matcher(words[2]).matches()) {
            long id = Long.parseLong(words[2]);
            if (id < Integer.MIN_VALUE || id > Integer.MAX_VALUE) {
                return -1;
            }
            buttonId = (int) id;
            return 3;
        }
        if (!words[2].startsWith(OPTION_LABEL + "=")) {
            return -1;
        }
        StringBuilder text = new StringBuilder(words[2].substring(OPTION_LABEL.length() + 1));
        int next = 3;
        while (next < words.length && !isOptionWord(words[next])) {
            text.append(' ').append(words[next]);
            next++;
        }
        label = text.toString();
        return isLabel(label) ? next : -1;
    }

    /** A word that ends a label written over several words: one of this step's options. */
    private static boolean isOptionWord(String word) {
        int eq = word.indexOf('=');
        if (eq <= 0) {
            return false;
        }
        String key = word.substring(0, eq);
        return OPTION_EXPECT.equals(key) || StepExtras.isKey(key);
    }

    private static boolean isSlot(String word) {
        return NUMBER.matcher(word).matches() && Integer.parseInt(word) <= MAX_SLOT;
    }

    /** One to {@link #MAX_LABEL} characters, none a control character, not only spaces. */
    static boolean isLabel(String text) {
        if (text == null || text.isEmpty() || text.length() > MAX_LABEL || text.trim().isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (Character.isISOControl(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean readOption(String key, String value) {
        if (SCREEN.equals(step)) {
            // Describing the screen acts on nothing, so it takes no wait and no picture.
            return false;
        }
        if (OPTION_EXPECT.equals(key)) {
            if (!(CLICK.equals(step) || SCROLL.equals(step)) || value.length() > MAX_CLASS_NAME
                    || !CLASS_NAME.matcher(value).matches()) {
                return false;
            }
            expect = value;
            return true;
        }
        return extras.take(key, value) == StepExtras.Take.TAKEN;
    }

    /** The words that {@link #parse} reads back into the same step, options in a fixed order. */
    public List<String> toWords() {
        List<String> words = new ArrayList<String>();
        words.add(step);
        if (SCROLL.equals(step)) {
            words.add(String.valueOf(amount));
        } else if (TOOLTIP.equals(step)) {
            words.add(CONTAINER);
            words.add(String.valueOf(slot));
            if (!tooltipWords.isEmpty()) {
                words.addAll(Arrays.asList(tooltipWords.split(",")));
            }
        } else if (CLICK.equals(step)) {
            words.add(target);
            if (SLOT.equals(target)) {
                words.add(String.valueOf(slot));
                if (KEY.equals(mouse)) {
                    words.add(KEY + "=" + hotbarKey);
                } else if (!LEFT.equals(mouse)) {
                    words.add(mouse);
                }
            } else if (BUTTON.equals(target)) {
                words.add(label.isEmpty() ? String.valueOf(buttonId) : OPTION_LABEL + "=" + label);
            } else {
                words.add(String.valueOf(x));
                words.add(String.valueOf(y));
                if (!LEFT.equals(mouse)) {
                    words.add(mouse);
                }
            }
        }
        if (!expect.isEmpty()) {
            words.add(OPTION_EXPECT + "=" + expect);
        }
        extras.addWords(words);
        return words;
    }

    /** Whether a dead player cannot take this step; describing the screen changes nothing. */
    public static boolean needsLivingPlayer(String step) {
        return !SCREEN.equals(step);
    }

    public String getStep() {
        return step;
    }

    /** For a click: {@code slot}, {@code button} or {@code at}; empty otherwise. */
    public String getTarget() {
        return target;
    }

    /** The slot of a slot click or a container tooltip; -1 otherwise. */
    public int getSlot() {
        return slot;
    }

    /** {@code left}, {@code right}, {@code shift} or {@code key}. */
    public String getMouse() {
        return mouse;
    }

    /** The hotbar key, 1 to 9, for a slot click with {@code key=}; 0 otherwise. */
    public int getHotbarKey() {
        return hotbarKey;
    }

    public int getButtonId() {
        return buttonId;
    }

    /** The button's exact label; empty when it is named by id. */
    public String getLabel() {
        return label;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    /** Notches to scroll; positive is away from the player, as the wheel's own sign. */
    public int getAmount() {
        return amount;
    }

    /** For a container tooltip: the advanced (F3+H) form. */
    public boolean isAdvanced() {
        return PlayerActionOptions.advancedOf(tooltipWords);
    }

    /** For a container tooltip: the modifier keys, {@code shift,ctrl,alt} in that order; empty for none. */
    public String modifierKeys() {
        return PlayerActionOptions.modifierKeysOf(tooltipWords);
    }

    /** The screen class the step expects to find open; empty for any. */
    public String getExpect() {
        return expect;
    }

    public int getDelay() {
        return extras.delay;
    }

    public String getScreenshot() {
        return extras.screenshot;
    }

    public int getScreenshotDelay() {
        return extras.screenshotDelay;
    }

    public String getScreenshotHide() {
        return extras.screenshotHide;
    }
}
