package com.mahghuuuls.agenttesttoolkit.net;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A screenshot's hide on the wire: written only when set, so older bytes still read. */
class ScreenshotHideTest {

    private static ScreenshotRequest roundTrip(ScreenshotRequest sent) {
        ByteBuf buf = Unpooled.buffer();
        sent.toBytes(buf);
        ScreenshotRequest read = new ScreenshotRequest();
        read.fromBytes(buf);
        assertEquals(0, buf.readableBytes(), "every byte read");
        return read;
    }

    @Test
    @DisplayName("hide=chat and hide=hud survive the wire; anything else is not acted on")
    void hideRoundTrip() {
        assertEquals("chat", roundTrip(new ScreenshotRequest("a", 0, 1L, "chat")).getHide());
        assertEquals("hud", roundTrip(new ScreenshotRequest("a", 0, 1L, "hud")).getHide());
        assertTrue(new ScreenshotRequest("a", 0, 1L, "hud").isValid());
        assertFalse(new ScreenshotRequest("a", 0, 1L, "all").isValid());
    }

    @Test
    @DisplayName("a request without hide has the 1.7.0 bytes, and 1.7.0 bytes read as no hide")
    void plainBytesUnchanged() {
        ByteBuf plain = Unpooled.buffer();
        new ScreenshotRequest("a", 5, 9L).toBytes(plain);
        ByteBuf old = Unpooled.buffer();
        // What a 1.7.0 server writes: the label, the delay, the tick, nothing after.
        net.minecraftforge.fml.common.network.ByteBufUtils.writeUTF8String(old, "a");
        old.writeInt(5);
        old.writeLong(9L);
        assertEquals(old, plain);
        ScreenshotRequest read = new ScreenshotRequest();
        read.fromBytes(old);
        assertEquals("", read.getHide());
        assertTrue(read.isValid());
    }

    @Test
    @DisplayName("an action's screenshotHide survives the wire, needs a screenshot, and is absent from 1.7.0 bytes")
    void actionHide() {
        ByteBuf buf = Unpooled.buffer();
        new PlayerActionRequest("use", -1, "", 0, "beef", 7L).withOptions(0, "shot", 0).withScreenshotHide("hud").toBytes(buf);
        PlayerActionRequest read = new PlayerActionRequest();
        read.fromBytes(buf);
        assertEquals("hud", read.getScreenshotHide());
        assertTrue(read.isValid());
        assertFalse(new PlayerActionRequest("use", -1, "", 0, "beef", 7L).withScreenshotHide("chat").isValid(),
                "hide without a screenshot");
        assertFalse(new PlayerActionRequest("use", -1, "", 0, "beef", 7L).withOptions(0, "shot", 0)
                .withScreenshotHide("all").isValid());

        ByteBuf plain = Unpooled.buffer();
        new PlayerActionRequest("use", -1, "", 0, "beef", 7L).withOptions(0, "shot", 0).toBytes(plain);
        PlayerActionRequest old = new PlayerActionRequest();
        old.fromBytes(plain);
        assertEquals("", old.getScreenshotHide());
    }
}
