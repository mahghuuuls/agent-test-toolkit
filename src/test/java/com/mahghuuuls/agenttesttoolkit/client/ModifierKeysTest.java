package com.mahghuuuls.agenttesttoolkit.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lwjgl.input.Keyboard;

import java.nio.ByteBuffer;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The modifier keys: which codes each word holds, and that the buffer always gets its bytes back. */
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
        Byte during = ModifierKeys.during(buffer, codes, new Callable<Byte>() {
            @Override
            public Byte call() {
                return buffer.get(Keyboard.KEY_LSHIFT);
            }
        });
        assertEquals(1, during.byteValue());
        assertEquals(0, buffer.get(Keyboard.KEY_LSHIFT), "put back");
        assertEquals(1, buffer.get(Keyboard.KEY_LMENU), "a key already down stays down");
        assertThrows(IllegalStateException.class, () -> ModifierKeys.during(buffer, codes, new Callable<Void>() {
            @Override
            public Void call() {
                throw new IllegalStateException("a tooltip handler threw");
            }
        }));
        assertEquals(0, buffer.get(Keyboard.KEY_LSHIFT), "put back after a throw");
    }
}
