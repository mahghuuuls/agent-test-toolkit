package com.mahghuuuls.agenttesttoolkit.client;

import org.lwjgl.input.Keyboard;

import java.util.concurrent.Callable;

/**
 * Makes the game and other mods read Shift, Ctrl or Alt as held, for one piece of work only.
 *
 * <p>Every check of a modifier, the game's and the ones mods write themselves, ends in
 * {@code Keyboard.isKeyDown}, which reads the keyboard library's own key state; no key binding
 * reaches it. So the asked keys are written into that state through {@link RawKeys} just before
 * the work, on the client thread, and put back right after; nothing is sent, and no binding
 * changes. Where this game's keyboard library offers no such state, {@link #available()} says so
 * and the caller refuses.
 */
final class ModifierKeys {

    private ModifierKeys() {
    }

    /** True when this game's key state can be written. Decided once, once the keyboard exists. */
    static boolean available() {
        return RawKeys.Choice.get() != null;
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

    /** Runs the work with the keys read as held, and puts the state back however the work ends. */
    static <T> T during(int[] codes, Callable<T> work) throws Exception {
        RawKeys keys = RawKeys.Choice.get();
        if (codes.length == 0 || keys == null) {
            return work.call();
        }
        return during(keys, codes, work);
    }

    /**
     * The same, for work that throws nothing checked, such as building or drawing a tooltip: what
     * it throws goes on unchanged, so a mod's handler that fails is the caller's to report.
     */
    static <T> T holding(int[] codes, Callable<T> work) {
        try {
            return during(codes, work);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception unexpected) {
            throw new IllegalStateException(unexpected);
        }
    }

    /**
     * The same, on a given key state; for tests. Every key set is put back, also when the work
     * or one of the writes throws: a write that fails stops the route for later requests, and the
     * other keys are still put back.
     */
    static <T> T during(RawKeys keys, int[] codes, Callable<T> work) throws Exception {
        boolean[] before = new boolean[codes.length];
        int set = 0;
        try {
            for (; set < codes.length; set++) {
                before[set] = keys.isDown(codes[set]);
                keys.setDown(codes[set], true);
            }
            return work.call();
        } finally {
            for (int i = set - 1; i >= 0; i--) {
                try {
                    keys.setDown(codes[i], before[i]);
                } catch (RuntimeException failure) {
                    RawKeys.Choice.failed(failure);
                }
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
