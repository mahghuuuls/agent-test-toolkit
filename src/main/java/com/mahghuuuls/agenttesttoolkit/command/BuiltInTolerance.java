package com.mahghuuuls.agenttesttoolkit.command;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Which bundle commands may fail with "nothing matched" without declaring it.
 *
 * <p>Only commands whose whole purpose is to remove something: a teardown that runs them twice
 * finds nothing the second time, and that is the state it wanted. A check command is the
 * opposite: {@code testfor}, {@code execute} or {@code tp} matching nothing is the very answer
 * the check exists to give, so it fails unless the step declares the key. The table lists the
 * commands that are tolerated, rather than the ones that fail, so a command it does not know,
 * another mod's included, errs toward being reported.
 *
 * <p>A pure function of the command's name, its arguments and the failure key, so the table is
 * checked without a game.
 */
public final class BuiltInTolerance {

    /** The failures that mean only that nothing matched, or that there was nothing to remove. */
    private static final Set<String> NOTHING_MATCHED = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            CommandOutcomes.KEY_SELECTOR_NOT_FOUND,
            CommandOutcomes.KEY_ENTITY_NOT_FOUND,
            CommandOutcomes.KEY_PLAYER_NOT_FOUND,
            CommandOutcomes.KEY_CLEAR_NOTHING,
            CommandOutcomes.KEY_EFFECT_NOT_ACTIVE,
            CommandOutcomes.KEY_EFFECT_NOT_ACTIVE_ALL)));

    private BuiltInTolerance() {
    }

    /**
     * Whether this failure is tolerated without a declaration: a "nothing matched" key from
     * {@code kill}, {@code clear}, {@code effect <target> clear}, {@code scoreboard players reset}
     * or {@code scoreboard players tag <target> remove}.
     *
     * @param name the command's name as registered, such as {@code kill}; null when unknown
     * @param args the arguments the command ran with; null is none
     */
    public static boolean tolerates(String name, String[] args, String translationKey) {
        if (name == null || translationKey == null || !NOTHING_MATCHED.contains(translationKey)) {
            return false;
        }
        String[] words = args == null ? new String[0] : args;
        if ("kill".equals(name) || "clear".equals(name)) {
            return true;
        }
        // Each word compared as vanilla compares it, so a form vanilla does not run as a removal
        // is not tolerated as one: "clear" and "remove" exactly, the scoreboard words in any case.
        if ("effect".equals(name)) {
            return words.length >= 2 && "clear".equals(words[1]);
        }
        if ("scoreboard".equals(name) && words.length >= 2 && "players".equalsIgnoreCase(words[0])) {
            if ("reset".equalsIgnoreCase(words[1])) {
                return true;
            }
            return "tag".equalsIgnoreCase(words[1]) && words.length >= 4 && "remove".equals(words[3]);
        }
        return false;
    }
}
