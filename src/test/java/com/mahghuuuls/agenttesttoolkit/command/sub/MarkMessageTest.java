package com.mahghuuuls.agenttesttoolkit.command.sub;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The marker's chat confirmation.
 *
 * <p>The record has always carried the label; the chat line did not. A human acting on a
 * timed instruction encoded in the label could not tell which marker had just fired. These
 * tests pin the label into the confirmation so that omission cannot come back quietly.
 */
class MarkMessageTest {

    @Test
    @DisplayName("the confirmation contains the label verbatim")
    void confirmationCarriesLabel() {
        String text = MarkSubCommand.confirmation("PRESS_H_NOW");
        assertTrue(text.contains("PRESS_H_NOW"), text);
        assertTrue(text.startsWith("[DevToolkit] "), text);
    }

    @Test
    @DisplayName("a multi word label survives whole")
    void multiWordLabelSurvives() {
        assertTrue(MarkSubCommand.confirmation("two words").endsWith("two words"));
    }

    @Test
    @DisplayName("two markers produce two different confirmations")
    void differentLabelsDifferentText() {
        String first = MarkSubCommand.confirmation("READY_A");
        String second = MarkSubCommand.confirmation("READY_B");
        assertNotEquals(first, second);
        assertEquals(first.replace("READY_A", "READY_B"), second);
    }
}
