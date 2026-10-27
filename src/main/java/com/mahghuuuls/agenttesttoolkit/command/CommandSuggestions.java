package com.mahghuuuls.agenttesttoolkit.command;

import java.util.Collection;
import java.util.Locale;

/**
 * Names the subcommand a mistyped one most likely meant.
 *
 * <p>Two rules, either enough: the typed word starts with a real name, or the other way round
 * ({@code logging} to {@code log}); or it is within two single-character edits of one
 * ({@code sesion} to {@code session}). The prefix rule exists because the reported mistake,
 * {@code logging}, is four edits from {@code log}. Text only: a suggestion never runs anything.
 */
public final class CommandSuggestions {

    private static final int MAX_EDITS = 2;
    private static final int MIN_PREFIX = 3;

    private CommandSuggestions() {
    }

    /** @return the closest name, the first one in iteration order on a tie, or null. */
    public static String closest(String typed, Collection<String> names) {
        if (typed == null || typed.isEmpty()) {
            return null;
        }
        String word = typed.toLowerCase(Locale.ROOT);
        String prefixMatch = null;
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String name : names) {
            String candidate = name.toLowerCase(Locale.ROOT);
            if (prefixMatch == null && candidate.length() >= MIN_PREFIX && word.length() >= MIN_PREFIX
                    && (word.startsWith(candidate) || candidate.startsWith(word))) {
                prefixMatch = name;
            }
            int distance = distance(word, candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = name;
            }
        }
        // A prefix match beats a distance of two: "ent" is two edits from "nbt" but plainly
        // the start of "entities". A word too short to judge gets no distance suggestion.
        if (best != null && bestDistance <= 1 && word.length() >= MIN_PREFIX) {
            return best;
        }
        if (prefixMatch != null) {
            return prefixMatch;
        }
        if (best != null && bestDistance <= MAX_EDITS && word.length() >= MIN_PREFIX) {
            return best;
        }
        return null;
    }

    /** Levenshtein distance; the words here are a dozen characters at most. */
    static int distance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
