package com.mahghuuuls.agenttesttoolkit.bundle;

/**
 * Runs one command and says whether it worked.
 *
 * <p>An interface, rather than a direct call into the server command manager, for one reason
 * that matters: it is what makes the execution state machine testable. Ordering, failure
 * classification, stop-on-failure and counter accuracy are the rules most likely to be wrong,
 * and none of them should require a running game to check.
 *
 * <p>Implementations must preserve the original caller's permission context. The real one
 * passes the original sender through to the command manager rather than substituting a sender
 * of its own, so a bundle can never run a command its caller could not have typed.
 */
public interface CommandDispatcher {

    /**
     * @param command the command line, without a leading slash
     * @return the outcome; never null
     */
    CommandOutcome dispatch(String command);

    /**
     * Whether the original caller can still be reached.
     *
     * <p>Asked before every batch of commands rather than assumed once, because a
     * bundle with delays spans ticks and the player who started it can disconnect between them.
     *
     * <p>The sender is re-resolved rather than held. That is what makes
     * this a normal check on the ordinary path instead of a special case: a lost caller is
     * simply a dispatcher that reports unavailable, and execution ends the same way it ends for
     * any other reason.
     */
    /**
     * <p>Defaulted to true because that is the correct answer for every caller that cannot
     * disconnect: the console, a command block, and every test stub. Only a player-backed
     * dispatcher has a real answer to give, and it overrides this.
     */
    default boolean isSenderAvailable() {
        return true;
    }
}
