package com.mahghuuuls.agenttesttoolkit.client;

/**
 * Whether an input route that reaches into another library's private state still works in this
 * launch, from plain values.
 *
 * <p>A route that failed once is refused from then on, so a half-working reflection is never
 * tried again on the next request. The first refusal after a failure names the exception, so the
 * log says why; later ones only say the route is unavailable.
 */
final class RouteState {

    /** The route cannot be driven in this game. */
    static final String REASON_ROUTE_UNAVAILABLE = "routeUnavailable";

    private boolean failed;
    private String failure;
    private boolean failureNamed;

    /** The route stops working for the rest of the launch; {@code why} is the exception's name. */
    void fail(String why) {
        if (!failed) {
            failed = true;
            failure = why == null || why.isEmpty() ? "unknown" : why;
        }
    }

    boolean isFailed() {
        return failed;
    }

    /** {@code routeUnavailable} once failed; null while it works. */
    String refusal() {
        return failed ? REASON_ROUTE_UNAVAILABLE : null;
    }

    /** The exception's name for the first refusal after the failure; null for every later one. */
    String takeFailureName() {
        if (!failed || failureNamed) {
            return null;
        }
        failureNamed = true;
        return failure;
    }
}
