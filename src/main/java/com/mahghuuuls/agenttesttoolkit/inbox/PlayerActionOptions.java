package com.mahghuuuls.agenttesttoolkit.inbox;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The words of one player action, parsed and bounded: which step, and its argument.
 *
 * <p>Pure, so the bounds are tested without a game. It lives here, beside the inbox's own
 * request parser, because three places read the same words: the command, the inbox's
 * {@code client} verb, and the network message's own check, and the network package may use
 * this package while this package may not use the network one.
 */
public final class PlayerActionOptions {

    /** Make a hotbar slot the held one. */
    public static final String SELECT = "select";
    /** One right click with the held item. */
    public static final String USE = "use";
    /** One left click. */
    public static final String ATTACK = "attack";
    /** Keep the use or attack input pressed for a number of ticks, then release it. */
    public static final String HOLD = "hold";
    /** Drop the held item, or with {@code all} the whole held stack. */
    public static final String DROP = "drop";
    /** Open the player's own inventory, as the inventory key does. */
    public static final String INVENTORY = "inventory";
    /** Show the tooltip of the item in an inventory slot, in a read-only view of the inventory. */
    public static final String TOOLTIP = "tooltip";
    /** The word after the tooltip's slot that asks for the advanced (F3+H) tooltip. */
    public static final String ADVANCED = "advanced";
    /**
     * The tooltip's words after its slot, in the order the argument keeps them: the advanced
     * form, then the modifier keys read as held while it is built and drawn.
     */
    public static final List<String> TOOLTIP_WORDS = Collections.unmodifiableList(
            Arrays.asList(ADVANCED, "shift", "ctrl", "alt"));
    /** The last slot of the player's inventory: 0 to 8 hotbar, 9 to 35 main, 36 to 39 armour, 40 off hand. */
    public static final int MAX_INVENTORY_SLOT = 40;
    /** How long the tooltip view stays open when not told, in ticks: one second. */
    public static final int DEFAULT_SHOW_TICKS = 20;

    /** The steps, in the order the usage text lists them. */
    public static final List<String> STEPS = Collections.unmodifiableList(
            Arrays.asList(SELECT, USE, ATTACK, HOLD, DROP, INVENTORY, TOOLTIP));

    /** The last hotbar slot; the hotbar has nine, counted from 0. */
    public static final int MAX_HOTBAR_SLOT = 8;
    /** The longest hold, in ticks: one minute. */
    public static final int MAX_HOLD_TICKS = 1200;

    /** No slot: the step takes none. */
    public static final int NO_SLOT = -1;
    /** No ticks: the step is not a hold. */
    public static final int NO_TICKS = 0;
    /** The word after {@code drop} that drops the whole stack. */
    public static final String ALL = "all";

    /** The longest wait before an action, in ticks: one minute. */
    public static final int MAX_DELAY_TICKS = 1200;
    /** The longest wait from an action to its screenshot, in ticks: ten seconds. */
    public static final int MAX_SCREENSHOT_DELAY_TICKS = 200;

    private static final Pattern HOTBAR_SLOT = Pattern.compile("[0-8]");
    private static final Pattern INVENTORY_SLOT = Pattern.compile("(?:[0-9]|[1-3][0-9]|40)");
    /**
     * A screenshot label: the screenshot command's rule, letters, digits, {@code _} and {@code -},
     * since it becomes a file name on the player's computer.
     */
    private static final Pattern SCREENSHOT_LABEL = Pattern.compile("[A-Za-z0-9_-]{1,48}");
    /** 0 to 1200 for a delay, 0 to 200 for a screenshot delay; no leading zeros. */
    private static final Pattern TICKS = Pattern.compile("(?:0|[1-9][0-9]{0,3})");
    /** 1 to 1200, without leading zeros. */
    private static final Pattern HOLD_TICKS = Pattern.compile("(?:[1-9][0-9]{0,2}|1[01][0-9]{2}|1200)");

    private final String step;
    private final int slot;
    /** For a hold, the input held ({@code use} or {@code attack}); for a drop, {@code all} or empty. */
    private final String argument;
    /** For a hold, the ticks held; for a tooltip, the ticks the view stays open. */
    private int ticks;
    private int delay;
    private String screenshot = "";
    private int screenshotDelay;
    private String screenshotHide = "";

    private PlayerActionOptions(String step, int slot, String argument, int ticks) {
        this.step = step;
        this.slot = slot;
        this.argument = argument;
        this.ticks = ticks;
    }

    /** True for a player-action step, written in lower case. */
    public static boolean isStep(String word) {
        return word != null && STEPS.contains(word);
    }

