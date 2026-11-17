package com.mahghuuuls.agenttesttoolkit.client.menu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What an {@code open} step does with each question its world asks, in the order an opening asks
 * them: the decisions the question watch carries out on the real screens.
 */
class QuestionAnswersTest {

    private static final String SCREEN = "net.minecraft.client.gui.GuiScreen";
    private static final List<String> OLD_SAVE = Arrays.asList(ConfirmScreens.OLD_SAVE, ConfirmScreens.YES_NO, SCREEN);
    private static final List<String> CONFIRMATION = Arrays.asList(ConfirmScreens.CONFIRMATION,
            ConfirmScreens.NOTIFICATION, SCREEN);
    private static final List<String> NOTICE = Arrays.asList(ConfirmScreens.NOTIFICATION, SCREEN);
    private static final List<String> LINK = Arrays.asList("net.minecraft.client.gui.GuiConfirmOpenLink",
            ConfirmScreens.YES_NO, SCREEN);
    private static final String MISSING = "Forge Mod Loader detected missing registry entries.";
    private static final String BACKUP = "Forge Mod Loader detected that the backup level.dat is being used.";

    private static QuestionAnswers armed(String onQuestion) {
        QuestionAnswers answers = new QuestionAnswers();
        answers.arm(onQuestion);
        return answers;
    }

    @Test
    @DisplayName("report: the first question ends the step and the watch; a later one is not this step's")
    void report() {
        QuestionAnswers answers = armed(MenuRequest.REPORT);
        QuestionAnswers.Decision first = answers.opened(CONFIRMATION, MISSING, true);
        assertEquals(QuestionAnswers.Act.REPORT, first.act);
        assertEquals(ConfirmScreens.KIND_MISSING_ENTRIES, first.kind, "the kind from the question's text");
        assertNull(first.answer);
        assertFalse(answers.isArmed());
        assertNull(answers.opened(CONFIRMATION, BACKUP, true), "a person's question after it");
    }

    @Test
    @DisplayName("confirm: two questions in a row are both answered yes, and the watch stays for the next")
    void twoInARow() {
        QuestionAnswers answers = armed(MenuRequest.CONFIRM);
        QuestionAnswers.Decision old = answers.opened(OLD_SAVE, "The world contains pre-update modding data", true);
        assertEquals(QuestionAnswers.Act.ANSWER_NEXT_TICK, old.act, "an ordinary screen, answered at the next tick");
        assertEquals(ConfirmScreens.KIND_OLD_SAVE, old.kind);
        assertTrue(old.yes);
        assertEquals(MenuRequest.CONFIRM, old.answer);
        assertFalse(answers.answering(old), "a yes does not end the step");
        assertTrue(answers.isArmed());

        QuestionAnswers.Decision forge = answers.opened(CONFIRMATION, MISSING, true);
        assertEquals(QuestionAnswers.Act.STAND_IN, forge.act, "a startup question, answered by a stand-in");
        assertEquals(ConfirmScreens.KIND_MISSING_ENTRIES, forge.kind);
        assertTrue(forge.yes);
        assertTrue(forge.twoButtons);
        assertEquals(MenuRequest.CONFIRM, forge.answer);
        assertFalse(answers.answering(forge));
        assertTrue(answers.isArmed(), "still watching until the step ends");
    }

    @Test
    @DisplayName("cancel: a notice is acknowledged and the watch stays; the question after it is answered no and ends the step")
    void cancel() {
        QuestionAnswers answers = armed(MenuRequest.CANCEL);
        QuestionAnswers.Decision notice = answers.opened(NOTICE, "Forge Mod Loader could not load this save.", true);
        assertEquals(QuestionAnswers.Act.STAND_IN, notice.act);
        assertEquals(ConfirmScreens.KIND_NOTICE, notice.kind);
        assertFalse(notice.twoButtons, "one button: no answer to set");
        assertEquals(QuestionAnswers.ACKNOWLEDGED, notice.answer);
        assertFalse(answers.answering(notice), "a notice's one button does not end the step");
        assertTrue(answers.isArmed());

        QuestionAnswers.Decision question = answers.opened(CONFIRMATION, BACKUP, true);
        assertEquals(ConfirmScreens.KIND_BACKUP_LEVEL_DAT, question.kind);
        assertFalse(question.yes);
        assertEquals(MenuRequest.CANCEL, question.answer);
        assertTrue(answers.answering(question), "a no ends the step");
        assertFalse(answers.isArmed(), "and the watch, before the world's start goes on");
        assertNull(answers.opened(CONFIRMATION, MISSING, true));
    }

    @Test
    @DisplayName("cancel on the old save's question: answered no at the next tick, which ends the step")
    void cancelOldSave() {
        QuestionAnswers answers = armed(MenuRequest.CANCEL);
        QuestionAnswers.Decision old = answers.opened(OLD_SAVE, "text", true);
        assertEquals(QuestionAnswers.Act.ANSWER_NEXT_TICK, old.act);
        assertEquals(MenuRequest.CANCEL, old.answer);
        assertTrue(answers.answering(old));
        assertFalse(answers.isArmed());
    }

    @Test
    @DisplayName("a startup question whose query cannot be read is reported, whatever the step asked")
    void unreadable() {
        QuestionAnswers answers = armed(MenuRequest.CONFIRM);
        QuestionAnswers.Decision decision = answers.opened(CONFIRMATION, null, false);
        assertEquals(QuestionAnswers.Act.REPORT, decision.act);
        assertEquals(ConfirmScreens.KIND_OTHER, decision.kind);
        assertFalse(answers.isArmed());

        QuestionAnswers old = armed(MenuRequest.CONFIRM);
        assertEquals(QuestionAnswers.Act.ANSWER_NEXT_TICK, old.opened(OLD_SAVE, "text", false).act,
                "the old save's question has no query to read");
    }

    @Test
    @DisplayName("nothing for a screen that is no world question, or while not armed")
    void ignored() {
        QuestionAnswers answers = armed(MenuRequest.CONFIRM);
        assertNull(answers.opened(LINK, "Open this link?", true));
        assertTrue(answers.isArmed(), "another screen leaves the watch as it was");
        assertNull(new QuestionAnswers().opened(CONFIRMATION, MISSING, true));
        answers.disarm();
        assertNull(answers.opened(CONFIRMATION, MISSING, true));
    }
}
