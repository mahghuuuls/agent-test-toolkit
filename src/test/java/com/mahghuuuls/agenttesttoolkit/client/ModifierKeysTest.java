package com.mahghuuuls.agenttesttoolkit.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lwjgl.input.Keyboard;

import java.nio.ByteBuffer;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The modifier keys: which codes each word holds, and that the key state always gets its keys back. */
class ModifierKeysTest {

    @Test
    @DisplayName("shift, ctrl (with the Command key macOS reads for Ctrl) and alt map to their left keys")
    void codes() {
        assertArrayEquals(new int[] {Keyboard.KEY_LSHIFT}, ModifierKeys.codes("shift"));
        assertArrayEquals(new int[] {Keyboard.KEY_LSHIFT, Keyboard.KEY_LCONTROL, Keyboard.KEY_LMETA, Keyboard.KEY_LMENU},
                ModifierKeys.codes("shift,ctrl,alt"));
        assertArrayEquals(new int[0], ModifierKeys.codes(""));
        assertArrayEquals(new int[0], ModifierKeys.codes(null));
    }

    @Test
    @DisplayName("the keys read as held during the work, and the earlier bytes come back after it, also when it throws")
    void heldOnlyDuring() throws Exception {
        final ByteBuffer buffer = ByteBuffer.allocate(256);
        buffer.put(Keyboard.KEY_LMENU, (byte) 1);
        final int[] codes = {Keyboard.KEY_LSHIFT, Keyboard.KEY_LMENU};
        Byte during = ModifierKeys.during(new RawKeys.Lwjgl2(buffer), codes, new Callable<Byte>() {
            @Override
            public Byte call() {
                return buffer.get(Keyboard.KEY_LSHIFT);
            }
        });
        assertEquals(1, during.byteValue());
        assertEquals(0, buffer.get(Keyboard.KEY_LSHIFT), "put back");
        assertEquals(1, buffer.get(Keyboard.KEY_LMENU), "a key already down stays down");
        assertThrows(IllegalStateException.class, () -> ModifierKeys.during(new RawKeys.Lwjgl2(buffer), codes, new Callable<Void>() {
            @Override
            public Void call() {
                throw new IllegalStateException("a tooltip handler threw");
            }
        }));
        assertEquals(0, buffer.get(Keyboard.KEY_LSHIFT), "put back after a throw");
    }

    @Test
    @DisplayName("every key is put back even when putting one back throws")
    void restoresEveryKey() throws Exception {
        final ByteBuffer buffer = ByteBuffer.allocate(256);
        RawKeys failing = new RawKeys() {
            private final RawKeys inner = new RawKeys.Lwjgl2(buffer);

            @Override
            public String name() {
                return "test";
            }

            @Override
            public boolean isDown(int keyCode) {
                return inner.isDown(keyCode);
            }

            @Override
            public void setDown(int keyCode, boolean down) {
                if (!down && keyCode == Keyboard.KEY_LCONTROL) {
                    throw new IllegalStateException("cannot let go");
                }
                inner.setDown(keyCode, down);
            }
        };
        int[] codes = {Keyboard.KEY_LSHIFT, Keyboard.KEY_LCONTROL, Keyboard.KEY_LMENU};
        ModifierKeys.during(failing, codes, new Callable<Void>() {
            @Override
            public Void call() {
                return null;
            }
        });
        assertEquals(0, buffer.get(Keyboard.KEY_LSHIFT), "put back after another key failed");
        assertEquals(0, buffer.get(Keyboard.KEY_LMENU));
    }
}
