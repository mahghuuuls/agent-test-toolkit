package com.mahghuuuls.agenttesttoolkit.bundle;

import java.util.ArrayList;
import java.util.List;

/**
 * The chain of bundles from the one the operator ran down to the one executing now.
 *
 * <p>Exists to make two failures impossible rather than merely unlikely: a bundle that invokes
 * itself, and a nesting chain deep enough to be a mistake. Both are checked <b>before</b> a
 * child starts, so neither can recurse, overflow, or hang the server tick.
 *
 * <p>Immutable and free of Minecraft types. Cycle detection is the one part of nesting that
 * should never need a game to test, and an immutable chain also means a child cannot corrupt
 * its parent's view by pushing onto shared state.
 */
public final class BundleCallStack {

    /**
     * Maximum nesting depth, counting the bundle the operator ran as depth 1.
     *
     * <p>Ten is arbitrary but deliberate: deep enough that no honest setup routine reaches it,
     * shallow enough that hitting it means a mistake worth reporting rather than a limit worth
     * raising.
     */
    public static final int MAX_DEPTH = 10;

    private final List<String> chain;

    private BundleCallStack(List<String> chain) {
        this.chain = chain;
    }

    /** The chain for a bundle the operator ran directly. */
    public static BundleCallStack root(String bundleName) {
        List<String> chain = new ArrayList<String>(1);
        chain.add(bundleName);
        return new BundleCallStack(chain);
    }

    /** Why a nested call was refused, or null when it is allowed. */
    public String rejectionReason(String childName) {
        if (childName == null) {
            return null;
        }
        if (chain.contains(childName)) {
            // Named in full rather than reporting "a cycle was detected". The author needs to
            // see which bundle re-entered and by what route, and the route is the only part
            // they cannot reconstruct from the file.
            return "bundle '" + childName + "' would run itself: " + describe() + " -> " + childName;
        }
        if (chain.size() >= MAX_DEPTH) {
            return "bundle nesting is deeper than " + MAX_DEPTH + ": " + describe()
                    + " -> " + childName;
        }
        return null;
    }

    /**
     * @return the chain with the child appended
     * @throws IllegalStateException if the child is not permitted; callers must consult
     *                               {@link #rejectionReason} first
     */
    public BundleCallStack push(String childName) {
        String reason = rejectionReason(childName);
        if (reason != null) {
            throw new IllegalStateException(reason);
        }
        List<String> next = new ArrayList<String>(chain.size() + 1);
        next.addAll(chain);
        next.add(childName);
        return new BundleCallStack(next);
    }

    public int getDepth() {
        return chain.size();
    }

    public String getRootName() {
        return chain.get(0);
    }

    public String getCurrentName() {
        return chain.get(chain.size() - 1);
    }

    /** The chain as {@code a -> b -> c}, for error messages. */
    public String describe() {
        StringBuilder out = new StringBuilder();
        for (String name : chain) {
            if (out.length() > 0) {
                out.append(" -> ");
            }
            out.append(name);
        }
        return out.toString();
    }
}
