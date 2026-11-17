package com.mahghuuuls.agenttesttoolkit.client.menu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The screens a close leaves for a person, the kinds of a world's questions, and what close matches. */
class ConfirmScreensTest {

    private static final String SCREEN = "net.minecraft.client.gui.GuiScreen";
    private static final List<String> BOOK = Arrays.asList("net.minecraft.client.gui.GuiScreenBook", SCREEN);
    private static final List<String> PAUSE = Arrays.asList("net.minecraft.client.gui.GuiIngameMenu", SCREEN);
    private static final List<String> OLD_SAVE = Arrays.asList(ConfirmScreens.OLD_SAVE, ConfirmScreens.YES_NO, SCREEN);
    private static final List<String> LINK = Arrays.asList("net.minecraft.client.gui.GuiConfirmOpenLink",
            ConfirmScreens.YES_NO, SCREEN);
    private static final List<String> CONFIRMATION = Arrays.asList(ConfirmScreens.CONFIRMATION,
            ConfirmScreens.NOTIFICATION, SCREEN);
    private static final List<String> NOTICE = Arrays.asList(ConfirmScreens.NOTIFICATION, SCREEN);
    private static final List<String> BACKUP = Arrays.asList(ConfirmScreens.BACKUP_FAILED, SCREEN);
    private static final List<String> MOD_YES_NO = Arrays.asList("com.example.ModQuestion", ConfirmScreens.YES_NO,
            SCREEN);

    @Test
    @DisplayName("yes-or-no screens, Forge's notices and questions, and the backup failure are confirmations, subclasses included")
    void confirmations() {
        assertTrue(ConfirmScreens.isConfirmation(OLD_SAVE));
        assertTrue(ConfirmScreens.isConfirmation(LINK));
        assertTrue(ConfirmScreens.isConfirmation(CONFIRMATION));
        assertTrue(ConfirmScreens.isConfirmation(NOTICE));
        assertTrue(ConfirmScreens.isConfirmation(BACKUP));
        assertTrue(ConfirmScreens.isConfirmation(MOD_YES_NO), "a mod's own subclass of the yes-or-no screen");
        assertFalse(ConfirmScreens.isConfirmation(BOOK));
        assertFalse(ConfirmScreens.isConfirmation(PAUSE));
    }

    @Test
    @DisplayName("close: notPaused first, then confirmScreen, then the class or a superclass, else screenMismatch")
    void close() {
        String book = "net.minecraft.client.gui.GuiScreenBook";
        assertNull(ConfirmScreens.closeRefusal(true, BOOK, book));
        assertNull(ConfirmScreens.closeRefusal(true, BOOK, SCREEN), "a superclass matches");
        assertEquals(ConfirmScreens.REASON_SCREEN_MISMATCH, ConfirmScreens.closeRefusal(true, PAUSE, book));
        assertEquals(ConfirmScreens.REASON_NOT_PAUSED, ConfirmScreens.closeRefusal(false, BOOK, book));
        assertEquals(ConfirmScreens.REASON_NOT_PAUSED,
                ConfirmScreens.closeRefusal(false, LINK, "net.minecraft.client.gui.GuiConfirmOpenLink"),
                "not paused is said before the screen asks a person");
        assertEquals(ConfirmScreens.REASON_NOT_PAUSED,
                ConfirmScreens.closeRefusal(true, Collections.<String>emptyList(), book), "no screen");
        assertEquals(ConfirmScreens.REASON_CONFIRM_SCREEN,
                ConfirmScreens.closeRefusal(true, LINK, "net.minecraft.client.gui.GuiConfirmOpenLink"),
                "named exactly, still left for a person");
        assertEquals(ConfirmScreens.REASON_CONFIRM_SCREEN, ConfirmScreens.closeRefusal(true, BACKUP, book));
    }

    @Test
    @DisplayName("a world's questions: the old save, Forge's questions by their text, and notices")
    void questionKinds() {
        assertTrue(ConfirmScreens.isQuestion(OLD_SAVE));
        assertTrue(ConfirmScreens.isQuestion(CONFIRMATION));
        assertTrue(ConfirmScreens.isQuestion(NOTICE));
        assertFalse(ConfirmScreens.isQuestion(LINK), "another yes-or-no screen is no world question");
        assertFalse(ConfirmScreens.isQuestion(BOOK));
        assertTrue(ConfirmScreens.isStartupQuestion(CONFIRMATION), "Forge's question holds the world's start");
        assertTrue(ConfirmScreens.isStartupQuestion(NOTICE));
        assertFalse(ConfirmScreens.isStartupQuestion(OLD_SAVE), "the old save's question is an ordinary screen");
        assertTrue(ConfirmScreens.isOldSave(OLD_SAVE));
        assertFalse(ConfirmScreens.isOldSave(CONFIRMATION));
        assertFalse(ConfirmScreens.isOldSave(LINK));

        assertEquals(ConfirmScreens.KIND_OLD_SAVE, ConfirmScreens.questionKind(OLD_SAVE, "anything"));
        assertEquals(ConfirmScreens.KIND_MISSING_REGISTRIES, ConfirmScreens.questionKind(CONFIRMATION,
                "Forge Mod Loader detected missing/unknown registrie(s).\n\nThere are 1 missing registries"));
        assertEquals(ConfirmScreens.KIND_MISSING_ENTRIES, ConfirmScreens.questionKind(CONFIRMATION,
                "Forge Mod Loader detected missing registry entries.\n\nThere are 3 missing entries in this save."));
        assertEquals(ConfirmScreens.KIND_BACKUP_LEVEL_DAT, ConfirmScreens.questionKind(CONFIRMATION,
                "Forge Mod Loader detected that the backup level.dat is being used."));
        assertEquals(ConfirmScreens.KIND_OTHER, ConfirmScreens.questionKind(CONFIRMATION, "Something new"));
        assertEquals(ConfirmScreens.KIND_OTHER, ConfirmScreens.questionKind(CONFIRMATION, null));
        assertEquals(ConfirmScreens.KIND_NOTICE, ConfirmScreens.questionKind(NOTICE,
                "Forge Mod Loader could not load this save."), "a notice, whatever its text");
    }

    @Test
    @DisplayName("a question's text as one record line: no line breaks or control characters, at most 256")
    void oneLine() {
        assertEquals("First line / Second line", ConfirmScreens.oneLine("First line\n\n  Second line\n"));
        assertEquals("a b", ConfirmScreens.oneLine("a\tb"));
        assertEquals("", ConfirmScreens.oneLine(null));
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 400; i++) {
            longText.append('x');
        }
        assertEquals(256, ConfirmScreens.oneLine(longText.toString()).length());
    }
}
