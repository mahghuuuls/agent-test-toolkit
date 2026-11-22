package com.mahghuuuls.agenttesttoolkit.inbox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The options that make a client step wait in its bundle: {@code wait=started|done},
 * {@code waitTicks=<1-1200>} and {@code expect=<selector|uuid>}, and the words of
 * {@code client await <input> [waitTicks]}.
 *
 * <p>Pure, and the one owner of these words, their bounds and the steps that take them. They are
 * the server's alone: they are taken out of the step's words before the step is parsed, so the
 * words sent to the client are exactly the ones a 1.9.0 client reads, and a step without them is
 * the same step it was before. {@code expect} is taken only by {@code use}, {@code attack},
 * {@code hold} and {@code face <selector|uuid>}; on a screen step the same word names a screen
 * class and stays the step's own.
 */
public final class ClientWaitOptions {

    public static final String OPTION_WAIT = "wait";
    public static final String OPTION_WAIT_TICKS = "waitTicks";
    public static final String OPTION_EXPECT = "expect";
    /** Pass once the client says the action began. */
    public static final String STARTED = "started";
    /** Pass once the client says the action finished. */
    public static final String DONE = "done";
    /** The step that waits for a hold started earlier in the bundle. */
    public static final String AWAIT = "await";

    /** How long a step waits for its answer when not told, in ticks: ten seconds. */
    public static final int DEFAULT_WAIT_TICKS = 200;
    /** The longest wait for an answer, in ticks: one minute. */
    public static final int MAX_WAIT_TICKS = 1200;

    /** 1 to 1200, without leading zeros. */
    private static final Pattern WAIT_TICKS = Pattern.compile("(?:[1-9][0-9]{0,2}|1[01][0-9]{2}|1200)");
    /** The steps that take {@code wait}, besides {@code key ... press|hold}. */
    private static final List<String> WAITING_STEPS = Collections.unmodifiableList(Arrays.asList(
            PlayerActionOptions.SELECT, PlayerActionOptions.USE, PlayerActionOptions.ATTACK, PlayerActionOptions.HOLD,
            PlayerActionOptions.DROP, PlayerActionOptions.INVENTORY, PlayerActionOptions.TOOLTIP,
            InputOptions.LOOK, InputOptions.FACE, InputOptions.VIEW,
            ScreenOptions.SCREEN, ScreenOptions.CLICK, ScreenOptions.SCROLL));

    private final String[] words;
    private final String wait;
    private final int waitTicks;
    private final String expect;

    private ClientWaitOptions(String[] words, String wait, int waitTicks, String expect) {
        this.words = words;
        this.wait = wait;
        this.waitTicks = waitTicks;
        this.expect = expect;
    }

    /**
     * Takes these options out of the words after {@code client}, the step first and in lower case.
     * Null when they are given wrong: a bad value, a key twice, {@code waitTicks} without anything
     * to wait for, or {@code expect} with {@code wait=started}. A step that does not take them
     * keeps them among its words, where its own parser refuses them.
     */
    public static ClientWaitOptions split(String[] words) {
        if (words == null || words.length == 0) {
            return null;
        }
        boolean takesWait = takesWait(words);
        boolean takesExpect = takesExpect(words);
        List<String> kept = new ArrayList<String>();
        kept.add(words[0]);
        Set<String> seen = new HashSet<String>();
        String wait = "";
        String ticks = null;
        String expect = "";
        for (int i = 1; i < words.length; i++) {
            String word = words[i];
            int eq = word.indexOf('=');
            String key = eq <= 0 ? "" : word.substring(0, eq);
            String value = eq <= 0 ? "" : word.substring(eq + 1);
            boolean mine = (takesWait && (OPTION_WAIT.equals(key) || OPTION_WAIT_TICKS.equals(key)))
                    || (takesExpect && OPTION_EXPECT.equals(key));
            if (!mine) {
                kept.add(word);
                continue;
            }
            if (!seen.add(key)) {
                return null;
            }
            if (OPTION_WAIT.equals(key)) {
                if (!STARTED.equals(value) && !DONE.equals(value)) {
                    return null;
                }
                wait = value;
            } else if (OPTION_WAIT_TICKS.equals(key)) {
                if (!WAIT_TICKS.matcher(value).matches()) {
                    return null;
                }
                ticks = value;
            } else {
                if (!InputOptions.isEntityReference(value)) {
                    return null;
                }
                expect = value;
            }
        }
        if (!expect.isEmpty()) {
            // An expected target is checked on the finished action, so it waits as done does.
            if (STARTED.equals(wait)) {
                return null;
            }
            wait = DONE;
        }
        if (ticks != null && wait.isEmpty()) {
            return null;
        }
        return new ClientWaitOptions(kept.toArray(new String[0]), wait,
                ticks == null ? DEFAULT_WAIT_TICKS : Integer.parseInt(ticks), expect);
    }

    /** Whether the step these words start takes {@code wait}: every acting step but a key's release and focus. */
    static boolean takesWait(String[] words) {
        String step = words[0];
        if (InputOptions.KEY.equals(step)) {
            return words.length >= 3 && (InputOptions.PRESS.equals(words[2]) || InputOptions.HOLD.equals(words[2]));
        }
        return WAITING_STEPS.contains(step);
    }

    /** Whether the step these words start takes {@code expect}: a click, a hold, or a turn to an entity. */
    static boolean takesExpect(String[] words) {
        String step = words[0];
        if (InputOptions.FACE.equals(step)) {
            return words.length >= 2 && !InputOptions.BLOCK.equals(words[1]);
        }
        return PlayerActionOptions.USE.equals(step) || PlayerActionOptions.ATTACK.equals(step)
                || PlayerActionOptions.HOLD.equals(step);
    }

    /** The step's own words, without these options. */
    public String[] getWords() {
        return words.clone();
    }

    /** Whether the step waits in its bundle. */
    public boolean waits() {
        return !wait.isEmpty();
    }

    /** {@link #STARTED} or {@link #DONE}, {@link #DONE} with an expected target; empty when the step does not wait. */
    public String getWait() {
        return wait;
    }

    /** Ticks the step waits for its answer. */
    public int getWaitTicks() {
        return waitTicks;
    }

    /** The selector or UUID the step expects under the crosshair; empty for none. */
    public String getExpect() {
        return expect;
    }

    /** The words of {@code client await}: the input whose hold it waits for, and for how long. */
    public static final class Await {
        private final String input;
        private final int waitTicks;

        Await(String input, int waitTicks) {
            this.input = input;
            this.waitTicks = waitTicks;
        }

        /** {@code use}, {@code attack}, or a key binding's name. */
        public String getInput() {
            return input;
        }

        public int getWaitTicks() {
            return waitTicks;
        }
    }

    /** Parses {@code await <use|attack|binding> [waitTicks]}, the step first in lower case; null when wrong. */
    public static Await parseAwait(String[] words) {
        if (words == null || words.length < 2 || words.length > 3 || !AWAIT.equals(words[0])
                || !InputOptions.isBindingName(words[1])) {
            return null;
        }
        if (words.length == 3 && !WAIT_TICKS.matcher(words[2]).matches()) {
            return null;
        }
        return new Await(words[1], words.length == 3 ? Integer.parseInt(words[2]) : DEFAULT_WAIT_TICKS);
    }
}
