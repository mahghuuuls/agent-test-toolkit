package com.mahghuuuls.agenttesttoolkit.command.sub;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Waiting on a picture needs a client that reports it: one that said hello with 1.10.0 or later.
 * Any other is refused at once with {@code devtool.screenshot.noReport}, so a bundle never sits out
 * a timeout for a report that cannot come.
 */
class ScreenshotAwaitTest {

    @Test
    @DisplayName("no hello, or a hello below 1.10.0, is refused noReport; 1.10.0 and later may be awaited")
    void reportRefusal() {
        assertEquals("devtool.screenshot.noReport", ScreenshotSubCommand.KEY_NO_REPORT);
        assertEquals(ScreenshotSubCommand.KEY_NO_REPORT, ScreenshotSubCommand.reportRefusal(null));
        assertEquals(ScreenshotSubCommand.KEY_NO_REPORT, ScreenshotSubCommand.reportRefusal("1.9.0"));
        assertEquals(ScreenshotSubCommand.KEY_NO_REPORT, ScreenshotSubCommand.reportRefusal("1.9.9"));
        assertNull(ScreenshotSubCommand.reportRefusal("1.10.0"));
        assertNull(ScreenshotSubCommand.reportRefusal("1.10.1"));
        assertNull(ScreenshotSubCommand.reportRefusal("2.0.0"));
    }
}
