package com.mahghuuuls.agenttesttoolkit.client;

import org.lwjgl.input.Mouse;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

/**
 * The mouse library's queue of button events, the one the game reads each tick.
 *
 * <p>A button event put here before the game reads it is handled as a real click: Forge's
 * {@code MouseEvent} (which a mod may cancel), the key binding, the input event, and the game's
 * own click. Two libraries keep the queue in different places: LWJGL 2 in {@code Mouse}'s
 * private read buffer, and Cleanroom in the input queues of its main window. Neither is compiled
 * against; both are found by name once. Buttons are the game's: 0 left, 1 right, 2 middle.
 *
 * <p>Client thread only, at a client tick start, before the game reads the queue in that tick.
 */
interface MouseQueue {

    /** The game stores a binding on a mouse button this far below zero: button 0 is key code -100. */
    int MOUSE_KEY_OFFSET = 100;
    /** The library's mouse buttons. */
    int MAX_BUTTONS = 16;

    /** The mouse button of a binding's key code, or -1 for a binding on a keyboard key or on none. */
    static int button(int keyCode) {
        int button = keyCode + MOUSE_KEY_OFFSET;
        return keyCode < 0 && button >= 0 && button < MAX_BUTTONS ? button : -1;
    }

    /** {@code lwjgl2} or {@code cleanroom}, for records. */
    String name();

    /** Puts one button event at the end of the queue. */
    void add(int button, boolean down);

    /** The backend of this launch, found once the mouse exists. */
    final class Choice {
        private static boolean resolved;
        private static MouseQueue backend;
        private static final RouteState STATE = new RouteState();

        private Choice() {
        }

        /** The backend, or null when none works in this game or the mouse is not made yet. */
        static synchronized MouseQueue get() {
            if (!resolved) {
                if (!Mouse.isCreated()) {
                    return null;
                }
                resolved = true;
                backend = find();
            }
            return STATE.isFailed() ? null : backend;
        }

        static synchronized RouteState state() {
            get();
            return STATE;
        }

        /** Adding an event failed in use; the route is refused from now on. */
        static synchronized void failed(RuntimeException failure) {
            STATE.fail(failure.getClass().getSimpleName());
        }

        private static MouseQueue find() {
            String lwjgl2Failure;
            try {
                return Lwjgl2.find();
            } catch (ReflectiveOperationException | RuntimeException | LinkageError notLwjgl2) {
                lwjgl2Failure = notLwjgl2.getClass().getSimpleName();
            }
            try {
                return Cleanroom.find();
            } catch (ReflectiveOperationException | RuntimeException | LinkageError notCleanroom) {
                STATE.fail(lwjgl2Failure + "," + notCleanroom.getClass().getSimpleName());
            }
            return null;
        }
    }

    /**
     * LWJGL 2: the private {@code Mouse.readBuffer}, which {@code Mouse.next()} reads from its
     * position to its limit, 22 bytes an event: the button (a byte), the state (a byte, 1 down),
     * two ints that are the movement while the mouse is grabbed and the position otherwise, the
     * wheel (an int), and the time in nanoseconds (a long). An event is written after the limit
     * and the limit moved past it.
     */
    final class Lwjgl2 implements MouseQueue {
        static final int EVENT_SIZE = 22;
        private final ByteBuffer buffer;

        Lwjgl2(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        static Lwjgl2 find() throws ReflectiveOperationException {
            Field size = Mouse.class.getDeclaredField("EVENT_SIZE");
            if (size.getInt(null) != EVENT_SIZE) {
                throw new NoSuchFieldException("EVENT_SIZE");
            }
            Field field = Mouse.class.getDeclaredField("readBuffer");
            field.setAccessible(true);
            Object value = field.get(null);
            if (!(value instanceof ByteBuffer)) {
                throw new NoSuchFieldException("readBuffer");
            }
            return new Lwjgl2((ByteBuffer) value);
        }

        @Override
        public String name() {
            return "lwjgl2";
        }

        @Override
        public void add(int button, boolean down) {
            boolean grabbed = Mouse.isGrabbed();
            append(buffer, button, down, grabbed ? 0 : Mouse.getX(), grabbed ? 0 : Mouse.getY(), System.nanoTime());
        }

        /** Writes one event after the unread ones; kept apart so its layout is tested. */
        static void append(ByteBuffer buffer, int button, boolean down, int first, int second, long nanos) {
            if (buffer.limit() + EVENT_SIZE > buffer.capacity()) {
                // Moves the unread events to the front, as the library does before it reads more.
                buffer.compact();
                buffer.flip();
            }
            int at = buffer.limit();
            if (at + EVENT_SIZE > buffer.capacity()) {
                throw new IllegalStateException("queueFull");
            }
            buffer.limit(at + EVENT_SIZE);
            buffer.put(at, (byte) button);
            buffer.put(at + 1, (byte) (down ? 1 : 0));
            buffer.putInt(at + 2, first);
            buffer.putInt(at + 6, second);
            buffer.putInt(at + 10, 0);
            buffer.putLong(at + 14, nanos);
        }
    }

    /**
     * Cleanroom: the package-private {@code button(int, boolean, float, float, long)} of the main
     * window's {@code legacy} input queues ({@code com.cleanroommc.client.sdl.LegacyInputQueues}),
     * which take SDL's button numbers (1 left, 2 middle, 3 right) and the position in pixels, as
     * the window's own button handler gives them.
     */
    final class Cleanroom implements MouseQueue {
        private final Object window;
        private final Object queues;
        private final Method button;
        private final Method mouseX;
        private final Method mouseY;
        private final Method pixelX;
        private final Method pixelY;

        private Cleanroom(Object window, Object queues, Method button, Method mouseX, Method mouseY, Method pixelX,
                          Method pixelY) {
            this.window = window;
            this.queues = queues;
            this.button = button;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
            this.pixelX = pixelX;
            this.pixelY = pixelY;
        }

        static Cleanroom find() throws ReflectiveOperationException {
            Object window = RawKeys.CleanroomWindow.main();
            Class<?> type = window.getClass();
            Field legacy = type.getDeclaredField("legacy");
            legacy.setAccessible(true);
            Object queues = legacy.get(window);
            Method button = queues.getClass().getDeclaredMethod("button", int.class, boolean.class, float.class,
                    float.class, long.class);
            button.setAccessible(true);
            Method pixelX = type.getDeclaredMethod("pixelX", float.class);
            pixelX.setAccessible(true);
            Method pixelY = type.getDeclaredMethod("pixelY", float.class);
            pixelY.setAccessible(true);
            return new Cleanroom(window, queues, button, type.getMethod("mouseX"), type.getMethod("mouseY"), pixelX,
                    pixelY);
        }

        /** The game's button number in SDL's numbering: right and middle trade places, and SDL counts from 1. */
        static int sdlButton(int button) {
            if (button == 1) {
                return 3;
            }
            if (button == 2) {
                return 2;
            }
            return button + 1;
        }

        @Override
        public String name() {
            return "cleanroom";
        }

        @Override
        public void add(int button, boolean down) {
            try {
                float x = ((Float) pixelX.invoke(window, mouseX.invoke(window))).floatValue();
                float y = ((Float) pixelY.invoke(window, mouseY.invoke(window))).floatValue();
                this.button.invoke(queues, sdlButton(button), down, x, y, System.nanoTime());
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(failure.getClass().getSimpleName(), failure);
            }
        }
    }
}
