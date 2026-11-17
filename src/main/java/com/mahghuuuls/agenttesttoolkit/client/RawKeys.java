package com.mahghuuuls.agenttesttoolkit.client;

import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

/**
 * The keyboard library's own state of each key, the one {@code Keyboard.isKeyDown} reads.
 *
 * <p>No key binding reaches it: the game and mods that check a key directly (a modifier for a
 * tooltip, a key a mod polls itself) read only this. Writing it is the one way to make them see a
 * key held without a real key press. Two libraries keep it in different places: LWJGL 2, the
 * game's own, in a private byte buffer it refills from the operating system once per frame; and
 * Cleanroom's LWJGL 3 bridge in its window's private key array, indexed by its own scan codes.
 * Neither is compiled against; both are found by name once, and tried by writing a key and reading
 * it back through {@code Keyboard.isKeyDown} before they are trusted.
 *
 * <p>Client thread only.
 */
interface RawKeys {

    /** {@code lwjgl2} or {@code cleanroom}, for records. */
    String name();

    /** Whether the stored state has the key down; a key code is the game's (LWJGL 2) code. */
    boolean isDown(int keyCode);

    void setDown(int keyCode, boolean down);

    /** The backend of this launch, found once the keyboard exists. */
    final class Choice {
        private static boolean resolved;
        private static RawKeys backend;
        private static final RouteState STATE = new RouteState();

        private Choice() {
        }

        /** The backend, or null when this game has none that works or the keyboard is not made yet. */
        static synchronized RawKeys get() {
            if (!resolved) {
                if (!Keyboard.isCreated()) {
                    return null;
                }
                resolved = true;
                backend = find();
            }
            return STATE.isFailed() ? null : backend;
        }

        /**
         * The backend found, also after it failed in use: a key it set down must still be let go
         * through it, since Cleanroom's window keeps a key down until something changes it.
         */
        static synchronized RawKeys resolved() {
            return backend;
        }

        /** The route's state: unavailable when no backend was found or one failed in use. */
        static synchronized RouteState state() {
            get();
            return STATE;
        }

        /** Writing failed in use; the route is refused from now on. */
        static synchronized void failed(RuntimeException failure) {
            STATE.fail(failure.getClass().getSimpleName());
        }

        private static RawKeys find() {
            String lwjgl2Failure;
            try {
                RawKeys lwjgl2 = Lwjgl2.find();
                if (readsBack(lwjgl2)) {
                    return lwjgl2;
                }
                lwjgl2Failure = "readBack";
            } catch (ReflectiveOperationException | RuntimeException | LinkageError notLwjgl2) {
                lwjgl2Failure = notLwjgl2.getClass().getSimpleName();
            }
            try {
                RawKeys cleanroom = Cleanroom.find();
                if (readsBack(cleanroom)) {
                    return cleanroom;
                }
                STATE.fail("readBack");
            } catch (ReflectiveOperationException | RuntimeException | LinkageError notCleanroom) {
                // Named after the game's own library first: that is the one a development client has.
                STATE.fail(lwjgl2Failure + "," + notCleanroom.getClass().getSimpleName());
            }
            return null;
        }

        /** Writes Left Shift, asks the keyboard, and puts the state back. */
        private static boolean readsBack(RawKeys keys) {
            boolean before = keys.isDown(Keyboard.KEY_LSHIFT);
            try {
                keys.setDown(Keyboard.KEY_LSHIFT, true);
                return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT);
            } catch (RuntimeException notThisKeyboard) {
                return false;
            } finally {
                keys.setDown(Keyboard.KEY_LSHIFT, before);
            }
        }
    }

    /** LWJGL 2: the private {@code Keyboard.keyDownBuffer}, one byte per key code. */
    final class Lwjgl2 implements RawKeys {
        private final ByteBuffer buffer;

        /** For tests, and for the buffer found by name. */
        Lwjgl2(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        static Lwjgl2 find() throws ReflectiveOperationException {
            Field field = Keyboard.class.getDeclaredField("keyDownBuffer");
            field.setAccessible(true);
            Object value = field.get(null);
            if (!(value instanceof ByteBuffer)) {
                throw new NoSuchFieldException("keyDownBuffer");
            }
            return new Lwjgl2((ByteBuffer) value);
        }

        @Override
        public String name() {
            return "lwjgl2";
        }

        @Override
        public boolean isDown(int keyCode) {
            return keyCode > 0 && keyCode < buffer.capacity() && buffer.get(keyCode) != 0;
        }

        @Override
        public void setDown(int keyCode, boolean down) {
            if (keyCode > 0 && keyCode < buffer.capacity()) {
                buffer.put(keyCode, (byte) (down ? 1 : 0));
            }
        }
    }

    /**
     * Cleanroom: the private {@code boolean[] keys} of the main {@code com.cleanroommc.client.sdl.Window},
     * indexed by the SDL scan code that {@code com.cleanroommc.lwjgly.rt.Scancodes.toSdl} gives
     * for a game key code. The window keeps a key until an SDL event or a focus loss changes it.
     */
    final class Cleanroom implements RawKeys {
        private final boolean[] keys;
        private final Method toSdl;

        private Cleanroom(boolean[] keys, Method toSdl) {
            this.keys = keys;
            this.toSdl = toSdl;
        }

        static Cleanroom find() throws ReflectiveOperationException {
            Object window = CleanroomWindow.main();
            Field keysField = window.getClass().getDeclaredField("keys");
            keysField.setAccessible(true);
            Object keys = keysField.get(window);
            if (!(keys instanceof boolean[])) {
                throw new NoSuchFieldException("keys");
            }
            Class<?> scancodes = Class.forName("com.cleanroommc.lwjgly.rt.Scancodes", false,
                    RawKeys.class.getClassLoader());
            Method toSdl = scancodes.getMethod("toSdl", int.class);
            return new Cleanroom((boolean[]) keys, toSdl);
        }

        private int index(int keyCode) {
            try {
                return ((Integer) toSdl.invoke(null, keyCode)).intValue();
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(failure);
            }
        }

        @Override
        public String name() {
            return "cleanroom";
        }

        @Override
        public boolean isDown(int keyCode) {
            int index = index(keyCode);
            return index > 0 && index < keys.length && keys[index];
        }

        @Override
        public void setDown(int keyCode, boolean down) {
            int index = index(keyCode);
            if (index > 0 && index < keys.length) {
                keys[index] = down;
            }
        }
    }

    /** Cleanroom's main window, shared with the mouse queue's backend. */
    final class CleanroomWindow {
        static final String WINDOW = "com.cleanroommc.client.sdl.Window";

        private CleanroomWindow() {
        }

        /** The main window object; fails when this game is not Cleanroom or has no window yet. */
        static Object main() throws ReflectiveOperationException {
            Class<?> type = Class.forName(WINDOW, false, RawKeys.class.getClassLoader());
            Field main = type.getDeclaredField("main");
            main.setAccessible(true);
            Object window = main.get(null);
            if (window == null) {
                throw new IllegalStateException("noWindow");
            }
            return window;
        }
    }
}
