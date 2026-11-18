package com.mahghuuuls.agenttesttoolkit.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The backend choice and the read-buffer room the mouse and keyboard queues share. */
class QueueChoiceTest {

    /** Stands in for the library's static read buffer, which it replaces when it makes the device again. */
    static ByteBuffer buffer;

    private static final QueueChoice.Finder<String> NO_LIBRARY = () -> {
        throw new ClassNotFoundException("absent");
    };

    @Test
    @DisplayName("LWJGL 2's backend first, Cleanroom's when LWJGL 2's is not there, neither when both fail")
    void findsInOrder() {
        RouteState state = new RouteState();
        assertEquals("lwjgl2", QueueChoice.find(() -> "lwjgl2", () -> "cleanroom", state));
        assertEquals("cleanroom", QueueChoice.find(() -> {
            throw new NoSuchFieldException("readBuffer");
        }, () -> "cleanroom", state));
        assertNull(state.refusal(), "a backend was found");
        assertNull(QueueChoice.find(() -> {
            throw new NoSuchFieldException("readBuffer");
        }, NO_LIBRARY, state));
        assertEquals("NoSuchFieldException,ClassNotFoundException", state.takeFailureName());
    }

    @Test
    @DisplayName("nothing is chosen before the device exists, and the choice is made once")
    void resolvedOnceTheDeviceExists() {
        boolean[] created = {false};
        int[] finds = {0};
        QueueChoice<String> choice = new QueueChoice<String>(() -> created[0], () -> {
            finds[0]++;
            return "lwjgl2";
        }, NO_LIBRARY);
        assertNull(choice.get());
        assertEquals(0, finds[0], "not looked for yet");
        created[0] = true;
        assertEquals("lwjgl2", choice.get());
        assertEquals("lwjgl2", choice.get());
        assertEquals(1, finds[0]);
    }

    @Test
    @DisplayName("an add that throws refuses the route from then on and names the exception once")
    void failedAddRefusesTheRoute() {
        QueueChoice<List<String>> choice = new QueueChoice<List<String>>(() -> true, ArrayList::new, () -> null);
        assertTrue(choice.add(queue -> queue.add("down")));
        assertFalse(choice.add(queue -> {
            throw new IllegalStateException("queueFull");
        }));
        assertNull(choice.get());
        assertFalse(choice.add(queue -> queue.add("up")), "never tried again");
        assertEquals("IllegalStateException", choice.state().takeFailureName());
        assertNull(choice.state().takeFailureName());
    }

    @Test
    @DisplayName("the read buffer is read from its field for every event, so a buffer made again gets the next one")
    void bufferReadEachTime() throws Exception {
        Field field = QueueChoiceTest.class.getDeclaredField("buffer");
        buffer = ByteBuffer.allocate(KeyQueue.Lwjgl2.EVENT_SIZE);
        ByteBuffer first = QueueChoice.readBuffer(field);
        assertSame(buffer, first);
        buffer = ByteBuffer.allocate(KeyQueue.Lwjgl2.EVENT_SIZE);
        assertSame(buffer, QueueChoice.readBuffer(field), "the window was made again: the new buffer");
        buffer = null;
        assertThrows(IllegalStateException.class, () -> QueueChoice.readBuffer(field));
    }

    @Test
    @DisplayName("room for an event: after the unread ones, the read ones dropped when full, refused when no room is left")
    void reserve() {
        ByteBuffer room = ByteBuffer.allocate(8);
        room.limit(2);
        room.position(1);
        assertEquals(2, QueueChoice.reserve(room, 3));
        assertEquals(5, room.limit());
        assertEquals(1, room.position(), "nothing moved while there was room");
        assertEquals(4, QueueChoice.reserve(room, 4), "the read byte dropped, the four unread moved to the front");
        assertEquals(0, room.position());
        assertEquals(8, room.limit());
        assertThrows(IllegalStateException.class, () -> QueueChoice.reserve(room, 1), "queueFull");
    }
}
