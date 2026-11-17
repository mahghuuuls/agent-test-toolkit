package com.mahghuuuls.agenttesttoolkit.client;

/**
 * Whether the game took a press made while a hand was active, from plain values.
 *
 * <p>While the player uses an item, the game empties the attack, use and pick-block press counts
 * on its next key handling without acting on them. A press is still taken when something reads
 * it during an input event first (a mod listening there), or when the hand was no longer active
 * by the time the game handled it. The count alone cannot tell a press drained from one used, so
 * the hand's state and the input events decide.
 */
final class PressTaken {

    static final String UNKNOWN = "unknown";

    private PressTaken() {
    }

    /**
     * @param readable     the binding's press count could be read
     * @param takenByEvent the count fell while an input event was handled, or a mod cancelled the
     *                     mouse event of a queued click
     * @param expected     the count once the press was made
     * @param after        the count at the end of the tick the game handled the press in
     * @param handActive   a hand was active when that tick began
     * @return {@code true}, {@code false} or {@code unknown}
     */
    static String taken(boolean readable, boolean takenByEvent, int expected, int after, boolean handActive) {
        if (!readable) {
            return UNKNOWN;
        }
        if (takenByEvent) {
            return "true";
        }
        return String.valueOf(after < expected && !handActive);
    }
}
