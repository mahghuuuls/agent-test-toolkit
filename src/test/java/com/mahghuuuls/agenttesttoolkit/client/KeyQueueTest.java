package com.mahghuuuls.agenttesttoolkit.client;

import com.mahghuuuls.agenttesttoolkit.client.PressStages.Act;
import com.mahghuuuls.agenttesttoolkit.client.PressStages.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lwjgl.input.Keyboard;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The plain parts of the keyboard queue: the event layout LWJGL 2's {@code Keyboard.next()}
 * reads, the keys each library can carry, the choice of a backend in a game that has none, and
 * the life of a queued key's down and up events.
 */
class KeyQueueTest {

    /** One event read back in the order and widths the library's own reader uses. */
    private static Object[] readAsTheLibraryDoes(ByteBuffer buffer) {
        int key = buffer.getInt() & 0xFF;
        boolean state = buffer.get() != 0;
        int character = buffer.getInt();
        long nanos = buffer.getLong();
        boolean repeat = buffer.get() == 1;
        return new Object[] {key, state, character, nanos, repeat};
    }

    @Test
    @DisplayName("the event size is the running library's own")
    void eventSize() {
        assertEquals(Keyboard.EVENT_SIZE, KeyQueue.Lwjgl2.EVENT_SIZE);
    }

    @Test
    @DisplayName("a key event is written after the unread ones, in the 18 bytes the library reads: key, state, character, time, repeat")
    void lwjgl2EventLayout() {
        int size = KeyQueue.Lwjgl2.EVENT_SIZE;
        ByteBuffer buffer = ByteBuffer.allocate(size * 3);
        buffer.limit(size);
        buffer.put(0, (byte) 9);
        KeyQueue.Lwjgl2.append(buffer, Keyboard.KEY_H, true, 0, 77L);
        KeyQueue.Lwjgl2.append(buffer, Keyboard.KEY_H, false, 0, 78L);
        assertEquals(0, buffer.position(), "the unread event stays first");
        assertEquals(3 * size, buffer.limit());
        buffer.position(size);
        assertEquals(Arrays.asList((Object) Keyboard.KEY_H, true, 0, 77L, false),
                Arrays.asList(readAsTheLibraryDoes(buffer)), "down, no character, not a repeat");
        assertEquals(Arrays.asList((Object) Keyboard.KEY_H, false, 0, 78L, false),
                Arrays.asList(readAsTheLibraryDoes(buffer)), "then up");
        assertFalse(buffer.hasRemaining());
    }

    @Test
    @DisplayName("each field sits at its byte offset: key 0, state 4, character 5, time 9, repeat 17")
    void lwjgl2Offsets() {
        ByteBuffer buffer = ByteBuffer.allocate(KeyQueue.Lwjgl2.EVENT_SIZE);
        buffer.limit(0);
        KeyQueue.Lwjgl2.append(buffer, 200, true, 'h', 0x0102030405060708L);
        assertEquals(200, buffer.getInt(0));
        assertEquals(1, buffer.get(4));
        assertEquals('h', buffer.getInt(5));
        assertEquals(0x0102030405060708L, buffer.getLong(9));
        assertEquals(0, buffer.get(17), "never a repeat, which the game would skip");
    }

    @Test
    @DisplayName("a full buffer's read events are dropped to make room; one with no room left refuses")
    void lwjgl2Compacts() {
        int size = KeyQueue.Lwjgl2.EVENT_SIZE;
        ByteBuffer buffer = ByteBuffer.allocate(size * 2);
        buffer.limit(size * 2);
        buffer.position(size);
        buffer.put(size, (byte) 5);
        KeyQueue.Lwjgl2.append(buffer, Keyboard.KEY_SPACE, true, 0, 1L);
        assertEquals(0, buffer.position());
        assertEquals(2 * size, buffer.limit());
        assertEquals(5, buffer.get(0), "the unread event moved to the front");
        assertEquals(Keyboard.KEY_SPACE, buffer.getInt(size), "then the new one");
        assertThrows(IllegalStateException.class,
                () -> KeyQueue.Lwjgl2.append(buffer, Keyboard.KEY_SPACE, false, 0, 2L));
    }

    @Test
    @DisplayName("LWJGL 2 carries the key codes 1 to 255; a character-only key or a mouse button has none")
    void lwjgl2Reaches() {
        KeyQueue queue = new KeyQueue.Lwjgl2(null);
        assertTrue(queue.reaches(Keyboard.KEY_ESCAPE));
        assertTrue(queue.reaches(255));
        assertFalse(queue.reaches(0), "no key");
        assertFalse(queue.reaches(256 + 'h'), "the game's code for a key known only by its character");
        assertFalse(queue.reaches(-100), "a mouse button");
    }

    @Test
    @DisplayName("in a game with neither library's queue, no backend is chosen and both failures are named")
    void noBackend() {
        RouteState state = new RouteState();
        // The test's LWJGL 2 keyboard is never made (and cannot start without a display), so
        // its read buffer is unusable; Cleanroom is absent.
        assertNull(QueueChoice.find(KeyQueue.Lwjgl2::find, KeyQueue.Cleanroom::find, state));
        assertEquals("routeUnavailable", state.refusal());
        String named = state.takeFailureName();
        assertTrue(named.matches("[A-Za-z]+,ClassNotFoundException"), named);
    }

    @Test
    @DisplayName("a queued key: its down goes in at a tick start, its up at the next, and the binding is let go a tick later unless the real key is down")
    void queuedKeyLife() {
        Stage[] stage = {Stage.QUEUE_DUE};
        List<Act> acts = new ArrayList<Act>();
        acts.add(start(stage, null));
        acts.add(end(stage));
        acts.add(start(stage, null));
        acts.add(start(stage, null));
        assertEquals(Arrays.asList(Act.ADD_PRESS, Act.LET_GO, Act.ADD_RELEASE, Act.UNPRESS_IF_UP), acts);
        assertEquals(Stage.OVER, stage[0]);
    }

    @Test
    @DisplayName("a key-down due while blocked is cancelled for each reason; a hold's key-up due then lets the binding go directly")
    void blockedKey() {
        for (String blocked : new String[] {
                PressStages.blocked(false, false, false),
                PressStages.blocked(true, true, false),
                PressStages.blocked(true, false, true)}) {
            Stage[] down = {Stage.QUEUE_DUE};
            assertEquals(Act.CANCEL, start(down, blocked), blocked);
            assertEquals(Stage.OVER, down[0]);
            // An ended hold's key-up starts here and never writes a record of its own.
            Stage[] up = {Stage.QUEUE_UP_DUE};
            assertEquals(Act.UNPRESS, start(up, blocked), blocked);
            assertEquals(Stage.OVER, up[0]);
        }
        assertEquals(Arrays.asList("noWorld", "screenOpen", "noFocus"), Arrays.asList(
                PressStages.blocked(false, false, false), PressStages.blocked(true, true, false),
                PressStages.blocked(true, false, true)));
    }

    private static Act start(Stage[] stage, String blocked) {
        Act act = PressStages.atStart(stage[0], blocked);
        stage[0] = PressStages.after(stage[0], act, true);
        return act;
    }

    private static Act end(Stage[] stage) {
        Act act = PressStages.atEnd(stage[0]);
        stage[0] = PressStages.after(stage[0], act, true);
        return act;
    }
}
