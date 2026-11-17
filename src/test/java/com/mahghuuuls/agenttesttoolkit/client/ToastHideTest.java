package com.mahghuuuls.agenttesttoolkit.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The toasts taken out of one frame come back in their slots and their queue order, in the same objects. */
class ToastHideTest {

    @Test
    @DisplayName("taken: the slots and the queue are emptied in place; restored: the same toasts in the same places")
    void restoreKeepsOrderAndIdentity() {
        Object a = new Object();
        Object b = new Object();
        Object q1 = new Object();
        Object q2 = new Object();
        Object[] slots = {a, null, b, null, null};
        Deque<Object> queue = new ArrayDeque<Object>(Arrays.asList(q1, q2));
        ToastHide hide = new ToastHide();

        hide.take(slots, queue);
        assertArrayEquals(new Object[5], slots, "no toast drawn in the frame");
        assertTrue(queue.isEmpty(), "none waiting to fill a slot during the frame");

        Object late = new Object();
        queue.add(late);
        hide.restore();
        assertSame(a, slots[0]);
        assertNull(slots[1]);
        assertSame(b, slots[2]);
        assertEquals(new ArrayList<Object>(Arrays.asList(q1, q2, late)), new ArrayList<Object>(queue),
                "the waiting toasts ahead of one added meanwhile, in their order");

        hide.restore();
        assertEquals(3, queue.size(), "a second restore puts nothing back twice");
        assertSame(a, slots[0]);
    }

    @Test
    @DisplayName("a second take first gives back what the first took")
    void takeTwice() {
        Object a = new Object();
        Object[] slots = {a};
        Deque<Object> queue = new ArrayDeque<Object>();
        ToastHide hide = new ToastHide();
        hide.take(slots, queue);
        hide.take(slots, queue);
        hide.restore();
        assertSame(a, slots[0]);
    }
}
