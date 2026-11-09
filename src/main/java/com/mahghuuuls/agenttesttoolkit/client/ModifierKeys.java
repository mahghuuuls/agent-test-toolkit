package com.mahghuuuls.agenttesttoolkit.client;

import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.concurrent.Callable;

/**
 * Makes the game and other mods read Shift, Ctrl or Alt as held, for one piece of work only.
 *
 * <p>Every check of a modifier, the game's and the ones mods write themselves, ends in
 * {@code Keyboard.isKeyDown}, which reads a private buffer the keyboard library refreshes from
 * the operating system once per frame. No key binding reaches it. So the asked keys are written
 * into that buffer just before the work, on the client thread, and the bytes are put back right
 * after; nothing is sent, and no binding changes. Whether this works is tried, not assumed: a
 * key is written and read back through {@code Keyboard.isKeyDown}. Where it does not, as with
 * another keyboard library, {@link #available()} says so and the caller refuses.
 */
final class ModifierKeys {

    private static boolean resolved;
    private static ByteBuffer buffer;

    private ModifierKeys() {
    }

    /**
     * True when writing the key buffer makes {@code Keyboard.isKeyDown} read the key as held.
     * Tried once, on the client thread, once the keyboard exists.
     */
    static synchronized boolean available() {
        if (!resolved) {
            if (!Keyboard.isCreated()) {
                return false;
            }
            resolved = true;
            ByteBuffer found = find();
            buffer = found != null && readsBack(found) ? found : null;
        }
        return buffer != null;
    }

    private static ByteBuffer find() {
        try {
            Field field = Keyboard.class.getDeclaredField("keyDownBuffer");
            field.setAccessible(true);
            Object value = field.get(null);
            return value instanceof ByteBuffer ? (ByteBuffer) value : null;
        } catch (ReflectiveOperationException | RuntimeException unreachable) {
            return null;
        }
    }

    /** Writes Left Shift, asks the keyboard, and puts the byte back. */
    private static boolean readsBack(ByteBuffer found) {
        byte before = found.get(Keyboard.KEY_LSHIFT);
        try {
            found.put(Keyboard.KEY_LSHIFT, (byte) 1);
            return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT);
        } catch (RuntimeException notThisKeyboard) {
            return false;
        } finally {
            found.put(Keyboard.KEY_LSHIFT, before);
        }
    }

    /**
     * The key codes of {@code shift,ctrl,alt} words. Ctrl is also the Command key, which the
     * game reads for Ctrl on macOS.
     */
    static int[] codes(String keys) {
        if (keys == null || keys.isEmpty()) {
            return new int[0];
        }
        java.util.List<Integer> codes = new java.util.ArrayList<Integer>();
        for (String word : keys.split(",")) {
            if ("shift".equals(word)) {
                codes.add(Keyboard.KEY_LSHIFT);
            } else if ("ctrl".equals(word)) {
                codes.add(Keyboard.KEY_LCONTROL);
                codes.add(Keyboard.KEY_LMETA);
            } else if ("alt".equals(word)) {
                codes.add(Keyboard.KEY_LMENU);
            }
        }
        int[] out = new int[codes.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = codes.get(i);
        }
        return out;
    }

    /** Runs the work with the keys read as held, and puts the buffer back however the work ends. */
    static <T> T during(int[] codes, Callable<T> work) throws Exception {
        if (codes.length == 0 || !available()) {
            return work.call();
        }
        return during(buffer, codes, work);
    }

    /** The same, on a given buffer; for tests. */
    static <T> T during(ByteBuffer target, int[] codes, Callable<T> work) throws Exception {
        byte[] before = new byte[codes.length];
        for (int i = 0; i < codes.length; i++) {
            before[i] = target.get(codes[i]);
            target.put(codes[i], (byte) 1);
        }
        try {
            return work.call();
        } finally {
            for (int i = codes.length - 1; i >= 0; i--) {
                target.put(codes[i], before[i]);
            }
        }
    }

    /** Which of shift, ctrl, alt the game reads as held right now, comma separated. */
    static String seen() {
        StringBuilder out = new StringBuilder();
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            out.append("shift");
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)
                || Keyboard.isKeyDown(Keyboard.KEY_LMETA)) {
            out.append(out.length() == 0 ? "" : ",").append("ctrl");
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU)) {
            out.append(out.length() == 0 ? "" : ",").append("alt");
        }
        return out.toString();
    }
}
