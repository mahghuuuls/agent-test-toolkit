package com.mahghuuuls.agenttesttoolkit.inbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The words of the screen steps: what each takes, its bounds, and what it refuses. */
class ScreenOptionsTest {

    private static ScreenOptions parse(String line) {
        return ScreenOptions.parse(line.split(" "));
    }

    private static void assertRoundTrip(String line) {
        ScreenOptions first = parse(line);
        assertNotNull(first, line);
        ScreenOptions again = ScreenOptions.parse(first.toWords().toArray(new String[0]));
        assertNotNull(again, "read back: " + first.toWords());
        assertEquals(first.toWords(), again.toWords(), line);
    }

    @Test
    @DisplayName("screen takes nothing")
    void screen() {
        assertEquals("screen", parse("screen").getStep());
        assertNull(parse("screen now"));
        assertNull(parse("screen delay=5"));
        assertNull(parse("screen expect=net.minecraft.client.gui.GuiChest"));
    }

    @Test
    @DisplayName("a slot click is left, right, shift or a number key, on slots 0 to 999")
    void slotClick() {
        ScreenOptions left = parse("click slot 0");
        assertEquals("slot", left.getTarget());
        assertEquals(0, left.getSlot());
        assertEquals("left", left.getMouse());
        assertEquals("right", parse("click slot 5 right").getMouse());
        assertEquals("shift", parse("click slot 5 shift").getMouse());
        ScreenOptions key = parse("click slot 999 key=9");
        assertEquals("key", key.getMouse());
        assertEquals(9, key.getHotbarKey());
        assertEquals("net.minecraft.client.gui.inventory.GuiChest",
                parse("click slot 0 shift expect=net.minecraft.client.gui.inventory.GuiChest").getExpect());
        for (String bad : new String[] {"click", "click slot", "click slot 1000", "click slot -1", "click slot 01",
                "click slot 5 middle", "click slot 5 key=0", "click slot 5 key=10", "click slot 5 left right",
                "click slot 5 expect=a/b", "click slot 5 expect=1abc", "click slot 5 drag", "click slot 5 showTicks=4"}) {
            assertNull(parse(bad), bad);
        }
    }

    @Test
    @DisplayName("a button is named by its id or by its exact label, which may run over several words")
    void buttonClick() {
        assertEquals(4, parse("click button 4").getButtonId());
        assertEquals(-3, parse("click button -3").getButtonId());
        assertEquals("", parse("click button 4").getLabel());
        assertEquals("Done", parse("click button label=Done").getLabel());
        ScreenOptions spaced = parse("click button label=Save and Quit to Title expect=a.B delay=2");
        assertEquals("Save and Quit to Title", spaced.getLabel());
        assertEquals("a.B", spaced.getExpect());
        assertEquals(2, spaced.getDelay());
        for (String bad : new String[] {"click button", "click button label=", "click button 4 5", "click button 2147483648",
                "click button Done", "click button label=x expect=a b"}) {
            assertNull(parse(bad), bad);
        }
        StringBuilder longLabel = new StringBuilder("click button label=");
        for (int i = 0; i < 129; i++) {
            longLabel.append('x');
        }
        assertNull(parse(longLabel.toString()));
        assertFalse(ScreenOptions.isLabel("a\tb"), "no control character in a label");
    }

    @Test
    @DisplayName("a click at a position is in GUI units, left or right")
    void clickAt() {
        ScreenOptions at = parse("click at 100 40 right");
        assertEquals(100, at.getX());
        assertEquals(40, at.getY());
        assertEquals("right", at.getMouse());
        assertEquals("left", parse("click at 0 0").getMouse());
        assertNotNull(parse("click at 8192 8192"));
        for (String bad : new String[] {"click at", "click at 1", "click at -1 2", "click at 8193 1", "click at 1 2 shift",
                "click at 1.5 2", "click here 1 2"}) {
            assertNull(parse(bad), bad);
        }
    }

    @Test
    @DisplayName("a scroll is a non-zero number of notches, at most 50 either way")
    void scroll() {
        assertEquals(3, parse("scroll 3").getAmount());
        assertEquals(-50, parse("scroll -50 expect=x.Y").getAmount());
        for (String bad : new String[] {"scroll", "scroll 0", "scroll 51", "scroll -51", "scroll up", "scroll 1 2"}) {
            assertNull(parse(bad), bad);
        }
    }

    @Test
    @DisplayName("a container tooltip names a slot, with the inventory tooltip's words, and is not the inventory view")
    void tooltipContainer() {
        ScreenOptions tip = parse("tooltip container 12 alt advanced screenshot=tip");
        assertEquals("tooltip", tip.getStep());
        assertEquals(12, tip.getSlot());
        assertTrue(tip.isAdvanced());
        assertEquals("alt", tip.modifierKeys());
        assertEquals("tip", tip.getScreenshot());
        assertNull(parse("tooltip 12"), "the inventory view is a player action");
        assertNull(parse("tooltip container"));
        assertNull(parse("tooltip container 12 advanced advanced"));
        assertNull(parse("tooltip container 12 expect=a.B"));
        assertNull(parse("tooltip container 12 bold"));
    }

    @Test
    @DisplayName("each step reads back from its own words unchanged; describing the screen is allowed when dead")
    void roundTripsAndLiving() {
        for (String line : Arrays.asList("screen", "click slot 3", "click slot 3 key=2 delay=4", "click slot 3 shift",
                "click button 7", "click button label=Save and Quit expect=a.B",
                "click at 10 20 right screenshot=a screenshotHide=chat", "scroll -2", "tooltip container 0 shift ctrl")) {
            assertRoundTrip(line);
        }
        assertEquals(Arrays.asList("tooltip", "container", "0", "advanced", "ctrl"),
                parse("tooltip container 0 ctrl advanced").toWords());
        assertFalse(ScreenOptions.needsLivingPlayer("screen"));
        assertTrue(ScreenOptions.needsLivingPlayer("click"));
        assertNull(ScreenOptions.parse(null));
        assertNull(parse("Click slot 1"));
    }
}
