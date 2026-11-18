package com.mahghuuuls.agenttesttoolkit.client;

import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

/**
 * The keyboard library's queue of key events, the one the game reads each tick.
 *
 * <p>A key event put here before the game reads it is handled as a real key: the game sets and
 * counts the key's bindings itself, and Forge's {@code KeyInputEvent} is posted by the game with
 * this event as the current keyboard event, so a mod that reads the key in that event sees this
 * key and its state. Building and posting that event ourselves would not do: its listeners read
 * the keyboard's current event, which would still be the last real one. Two libraries keep the
 * queue in different places: LWJGL 2 in {@code Keyboard}'s private read buffer, and Cleanroom in
 * the input queues of its main window. Neither is compiled against; both are found by name once
 * (see {@link QueueChoice}). An event carries no typed character (0): only a screen reads one,
 * and nothing is queued while a screen is open. Its time is {@code System.nanoTime()}, not the
 * clock of the library's own events (LWJGL 2 stamps them with the time the operating system
 * gave, Cleanroom with SDL's): the game reads no key event's time, and a mod that compares it
 * with a real event's time sees an unrelated number.
 *
 * <p>Client thread only, at a client tick start, before the game reads the queue in that tick.
 */
interface KeyQueue {

    /**
     * Whether the library can carry an event of this key: one it has a code for. A binding on a
     * key the game knows only by its character (a code above 255) has none.
     */
    boolean reaches(int keyCode);

    /** Puts one event of a key, by the game's key code, at the end of the queue; only a key it {@link #reaches}. */
    void add(int keyCode, boolean down);

    /** The backend of this launch, found once the keyboard exists. */
    // Lambdas, not method references: the keyboard classes are loaded only when asked.
    QueueChoice<KeyQueue> CHOICE = new QueueChoice<KeyQueue>(() -> Keyboard.isCreated(), () -> Lwjgl2.find(),
            () -> Cleanroom.find());

    /**
     * LWJGL 2: the private {@code Keyboard.readBuffer}, which {@code Keyboard.next()} reads from
     * its position to its limit, 18 bytes an event: the key code (an int, of which the library
     * keeps the low byte), the state (a byte, not 0 for down), the character (an int), the time
     * in nanoseconds (a long), and whether it is a repeat (a byte, 1 for a repeat, which the
     * game skips unless repeats are on). An event is written after the limit and the limit moved
     * past it, as for the mouse.
     */
    final class Lwjgl2 implements KeyQueue {
        static final int EVENT_SIZE = 18;
        /** The field, not the buffer: the library makes a new buffer when it makes the keyboard again. */
        private final Field readBuffer;

        /** For tests, which need no buffer, and for the field found by name. */
        Lwjgl2(Field readBuffer) {
            this.readBuffer = readBuffer;
        }

        static Lwjgl2 find() throws ReflectiveOperationException {
            // Read from the running library, not from the constant this class was compiled with.
            Field size = Keyboard.class.getDeclaredField("EVENT_SIZE");
            if (size.getInt(null) != EVENT_SIZE) {
                throw new NoSuchFieldException("EVENT_SIZE");
            }
            Field field = Keyboard.class.getDeclaredField("readBuffer");
            field.setAccessible(true);
            if (!(field.get(null) instanceof ByteBuffer)) {
                throw new NoSuchFieldException("readBuffer");
            }
            return new Lwjgl2(field);
        }

        /** The library keeps only the low byte of a key code. */
        @Override
        public boolean reaches(int keyCode) {
            return keyCode > 0 && keyCode < 256;
        }

        @Override
        public void add(int keyCode, boolean down) {
            append(QueueChoice.readBuffer(readBuffer), keyCode, down, 0, System.nanoTime());
        }

        /** Writes one event, never a repeat, after the unread ones; kept apart so its layout is tested. */
        static void append(ByteBuffer buffer, int keyCode, boolean down, int character, long nanos) {
            int at = QueueChoice.reserve(buffer, EVENT_SIZE);
            buffer.putInt(at, keyCode);
            buffer.put(at + 4, (byte) (down ? 1 : 0));
            buffer.putInt(at + 5, character);
            buffer.putLong(at + 9, nanos);
            buffer.put(at + 17, (byte) 0);
        }
    }

    /**
     * Cleanroom: the package-private {@code key(int, int, boolean, boolean, char, long)} of the
     * main window's {@code legacy} input queues ({@code com.cleanroommc.client.sdl.LegacyInputQueues}):
     * the SDL scan code, SDL's key symbol, down, repeat, the character and the time, as the
     * window's own key handler gives them. The keyboard bridge turns the scan code back into the
     * game's key code ({@code Scancodes.toLwjgl}) and reads nothing of the key symbol, so it is 0,
     * as in the window's own release of every key when it loses focus.
     */
    final class Cleanroom implements KeyQueue {
        private final Object queues;
        private final Method key;
        private final Method toSdl;

        private Cleanroom(Object queues, Method key, Method toSdl) {
            this.queues = queues;
            this.key = key;
            this.toSdl = toSdl;
        }

        static Cleanroom find() throws ReflectiveOperationException {
            Object window = RawKeys.CleanroomWindow.main();
            Field legacy = window.getClass().getDeclaredField("legacy");
            legacy.setAccessible(true);
            Object queues = legacy.get(window);
            Method key = queues.getClass().getDeclaredMethod("key", int.class, int.class, boolean.class,
                    boolean.class, char.class, long.class);
            key.setAccessible(true);
            Class<?> scancodes = Class.forName("com.cleanroommc.lwjgly.rt.Scancodes", false,
                    KeyQueue.class.getClassLoader());
            return new Cleanroom(queues, key, scancodes.getMethod("toSdl", int.class));
        }

        /** A key without a scan code in the bridge would reach the game as key 0. */
        @Override
        public boolean reaches(int keyCode) {
            return keyCode > 0 && scancode(keyCode) > 0;
        }

        @Override
        public void add(int keyCode, boolean down) {
            try {
                key.invoke(queues, scancode(keyCode), 0, down, false, (char) 0, System.nanoTime());
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(failure.getClass().getSimpleName(), failure);
            }
        }

        private int scancode(int keyCode) {
            try {
                return ((Integer) toSdl.invoke(null, keyCode)).intValue();
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(failure.getClass().getSimpleName(), failure);
            }
        }
    }
}
