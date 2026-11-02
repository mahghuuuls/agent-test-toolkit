package com.mahghuuuls.agenttesttoolkit.logging;

/**
 * Records an observer holds back until the end of the tick, written early on request.
 *
 * <p>{@code ENTITY_DAMAGE} is assembled from three Forge events and written at the end of the
 * server tick (ARC-003). Read in file order that put it after its own target's
 * {@code ENTITY_DEATH}, and after a {@code LOG_CONFIG} written in the same tick, so a reader saw
 * a death with no hit before it, or a hit recorded after its category was turned off. The
 * death observer and the {@code log} command ask for the pending records first (ARC-025).
 *
 * <p>The hook lives here so neither caller depends on the damage observer: {@code observe}
 * depends only on {@code logging} and {@code state} (rule 3), and {@code command} need not reach
 * into an observer's state. Installed at mod start; a no-op until then, and in tests.
 */
public final class PendingRecords {

    /** What an observer offers to write early. */
    public interface Flusher {
        /** Writes the target's pending records whose events have finished. */
        void flushFinished(Object target);

        /** Writes every pending record. */
        void flushAll();
    }

    private static final Flusher NONE = new Flusher() {
        @Override
        public void flushFinished(Object target) {
        }

        @Override
        public void flushAll() {
        }
    };

    private static volatile Flusher flusher = NONE;

    private PendingRecords() {
    }

    public static void install(Flusher installed) {
        flusher = installed == null ? NONE : installed;
    }

    /** Before a death record: that target's finished hits. */
    public static void flushFinished(Object target) {
        flusher.flushFinished(target);
    }

    /** Before a logging change: everything pending. */
    public static void flushAll() {
        flusher.flushAll();
    }
}
