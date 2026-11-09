package com.mahghuuuls.agenttesttoolkit.inbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The words of a player action: the steps, the slot's bounds, and nothing extra. */
class PlayerActionOptionsTest {

    private static PlayerActionOptions parse(String line) {
        return PlayerActionOptions.parse(line.split(" "));
    }

    @Test
    @DisplayName("select takes one slot from 0 to 8")
    void select() {
        for (int slot = 0; slot <= 8; slot++) {
            PlayerActionOptions action = parse("select " + slot);
            assertEquals("select", action.getStep());
            assertEquals(slot, action.getSlot());
        }
        for (String bad : new String[] {"select", "select 9", "select -1", "select 01", "select a", "select 1 2", "select 1.0"}) {
            assertNull(parse(bad), bad);
        }
    }

    @Test
    @DisplayName("use and attack take no argument")
    void useAndAttack() {
        assertEquals(PlayerActionOptions.NO_SLOT, parse("use").getSlot());
        assertEquals("attack", parse("attack").getStep());
        assertNull(parse("use 1"));
        assertNull(parse("attack Developer"));
    }

    @Test
    @DisplayName("only the lower-case steps are actions")
    void steps() {
        assertTrue(PlayerActionOptions.isStep("select"));
        assertTrue(PlayerActionOptions.isStep("use"));
        assertTrue(PlayerActionOptions.isStep("attack"));
        for (String bad : new String[] {"Use", "move", "look", "reconnect", "", null}) {
            assertFalse(PlayerActionOptions.isStep(bad), String.valueOf(bad));
        }
        assertNull(PlayerActionOptions.parse(new String[0]));
        assertNull(PlayerActionOptions.parse(null));
    }

    @Test
    @DisplayName("a decoded action is valid only as the parser would have made it")
    void decodedValidity() {
        assertTrue(PlayerActionOptions.isValid("select", 0));
        assertTrue(PlayerActionOptions.isValid("select", 8));
        assertFalse(PlayerActionOptions.isValid("select", 9));
        assertFalse(PlayerActionOptions.isValid("select", PlayerActionOptions.NO_SLOT));
        assertTrue(PlayerActionOptions.isValid("use", PlayerActionOptions.NO_SLOT));
        assertFalse(PlayerActionOptions.isValid("use", 3));
        assertFalse(PlayerActionOptions.isValid("jump", PlayerActionOptions.NO_SLOT));
    }

    @Test
    @DisplayName("a dead player may still choose a slot, but not click")
    void living() {
        assertFalse(PlayerActionOptions.needsLivingPlayer("select"));
        assertTrue(PlayerActionOptions.needsLivingPlayer("use"));
        assertTrue(PlayerActionOptions.needsLivingPlayer("attack"));
    }
}
