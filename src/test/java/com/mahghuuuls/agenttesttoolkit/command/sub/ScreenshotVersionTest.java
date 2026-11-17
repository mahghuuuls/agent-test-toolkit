package com.mahghuuuls.agenttesttoolkit.command.sub;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The screenshot command's version check: the client's hello against what its {@code hide}
 * needs, by the same rule the client steps' {@code screenshotHide} follows.
 */
class ScreenshotVersionTest {

    @Test
    @DisplayName("toasts or a list needs 1.9.0, chat or hud alone 1.8.0, nothing left out any toolkit")
    void versionByHide() {
        Object[] toasts = ScreenshotSubCommand.tooOld("Dev", "1.8.0", "toasts");
        assertEquals("Dev (client toolkit 1.8.0, this screenshot needs 1.9.0)", toasts[0]);
        assertEquals("1.9.0", ScreenshotSubCommand.tooOld("Dev", "1.8.0", "chat,hud")[2],
                "a list a 1.8.0 client cannot read");
        assertNull(ScreenshotSubCommand.tooOld("Dev", "1.8.0", "chat"), "1.8.0 values stay");
        assertNull(ScreenshotSubCommand.tooOld("Dev", "1.9.0", "chat,toasts"));
        assertEquals("1.8.0", ScreenshotSubCommand.tooOld("Dev", "1.7.0", "hud")[2]);
        assertEquals("Dev (client toolkit none, this screenshot needs 1.8.0)",
                ScreenshotSubCommand.tooOld("Dev", null, "chat")[0], "no hello yet");
        assertNull(ScreenshotSubCommand.tooOld("Dev", null, ""), "nothing left out: the 1.7.0 bytes");
    }
}