    /**
     * Parses the words after {@code client}: the step first, then its arguments. Null when they
     * do not form one action, so the caller refuses before anything is sent.
     */
    public static PlayerActionOptions parse(String[] words) {
        if (words == null || words.length == 0 || !isStep(words[0])) {
            return null;
        }
        // Options are the words written key=value, after the step's own words.
        int positional = 0;
        while (positional < words.length && words[positional].indexOf('=') < 0) {
            positional++;
        }
        String[] stepWords = Arrays.copyOf(words, positional);
        PlayerActionOptions action = parseStep(stepWords);
        if (action == null) {
            return null;
        }
        java.util.Set<String> seen = new java.util.HashSet<String>();
        for (int i = positional; i < words.length; i++) {
            int eq = words[i].indexOf('=');
            if (eq < 0) {
                return null;
            }
            String key = words[i].substring(0, eq);
            String value = words[i].substring(eq + 1);
            if (!seen.add(key)) {
                return null;
            }
            if (OPTION_DELAY.equals(key) && TICKS.matcher(value).matches()) {
                action.delay = Integer.parseInt(value);
            } else if (OPTION_SCREENSHOT.equals(key) && SCREENSHOT_LABEL.matcher(value).matches()) {
                action.screenshot = value;
            } else if (OPTION_SCREENSHOT_DELAY.equals(key) && TICKS.matcher(value).matches()) {
                action.screenshotDelay = Integer.parseInt(value);
            } else if (OPTION_SCREENSHOT_HIDE.equals(key) && (HIDE_CHAT.equals(value) || HIDE_HUD.equals(value))) {
                action.screenshotHide = value;
            } else if (OPTION_SHOW_TICKS.equals(key) && TOOLTIP.equals(action.step) && HOLD_TICKS.matcher(value).matches()) {
                action.ticks = Integer.parseInt(value);
            } else {
                return null;
            }
        }
        if (TOOLTIP.equals(action.step) && !screenshotFitsTooltip(action.ticks, action.screenshot, action.screenshotDelay)) {
            return null;
        }
        if (!action.screenshotHide.isEmpty() && action.screenshot.isEmpty()) {
            // What to leave out of a picture means nothing without one.
            return null;
        }
        return isValidOptions(action.delay, action.screenshot, action.screenshotDelay) ? action : null;
    }

    /** {@code delay=}: ticks the client waits before it acts. */
    public static final String OPTION_DELAY = "delay";
    /** {@code screenshot=}: the label of a screenshot taken after the action. */
    public static final String OPTION_SCREENSHOT = "screenshot";
    /** {@code screenshotDelay=}: ticks from the action to that screenshot. */
    public static final String OPTION_SCREENSHOT_DELAY = "screenshotDelay";
    /** {@code screenshotHide=}: {@code chat} or {@code hud}, left out of that screenshot. */
    public static final String OPTION_SCREENSHOT_HIDE = "screenshotHide";
    /**
     * The values of {@code screenshotHide}, which are also the screenshot command's {@code hide}
     * values; the one list both read.
     */
    public static final String HIDE_CHAT = "chat";
    public static final String HIDE_HUD = "hud";
    /** {@code showTicks=}: for a tooltip, the ticks the view stays open (1 to 1200). */
    public static final String OPTION_SHOW_TICKS = "showTicks";

    /** Whether the options are in bounds; a screenshot delay needs a screenshot. */
    public static boolean isValidOptions(int delay, String screenshot, int screenshotDelay) {
        return delay >= 0 && delay <= MAX_DELAY_TICKS && screenshot != null
                && (screenshot.isEmpty() || SCREENSHOT_LABEL.matcher(screenshot).matches())
                && screenshotDelay >= 0 && screenshotDelay <= MAX_SCREENSHOT_DELAY_TICKS
                && (!screenshot.isEmpty() || screenshotDelay == 0);
    }

    private static PlayerActionOptions parseStep(String[] words) {
        if (words.length == 0) {
            return null;
        }
        String step = words[0];
        if (SELECT.equals(step)) {
            if (words.length != 2 || !HOTBAR_SLOT.matcher(words[1]).matches()) {
                return null;
            }
            return new PlayerActionOptions(step, Integer.parseInt(words[1]), "", NO_TICKS);
        }
        if (HOLD.equals(step)) {
            if (words.length != 3 || !(USE.equals(words[1]) || ATTACK.equals(words[1]))
                    || !HOLD_TICKS.matcher(words[2]).matches()) {
                return null;
            }
            return new PlayerActionOptions(step, NO_SLOT, words[1], Integer.parseInt(words[2]));
        }
        if (TOOLTIP.equals(step)) {
            if (words.length < 2 || !INVENTORY_SLOT.matcher(words[1]).matches()) {
                return null;
            }
            java.util.Set<String> given = new java.util.HashSet<String>();
            for (int i = 2; i < words.length; i++) {
                if (!TOOLTIP_WORDS.contains(words[i]) || !given.add(words[i])) {
                    return null;
                }
            }
            return new PlayerActionOptions(step, Integer.parseInt(words[1]), tooltipArgument(given), DEFAULT_SHOW_TICKS);
        }
        if (DROP.equals(step)) {
            if (words.length == 1) {
                return new PlayerActionOptions(step, NO_SLOT, "", NO_TICKS);
            }
            return words.length == 2 && ALL.equals(words[1]) ? new PlayerActionOptions(step, NO_SLOT, ALL, NO_TICKS) : null;
        }
        return words.length == 1 ? new PlayerActionOptions(step, NO_SLOT, "", NO_TICKS) : null;
    }

