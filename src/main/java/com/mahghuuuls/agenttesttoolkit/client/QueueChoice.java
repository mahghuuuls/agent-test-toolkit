package com.mahghuuuls.agenttesttoolkit.client;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;

/**
 * The backend of one input queue, the mouse's or the keyboard's, for this launch: LWJGL 2's if
 * this game has it, else Cleanroom's, looked for once the device exists. A backend that fails
 * while an event is added is refused for the rest of the launch (see {@link RouteState}), so a
 * half-working reflection is never tried again.
 *
 * <p>Also the one place where an event is fitted into LWJGL 2's read buffer, which both queues
 * share in layout: the unread events run from the position to the limit, and a new one goes
 * after the limit.
 */
final class QueueChoice<T> {

    /** Whether the device the queue belongs to has been made yet. */
    interface Device {
        boolean isCreated();
    }

    /** Finds one library's backend; throws when this game does not have that library's queue. */
    interface Finder<T> {
        T find() throws ReflectiveOperationException;
    }

    /** One event added through the backend. */
    interface Adder<T> {
        void add(T queue);
    }

    private final Device device;
    private final Finder<T> lwjgl2;
    private final Finder<T> cleanroom;
    private final RouteState state = new RouteState();
    private boolean resolved;
    private T backend;

    QueueChoice(Device device, Finder<T> lwjgl2, Finder<T> cleanroom) {
        this.device = device;
        this.lwjgl2 = lwjgl2;
        this.cleanroom = cleanroom;
    }

    /** The backend, or null when none works in this game or the device is not made yet. */
    synchronized T get() {
        if (!resolved) {
            if (!device.isCreated()) {
                return null;
            }
            resolved = true;
            backend = find(lwjgl2, cleanroom, state);
        }
        return state.isFailed() ? null : backend;
    }

    /** The route's state: unavailable when no backend was found or one failed in use. */
    synchronized RouteState state() {
        get();
        return state;
    }

    /** Adding an event failed in use; the route is refused from now on. */
    synchronized void failed(RuntimeException failure) {
        state.fail(failure.getClass().getSimpleName());
    }

    /**
     * Adds one event through the backend; false when there is none, or when adding threw, which
     * refuses the route from then on.
     */
    boolean add(Adder<T> adder) {
        T queue = get();
        if (queue == null) {
            return false;
        }
        try {
            adder.add(queue);
            return true;
        } catch (RuntimeException failure) {
            failed(failure);
            return false;
        }
    }

    /** Tries LWJGL 2, then Cleanroom; when neither works, {@code state} fails naming both exceptions. */
    static <T> T find(Finder<T> lwjgl2, Finder<T> cleanroom, RouteState state) {
        String lwjgl2Failure;
        try {
            return lwjgl2.find();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError notLwjgl2) {
            lwjgl2Failure = notLwjgl2.getClass().getSimpleName();
        }
        try {
            return cleanroom.find();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError notCleanroom) {
            // Named after the game's own library first: that is the one a development client has.
            state.fail(lwjgl2Failure + "," + notCleanroom.getClass().getSimpleName());
        }
        return null;
    }

    /**
     * The LWJGL 2 read buffer the field holds now. Read again for every event, never kept: the
     * library makes a new buffer each time it makes the device again, as when the window is made
     * again for full screen or a new display mode, and an event put in the old one is never read.
     *
     * @throws IllegalStateException {@code readBuffer} when the field holds no buffer
     */
    static ByteBuffer readBuffer(Field field) {
        Object value;
        try {
            value = field.get(null);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException(failure.getClass().getSimpleName(), failure);
        }
        if (!(value instanceof ByteBuffer)) {
            throw new IllegalStateException("readBuffer");
        }
        return (ByteBuffer) value;
    }

    /**
     * Makes room for one event of {@code size} bytes after the unread events of an LWJGL 2 read
     * buffer and moves the limit past it.
     *
     * @return where the event's bytes go
     * @throws IllegalStateException {@code queueFull} when the unread events leave no room
     */
    static int reserve(ByteBuffer buffer, int size) {
        if (buffer.limit() + size > buffer.capacity()) {
            // Moves the unread events to the front, as the library does before it reads more.
            buffer.compact();
            buffer.flip();
        }
        int at = buffer.limit();
        if (at + size > buffer.capacity()) {
            throw new IllegalStateException("queueFull");
        }
        buffer.limit(at + size);
        return at;
    }
}
