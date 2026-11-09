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
        assertTrue(PlayerActionOptions.isValid("select", 0, "", 0));
        assertTrue(PlayerActionOptions.isValid("select", 8, "", 0));
        assertFalse(PlayerActionOptions.isValid("select", 9, "", 0));
        assertFalse(PlayerActionOptions.isValid("select", PlayerActionOptions.NO_SLOT, "", 0));
        assertTrue(PlayerActionOptions.isValid("use", PlayerActionOptions.NO_SLOT, "", 0));
        assertFalse(PlayerActionOptions.isValid("use", 3, "", 0));
        assertFalse(PlayerActionOptions.isValid("jump", PlayerActionOptions.NO_SLOT, "", 0));
    }

    @Test
    @DisplayName("a dead player may still choose a slot, but not click")
    void living() {
        assertFalse(PlayerActionOptions.needsLivingPlayer("select"));
        assertTrue(PlayerActionOptions.needsLivingPlayer("use"));
        assertTrue(PlayerActionOptions.needsLivingPlayer("attack"));
        assertTrue(PlayerActionOptions.needsLivingPlayer("hold"));
        assertTrue(PlayerActionOptions.needsLivingPlayer("drop"));
        assertFalse(PlayerActionOptions.needsLivingPlayer("inventory"), "the death screen refuses it on the client");
    }

    @Test
    @DisplayName("hold takes use or attack and 1 to 1200 ticks")
    void hold() {
        PlayerActionOptions bow = parse("hold use 20");
        assertEquals("use", bow.getArgument());
        assertEquals(20, bow.getTicks());
        assertEquals(1200, parse("hold attack 1200").getTicks());
        assertEquals(1, parse("hold attack 1").getTicks());
        assertEquals(1199, parse("hold use 1199").getTicks());
        for (String bad : new String[] {"hold", "hold use", "hold use 0", "hold use 1201", "hold use 020", "hold jump 5",
                "hold Use 5", "hold use 5 6", "hold 5 use", "hold use -1"}) {
            assertNull(parse(bad), bad);
        }
        assertTrue(PlayerActionOptions.isValid("hold", PlayerActionOptions.NO_SLOT, "attack", 100));
        assertFalse(PlayerActionOptions.isValid("hold", PlayerActionOptions.NO_SLOT, "attack", 0));
        assertFalse(PlayerActionOptions.isValid("hold", PlayerActionOptions.NO_SLOT, "attack", 1201));
        assertFalse(PlayerActionOptions.isValid("hold", PlayerActionOptions.NO_SLOT, "all", 5));
        assertFalse(PlayerActionOptions.isValid("hold", 2, "use", 5));
    }

    @Test
    @DisplayName("every action takes delay, screenshot and screenshotDelay after its own words, each once and in bounds")
    void options() {
        PlayerActionOptions use = parse("use screenshot=after_use");
        assertEquals("after_use", use.getScreenshot());
        assertEquals(0, use.getScreenshotDelay());
        assertEquals(0, use.getDelay());
        PlayerActionOptions attack = parse("attack delay=40");
        assertEquals(40, attack.getDelay());
        assertEquals("", attack.getScreenshot());
        PlayerActionOptions hold = parse("hold use 20 delay=5 screenshot=bow-1 screenshotDelay=10");
        assertEquals(20, hold.getTicks());
        assertEquals(5, hold.getDelay());
        assertEquals(10, hold.getScreenshotDelay());
        assertEquals(3, parse("select 3 screenshotDelay=200 screenshot=s").getSlot());
        assertEquals(1200, parse("drop all delay=1200").getDelay());
        for (String bad : new String[] {"use delay=1201", "use delay=-1", "use delay=05", "use delay=",
                "use screenshot=a/b", "use screenshot=", "use screenshotDelay=5", "use screenshot=a screenshotDelay=201",
                "use delay=1 delay=2", "use colour=red", "use delay=5 extra", "delay=5 use", "select delay=5 3",
                "use screenshot=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"}) {
            assertNull(parse(bad), bad);
        }
        assertTrue(PlayerActionOptions.isValidOptions(0, "", 0));
        assertFalse(PlayerActionOptions.isValidOptions(0, "", 3), "a screenshot delay needs a screenshot");
        assertFalse(PlayerActionOptions.isValidOptions(0, null, 0));
    }

    @Test
    @DisplayName("tooltip takes an inventory slot 0 to 40, advanced, and showTicks 1 to 1200, default 20")
    void tooltip() {
        PlayerActionOptions plain = parse("tooltip 0");
        assertEquals(0, plain.getSlot());
        assertEquals("", plain.getArgument());
        assertEquals(20, plain.getTicks());
        PlayerActionOptions advanced = parse("tooltip 40 advanced showTicks=60 screenshot=tip");
        assertEquals(40, advanced.getSlot());
        assertEquals("advanced", advanced.getArgument());
        assertEquals(60, advanced.getTicks());
        assertEquals("tip", advanced.getScreenshot());
        assertEquals(36, parse("tooltip 36").getSlot());
        assertEquals("advanced,shift,alt", parse("tooltip 3 alt advanced shift").getArgument());
        assertEquals("ctrl", parse("tooltip 3 ctrl").getArgument());
        assertTrue(PlayerActionOptions.advancedOf("advanced,shift"));
        assertFalse(PlayerActionOptions.advancedOf("shift"));
        assertEquals("shift,alt", PlayerActionOptions.modifierKeysOf("advanced,shift,alt"));
        assertEquals("", PlayerActionOptions.modifierKeysOf("advanced"));
        assertEquals(18, parse("tooltip 0 screenshot=a screenshotDelay=18").getScreenshotDelay(), "two ticks before the close");
        for (String bad : new String[] {"tooltip", "tooltip 41", "tooltip -1", "tooltip 05", "tooltip 3 Advanced",
                "tooltip 3 advanced extra", "tooltip 3 showTicks=0", "tooltip 3 showTicks=1201", "use showTicks=5",
                "hold use 20 showTicks=5", "tooltip 3 shift shift", "tooltip 3 Shift",
                "tooltip 0 screenshot=a screenshotDelay=19", "tooltip 0 showTicks=1 screenshot=a",
                "tooltip 0 showTicks=10 screenshot=a screenshotDelay=9"}) {
            assertNull(parse(bad), bad);
        }
        assertTrue(PlayerActionOptions.needsLivingPlayer("tooltip"));
        assertTrue(PlayerActionOptions.isValid("tooltip", 40, "advanced", 20));
        assertFalse(PlayerActionOptions.isValid("tooltip", 41, "", 20));
        assertFalse(PlayerActionOptions.isValid("tooltip", 3, "", 0));
        assertFalse(PlayerActionOptions.isValid("tooltip", 3, "all", 20));
        assertTrue(PlayerActionOptions.isValid("tooltip", 3, "advanced,shift,ctrl,alt", 20));
        assertFalse(PlayerActionOptions.isValid("tooltip", 3, "shift,advanced", 20), "not the order the parser writes");
        assertFalse(PlayerActionOptions.isValid("tooltip", 3, "shift,shift", 20));
        assertFalse(PlayerActionOptions.isValid("tooltip", 3, "shift,", 20));
    }

    @Test
    @DisplayName("drop takes nothing or all; inventory takes nothing")
    void dropAndInventory() {
        assertEquals("", parse("drop").getArgument());
        assertEquals("all", parse("drop all").getArgument());
        assertNull(parse("drop 2"));
        assertNull(parse("drop all now"));
        assertNull(parse("drop All"));
        assertEquals("inventory", parse("inventory").getStep());
        assertNull(parse("inventory open"));
        assertTrue(PlayerActionOptions.isValid("drop", PlayerActionOptions.NO_SLOT, "all", 0));
        assertFalse(PlayerActionOptions.isValid("drop", PlayerActionOptions.NO_SLOT, "use", 0));
        assertFalse(PlayerActionOptions.isValid("drop", PlayerActionOptions.NO_SLOT, "", 5));
        assertTrue(PlayerActionOptions.isValid("inventory", PlayerActionOptions.NO_SLOT, "", 0));
        assertFalse(PlayerActionOptions.isValid("inventory", PlayerActionOptions.NO_SLOT, "all", 0));
        assertFalse(PlayerActionOptions.isValid("use", PlayerActionOptions.NO_SLOT, null, 0));
    }
}