    /** The tooltip's words as one argument, comma separated, in the order of {@link #TOOLTIP_WORDS}. */
    private static String tooltipArgument(java.util.Set<String> given) {
        StringBuilder out = new StringBuilder();
        for (String word : TOOLTIP_WORDS) {
            if (given.contains(word)) {
                out.append(out.length() == 0 ? "" : ",").append(word);
            }
        }
        return out.toString();
    }

    /** For a tooltip's argument: the advanced form was asked. */
    public static boolean advancedOf(String argument) {
        return argument != null && Arrays.asList(argument.split(",")).contains(ADVANCED);
    }

    /** For a tooltip's argument: the modifier keys, {@code shift,ctrl,alt} in that order; empty for none. */
    public static String modifierKeysOf(String argument) {
        StringBuilder out = new StringBuilder();
        if (argument != null) {
            for (String word : argument.split(",")) {
                if (!word.isEmpty() && !ADVANCED.equals(word)) {
                    out.append(out.length() == 0 ? "" : ",").append(word);
                }
            }
        }
        return out.toString();
    }

    /** True for an argument {@link #tooltipArgument} could have made. */
    private static boolean isTooltipArgument(String argument) {
        if (argument.isEmpty()) {
            return true;
        }
        java.util.Set<String> given = new java.util.LinkedHashSet<String>(Arrays.asList(argument.split(",", -1)));
        return TOOLTIP_WORDS.containsAll(given) && tooltipArgument(given).equals(argument);
    }

    /**
     * For a tooltip with a screenshot: the picture is taken after the view's first frames and
     * the view closes after {@code showTicks}, so a screenshot delay above {@code showTicks - 2}
     * would picture the view already closed.
     */
    public static boolean screenshotFitsTooltip(int showTicks, String screenshot, int screenshotDelay) {
        return screenshot.isEmpty() || screenshotDelay <= showTicks - 2;
    }

    /** Whether a decoded message holds an action this parser would have produced. */
    public static boolean isValid(String step, int slot, String argument, int ticks) {
        if (!isStep(step) || argument == null) {
            return false;
        }
        if (SELECT.equals(step)) {
            return slot >= 0 && slot <= MAX_HOTBAR_SLOT && argument.isEmpty() && ticks == NO_TICKS;
        }
        if (TOOLTIP.equals(step)) {
            return slot >= 0 && slot <= MAX_INVENTORY_SLOT && isTooltipArgument(argument)
                    && ticks >= 1 && ticks <= MAX_HOLD_TICKS;
        }
        if (slot != NO_SLOT) {
            return false;
        }
        if (HOLD.equals(step)) {
            return (USE.equals(argument) || ATTACK.equals(argument)) && ticks >= 1 && ticks <= MAX_HOLD_TICKS;
        }
        if (DROP.equals(step)) {
            return (argument.isEmpty() || ALL.equals(argument)) && ticks == NO_TICKS;
        }
        return argument.isEmpty() && ticks == NO_TICKS;
    }

    /**
     * Whether the step acts on the world, so a dead player cannot take it. Choosing a slot and
     * opening the inventory do not; the death screen then refuses them on the client.
     */
    public static boolean needsLivingPlayer(String step) {
        return USE.equals(step) || ATTACK.equals(step) || HOLD.equals(step) || DROP.equals(step)
                || TOOLTIP.equals(step);
    }

    public String getStep() {
        return step;
    }

    /** The hotbar slot for {@code select}, the inventory slot for {@code tooltip}; {@link #NO_SLOT} otherwise. */
    public int getSlot() {
        return slot;
    }

    /**
     * {@code use} or {@code attack} for a hold, {@code all} for a whole-stack drop, the tooltip's
     * words comma separated for a tooltip; empty otherwise.
     */
    public String getArgument() {
        return argument;
    }

    /** The ticks of a hold, or the ticks a tooltip view stays open; {@link #NO_TICKS} otherwise. */
    public int getTicks() {
        return ticks;
    }

    /** Ticks the client waits before it acts; 0 acts at once. */
    public int getDelay() {
        return delay;
    }

    /** The label of the screenshot taken after the action; empty for none. */
    public String getScreenshot() {
        return screenshot;
    }

    /** Ticks from the action to its screenshot. */
    public int getScreenshotDelay() {
        return screenshotDelay;
    }

    /** What that screenshot leaves out, {@code chat} or {@code hud}; empty for nothing. */
    public String getScreenshotHide() {
        return screenshotHide;
    }
}
