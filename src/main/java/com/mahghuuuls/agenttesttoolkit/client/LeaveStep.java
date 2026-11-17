package com.mahghuuuls.agenttesttoolkit.client;

/**
 * What the single-player logout leave does next, from plain values, so every stage and both
 * failures are tested without a game.
 *
 * <p>The leave sends the quitting packet and stops the integrated server only once the server is
 * proven paused. A paused integrated server runs its queued tasks but not its network step, so
 * it cannot see the closed connection and stop itself while the client waits for its shutdown
 * task, which is the freeze this sequence exists to prevent. The proof is two marker tasks: the
 * second is queued only after the first has run, so it runs in a server tick that began after
 * the game was paused, and every tick from then on is a paused one while the pause screen stays.
 *
 * <p>The two stages, pausing (the pause screen is open, the game does not read as paused yet)
 * and proving (paused, the markers not both run), are the reconnect's own stages; this class
 * only decides between them.
 */
final class LeaveStep {

    /** How long the pause may take to be proven before the leave is given up, in milliseconds. */
    static final long DEADLINE_MS = 10000L;

    /** What the client does this tick. */
    enum Next {
        /** Nothing yet. */
        WAIT,
        /** The game is paused: queue the first marker and start proving. */
        PROVE,
        /** The first marker ran: queue the second. */
        QUEUE_SECOND,
        /** Both ran: send the quitting packet and leave, in this same tick. */
        LEAVE,
        /** Not proven in time: close the pause screen, stay in the world, end the reconnect failed. */
        TIMEOUT,
        /** Another screen took the pause screen's place, or the world is gone: end it failed, sending nothing. */
        INTERRUPTED
    }

    private LeaveStep() {
    }

    /**
     * @param proving          the game already read as paused and the markers are being run
     * @param worldOpen        the client is still in its single-player world, with its server
     * @param pauseScreenShown the screen open is the toolkit's own pause screen
     * @param gamePaused       the game reads as paused, which is what the integrated server checks
     * @param markersQueued    how many marker tasks were queued, 0 to 2
     * @param markersDone      how many of them have run
     * @param msSincePause     milliseconds since the pause screen was opened
     */
    static Next next(boolean proving, boolean worldOpen, boolean pauseScreenShown, boolean gamePaused,
                     int markersQueued, int markersDone, long msSincePause) {
        if (!worldOpen || !pauseScreenShown) {
            // Before anything is sent: whatever replaced the pause screen, or left, is the person's or a mod's.
            return Next.INTERRUPTED;
        }
        if (msSincePause > DEADLINE_MS) {
            // The leave promises a bounded wait: past the deadline it gives up, even when the
            // pause or the markers would now be proven, so a caller never waits longer than that.
            return Next.TIMEOUT;
        }
        if (!proving) {
            return gamePaused ? Next.PROVE : Next.WAIT;
        }
        if (gamePaused && markersQueued >= 2 && markersDone >= 2) {
            return Next.LEAVE;
        }
        if (gamePaused && markersQueued == 1 && markersDone >= 1) {
            return Next.QUEUE_SECOND;
        }
        // Waiting for a marker, or for the game to read as paused again: a server tick that is
        // not paused could still stop itself, so nothing is sent until it is.
        return Next.WAIT;
    }
}
