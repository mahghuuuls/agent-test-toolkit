package com.mahghuuuls.agenttesttoolkit.bundle;

/**
 * Where placeholder values come from at the moment a command runs.
 *
 * <p>Every method returns null when the value is not available right now, and
 * {@link Placeholders} turns that into a command failure with a named key. The source never
 * throws and never guesses: an operator who is not bound is null, not an empty string, because
 * an empty string would silently produce {@code gamemode creative } and a confusing vanilla
 * error two steps later.
 */
public interface PlaceholderSource {

    /** The bound operator's name, or null. */
    String operatorName();

    /** The bound operator's UUID as text, or null. */
    String operatorUuid();

    /** A selector resolving to the bound operator, or null. */
    String operatorSelector();

    /** The UUID of a fixture claimed under this handle in the current session, or null. */
    String fixtureUuid(String handle);

    /** A source with nothing bound. Every lookup fails, which is the honest resting state. */
    PlaceholderSource EMPTY = new PlaceholderSource() {
        @Override
        public String operatorName() {
            return null;
        }

        @Override
        public String operatorUuid() {
            return null;
        }

        @Override
        public String operatorSelector() {
            return null;
        }

        @Override
        public String fixtureUuid(String handle) {
            return null;
        }
    };
}
