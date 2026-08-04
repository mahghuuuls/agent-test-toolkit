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
}
