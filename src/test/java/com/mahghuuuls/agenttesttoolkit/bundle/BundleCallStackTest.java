package com.mahghuuuls.agenttesttoolkit.bundle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nesting depth and cycle detection.
 *
 * <p>The issue is explicit that cycle detection should never require a game to test, and the
 * reason is the failure mode: a bundle invoking itself would otherwise recurse until the server
 * tick dies, and "the server stopped responding" is the least diagnosable symptom this project
 * could produce.
 *
 * <p>Both checks run before a child starts, so nothing partially executes and then unwinds.
 */
class BundleCallStackTest {

    @Test
    @DisplayName("a fresh chain is one deep and names the bundle that was run")
    void rootChain() {
        BundleCallStack stack = BundleCallStack.root("setup");
        assertEquals(1, stack.getDepth());
        assertEquals("setup", stack.getRootName());
        assertEquals("setup", stack.getCurrentName());
    }

    @Test
    @DisplayName("an ordinary nested call is permitted")
    void ordinaryNesting() {
        BundleCallStack stack = BundleCallStack.root("a").push("b").push("c");
        assertEquals(3, stack.getDepth());
        assertEquals("a", stack.getRootName());
        assertEquals("c", stack.getCurrentName());
        assertNull(stack.rejectionReason("d"));
    }

    @Test
    @DisplayName("a bundle invoking itself directly is refused")
    void directSelfInvocationRefused() {
        assertNotNull(BundleCallStack.root("loop").rejectionReason("loop"));
    }

    @Test
    @DisplayName("an indirect cycle is refused, a to b to a")
    void indirectCycleRefused() {
        // The acceptance criterion's case. Detecting only direct self-invocation would let this
        // through, and it is the more likely mistake: nobody writes a bundle that calls itself,
        // but two setup bundles each calling the other is an easy accident.
        BundleCallStack stack = BundleCallStack.root("bundle_a").push("bundle_b");
        String reason = stack.rejectionReason("bundle_a");

        assertNotNull(reason);
        assertTrue(reason.contains("bundle_a"), reason);
        assertTrue(reason.contains("bundle_b"), reason);
    }

    @Test
    @DisplayName("the cycle error names the whole route, not just the repeated bundle")
    void cycleErrorNamesRoute() {
        // The route is the only part the author cannot reconstruct by reading their files.
        String reason = BundleCallStack.root("a").push("b").push("c").rejectionReason("a");
        assertTrue(reason.contains("a -> b -> c -> a"), reason);
    }

    @Test
    @DisplayName("nesting is allowed up to the depth limit")
    void allowedUpToLimit() {
        BundleCallStack stack = BundleCallStack.root("b1");
        for (int i = 2; i <= BundleCallStack.MAX_DEPTH; i++) {
            assertNull(stack.rejectionReason("b" + i), "depth " + i + " should be allowed");
            stack = stack.push("b" + i);
        }
        assertEquals(BundleCallStack.MAX_DEPTH, stack.getDepth());
    }

    @Test
    @DisplayName("one level beyond the limit is refused with the limit named")
    void refusedBeyondLimit() {
        // The acceptance criterion asks for a chain 11 deep to fail explicitly.
        BundleCallStack stack = BundleCallStack.root("b1");
        for (int i = 2; i <= BundleCallStack.MAX_DEPTH; i++) {
            stack = stack.push("b" + i);
        }
        String reason = stack.rejectionReason("b11");

        assertNotNull(reason);
        assertTrue(reason.contains(String.valueOf(BundleCallStack.MAX_DEPTH)), reason);
    }

    @Test
    @DisplayName("a cycle is reported as a cycle even at the depth limit")
    void cycleWinsOverDepth() {
        // Both conditions can hold at once. The cycle is the more actionable message: raising
        // a depth limit never fixes a loop.
        BundleCallStack stack = BundleCallStack.root("b1");
        for (int i = 2; i <= BundleCallStack.MAX_DEPTH; i++) {
            stack = stack.push("b" + i);
        }
        String reason = stack.rejectionReason("b1");
        assertTrue(reason.contains("would run itself"), reason);
    }

    @Test
    @DisplayName("pushing a refused child throws rather than corrupting the chain")
    void pushRefusedThrows() {
        // push is not the guard; rejectionReason is. This makes a caller that forgets to check
        // fail loudly at the point of the mistake instead of building an invalid chain.
        assertThrows(IllegalStateException.class,
                () -> BundleCallStack.root("loop").push("loop"));
    }

    @Test
    @DisplayName("the chain is immutable, so a child cannot corrupt its parent")
    void chainIsImmutable() {
        BundleCallStack parent = BundleCallStack.root("a");
        BundleCallStack child = parent.push("b");

        assertEquals(1, parent.getDepth(), "parent must be unchanged by the child");
        assertEquals(2, child.getDepth());
        assertNull(parent.rejectionReason("b"),
                "a sibling call must still be allowed after a child was pushed");
    }
}
