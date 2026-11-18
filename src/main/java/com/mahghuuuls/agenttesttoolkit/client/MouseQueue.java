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
 * against; both are found by name once (see {@link QueueChoice}). Buttons are the game's: 0
 * left, 1 right, 2 middle. An event carries {@code System.nanoTime()} as its time, not the
 * clock of the library's own events (LWJGL 2 stamps them with the time the operating system
 * gave, Cleanroom with SDL's): the game reads no button event's time, and a mod that compares
 * it with a real event's time sees an unrelated number.
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

    /** Puts one button event at the end of the queue. */
    void add(int button, boolean down);

    /**
     * Puts one wheel event at the end of the queue, at the mouse's position: {@code notches}
     * positive turns away from the player, as the wheel's own sign.
     */
    void wheel(int notches);

    /** LWJGL 2's wheel amount for one notch, as the operating system reports it. */
    int WHEEL_PER_NOTCH = 120;

    /** The backend of this launch, found once the mouse exists. */
    // Lambdas, not method references: the mouse classes are loaded only when asked, not when
    // this interface is first used for its button numbers.
    QueueChoice<MouseQueue> CHOICE = new QueueChoice<MouseQueue>(() -> Mouse.isCreated(), () -> Lwjgl2.find(),
            () -> Cleanroom.find());

    /**
     * LWJGL 2: the private {@code Mouse.readBuffer}, which {@code Mouse.next()} reads from its
     * position to its limit, 22 bytes an event: the button (a byte), the state (a byte, 1 down),
     * two ints that are the movement while the mouse is grabbed and the position otherwise, the
     * wheel (an int), and the time in nanoseconds (a long). An event is written after the limit
     * and the limit moved past it.
     */
    final class Lwjgl2 implements MouseQueue {
        static final int EVENT_SIZE = 22;
        /** The field, not the buffer: the library makes a new buffer when it makes the mouse again. */
        private final Field readBuffer;

        private Lwjgl2(Field readBuffer) {
            this.readBuffer = readBuffer;
        }

        static Lwjgl2 find() throws ReflectiveOperationException {
            Field size = Mouse.class.getDeclaredField("EVENT_SIZE");
            if (size.getInt(null) != EVENT_SIZE) {
                throw new NoSuchFieldException("EVENT_SIZE");
            }
            Field field = Mouse.class.getDeclaredField("readBuffer");
            field.setAccessible(true);
            if (!(field.get(null) instanceof ByteBuffer)) {
                throw new NoSuchFieldException("readBuffer");
            }
            return new Lwjgl2(field);
        }

        @Override
        public void add(int button, boolean down) {
            boolean grabbed = Mouse.isGrabbed();
            append(QueueChoice.readBuffer(readBuffer), button, down, grabbed ? 0 : Mouse.getX(),
                    grabbed ? 0 : Mouse.getY(), System.nanoTime());
        }

        @Override
        public void wheel(int notches) {
            boolean grabbed = Mouse.isGrabbed();
            // No button: the library's -1, and the wheel's amount where a button event has 0.
            append(QueueChoice.readBuffer(readBuffer), -1, false, grabbed ? 0 : Mouse.getX(),
                    grabbed ? 0 : Mouse.getY(), notches * WHEEL_PER_NOTCH, System.nanoTime());
        }

        /** Writes one event after the unread ones; kept apart so its layout is tested. */
        static void append(ByteBuffer buffer, int button, boolean down, int first, int second, long nanos) {
            append(buffer, button, down, first, second, 0, nanos);
        }

        /** The same, with a wheel amount. */
        static void append(ByteBuffer buffer, int button, boolean down, int first, int second, int wheel, long nanos) {
            int at = QueueChoice.reserve(buffer, EVENT_SIZE);
            buffer.put(at, (byte) button);
            buffer.put(at + 1, (byte) (down ? 1 : 0));
            buffer.putInt(at + 2, first);
            buffer.putInt(at + 6, second);
            buffer.putInt(at + 10, wheel);
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
        private final Method wheel;
        private final Method mouseX;
        private final Method mouseY;
        private final Method pixelX;
        private final Method pixelY;

        private Cleanroom(Object window, Object queues, Method button, Method wheel, Method mouseX, Method mouseY,
                          Method pixelX, Method pixelY) {
            this.window = window;
            this.queues = queues;
            this.button = button;
            this.wheel = wheel;
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
            // The window's own wheel handler gives the position in pixels and one per notch.
            Method wheel = queues.getClass().getDeclaredMethod("wheel", float.class, float.class, float.class,
                    long.class);
            wheel.setAccessible(true);
            Method pixelX = type.getDeclaredMethod("pixelX", float.class);
            pixelX.setAccessible(true);
            Method pixelY = type.getDeclaredMethod("pixelY", float.class);
            pixelY.setAccessible(true);
            return new Cleanroom(window, queues, button, wheel, type.getMethod("mouseX"), type.getMethod("mouseY"),
                    pixelX, pixelY);
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
        public void add(int button, boolean down) {
            try {
                float x = ((Float) pixelX.invoke(window, mouseX.invoke(window))).floatValue();
                float y = ((Float) pixelY.invoke(window, mouseY.invoke(window))).floatValue();
                this.button.invoke(queues, sdlButton(button), down, x, y, System.nanoTime());
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(failure.getClass().getSimpleName(), failure);
            }
        }

        @Override
        public void wheel(int notches) {
            try {
                float x = ((Float) pixelX.invoke(window, mouseX.invoke(window))).floatValue();
                float y = ((Float) pixelY.invoke(window, mouseY.invoke(window))).floatValue();
                this.wheel.invoke(queues, x, y, (float) notches, System.nanoTime());
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(failure.getClass().getSimpleName(), failure);
            }
        }
    }
}
