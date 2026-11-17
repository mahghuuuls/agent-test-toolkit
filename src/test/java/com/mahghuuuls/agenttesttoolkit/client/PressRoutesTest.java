package com.mahghuuuls.agenttesttoolkit.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The plain parts of the press routes: whether a press was taken, a route refused for the rest
 * of a launch, the mouse queue's event layout, and the button numbers each library uses.
 */
class PressRoutesTest {

    @Test
    @DisplayName("taken: by an input event, or used by the game with no hand active; drained while one is")
    void taken() {
        assertEquals("true", PressTaken.taken(true, true, 1, 1, true), "a mod took it in an input event");
        assertEquals("false", PressTaken.taken(true, false, 1, 0, true), "drained while the hand was active");
        assertEquals("true", PressTaken.taken(true, false, 1, 0, false), "the hand was free: the game clicked");
        assertEquals("false", PressTaken.taken(true, false, 1, 1, false), "nothing read it");
        assertEquals("unknown", PressTaken.taken(false, true, -1, -1, false), "the count could not be read");
    }

    @Test
    @DisplayName("a route that failed is refused from then on, and only its first refusal names the exception")
    void routeState() {
        RouteState state = new RouteState();
        assertNull(state.refusal());
        assertNull(state.takeFailureName(), "nothing failed");
        state.fail("InaccessibleObjectException");
        state.fail("SecondFailure");
        assertTrue(state.isFailed());
        assertEquals("routeUnavailable", state.refusal());
        assertEquals("InaccessibleObjectException", state.takeFailureName(), "the first failure is kept");
        assertNull(state.takeFailureName(), "named once");
        assertEquals("routeUnavailable", state.refusal(), "still refused");
        RouteState unnamed = new RouteState();
        unnamed.fail("");
        assertEquals("unknown", unnamed.takeFailureName());
    }

    @Test
    @DisplayName("a queued button event is written after the unread ones, in the 22 bytes the library reads")
    void lwjgl2EventLayout() {
        ByteBuffer buffer = ByteBuffer.allocate(MouseQueue.Lwjgl2.EVENT_SIZE * 3);
        buffer.limit(MouseQueue.Lwjgl2.EVENT_SIZE);
        buffer.put(0, (byte) 1);
        MouseQueue.Lwjgl2.append(buffer, 0, true, 0, 0, 77L);
        assertEquals(0, buffer.position(), "the unread event stays first");
        assertEquals(2 * MouseQueue.Lwjgl2.EVENT_SIZE, buffer.limit());
        buffer.position(MouseQueue.Lwjgl2.EVENT_SIZE);
        assertEquals(0, buffer.get(), "button: left");
        assertEquals(1, buffer.get(), "state: down");
        assertEquals(0, buffer.getInt());
        assertEquals(0, buffer.getInt());
        assertEquals(0, buffer.getInt(), "no wheel");
        assertEquals(77L, buffer.getLong());
        assertFalse(buffer.hasRemaining());
    }

    @Test
    @DisplayName("a full buffer's read events are dropped to make room; one with no room left refuses")
    void lwjgl2Compacts() {
        int size = MouseQueue.Lwjgl2.EVENT_SIZE;
        ByteBuffer buffer = ByteBuffer.allocate(size * 2);
        buffer.limit(size * 2);
        buffer.position(size);
        buffer.put(size, (byte) 5);
        MouseQueue.Lwjgl2.append(buffer, 1, false, 3, 4, 1L);
        assertEquals(0, buffer.position());
        assertEquals(2 * size, buffer.limit());
        assertEquals(5, buffer.get(0), "the unread event moved to the front");
        assertEquals(1, buffer.get(size), "then the new one: right button");
        assertEquals(0, buffer.get(size + 1), "up");
        assertEquals(3, buffer.getInt(size + 2));
        assertEquals(4, buffer.getInt(size + 6));
        assertThrows(IllegalStateException.class, () -> MouseQueue.Lwjgl2.append(buffer, 0, true, 0, 0, 0L));
    }

    @Test
    @DisplayName("Cleanroom numbers buttons as SDL does: left 1, middle 2, right 3")
    void sdlButtons() {
        assertEquals(1, MouseQueue.Cleanroom.sdlButton(0));
        assertEquals(3, MouseQueue.Cleanroom.sdlButton(1));
        assertEquals(2, MouseQueue.Cleanroom.sdlButton(2));
        assertEquals(4, MouseQueue.Cleanroom.sdlButton(3));
    }

    @Test
    @DisplayName("the game's own key state reads back what is written, and ignores codes outside it")
    void lwjgl2KeyState() {
        RawKeys keys = new RawKeys.Lwjgl2(ByteBuffer.allocate(256));
        assertFalse(keys.isDown(57));
        keys.setDown(57, true);
        assertTrue(keys.isDown(57));
        keys.setDown(57, false);
        assertFalse(keys.isDown(57));
        keys.setDown(-100, true);
        keys.setDown(300, true);
        assertFalse(keys.isDown(-100), "a mouse button has no key state");
        assertFalse(keys.isDown(300));
    }

    @Test
    @DisplayName("a binding's mouse button: 100 below zero is button 0; keys and no key have none")
    void mouseButtons() {
        assertEquals(0, MouseQueue.button(-100));
        assertEquals(1, MouseQueue.button(-99));
        assertEquals(2, MouseQueue.button(-98));
        assertEquals(15, MouseQueue.button(-85));
        assertEquals(-1, MouseQueue.button(-84));
        assertEquals(-1, MouseQueue.button(-101));
        assertEquals(-1, MouseQueue.button(0));
        assertEquals(-1, MouseQueue.button(57));
    }
}
