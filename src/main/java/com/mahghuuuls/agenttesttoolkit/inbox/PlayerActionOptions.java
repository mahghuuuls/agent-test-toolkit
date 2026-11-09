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

    /** The steps, in the order the usage text lists them. */
    public static final List<String> STEPS = Collections.unmodifiableList(Arrays.asList(SELECT, USE, ATTACK));

    /** The last hotbar slot; the hotbar has nine, counted from 0. */
    public static final int MAX_HOTBAR_SLOT = 8;

    /** No slot: the step takes none. */
    public static final int NO_SLOT = -1;

    private static final Pattern HOTBAR_SLOT = Pattern.compile("[0-8]");

    private final String step;
    private final int slot;

    private PlayerActionOptions(String step, int slot) {
        this.step = step;
        this.slot = slot;
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
        String step = words[0];
        if (SELECT.equals(step)) {
            if (words.length != 2 || !HOTBAR_SLOT.matcher(words[1]).matches()) {
                return null;
            }
            return new PlayerActionOptions(step, Integer.parseInt(words[1]));
        }
        return words.length == 1 ? new PlayerActionOptions(step, NO_SLOT) : null;
    }

    /** Whether a decoded message holds an action this parser would have produced. */
    public static boolean isValid(String step, int slot) {
        if (!isStep(step)) {
            return false;
        }
        if (SELECT.equals(step)) {
            return slot >= 0 && slot <= MAX_HOTBAR_SLOT;
        }
        return slot == NO_SLOT;
    }

    /** Whether the step acts on the world, so a dead player cannot take it. Choosing a slot does not. */
    public static boolean needsLivingPlayer(String step) {
        return USE.equals(step) || ATTACK.equals(step);
    }

    public String getStep() {
        return step;
    }

    /** The hotbar slot for {@code select}; {@link #NO_SLOT} otherwise. */
    public int getSlot() {
        return slot;
    }
}
