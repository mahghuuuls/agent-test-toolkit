package com.mahghuuuls.agenttesttoolkit.client.screen;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which slot or button a screen step names, and the refusals when it names none or the wrong screen. */
class ScreenTargetsTest {

    private static final List<String> CHEST = Arrays.asList("net.minecraft.client.gui.inventory.GuiChest",
            "net.minecraft.client.gui.inventory.GuiContainer", "net.minecraft.client.gui.GuiScreen",
            "net.minecraft.client.gui.Gui");

    private static ScreenTargets.Button button(int id, String label, boolean enabled, boolean visible) {
        return new ScreenTargets.Button(id, label, 10, 20, 200, 20, enabled, visible);
    }

    @Test
    @DisplayName("no screen is noScreen; expect matches the class or a superclass, else screenChanged")
    void screen() {
        assertEquals(ScreenTargets.REASON_NO_SCREEN, ScreenTargets.screenRefusal(Collections.<String>emptyList(), ""));
        assertEquals(ScreenTargets.REASON_NO_SCREEN, ScreenTargets.screenRefusal(null, "x.Y"));
        assertNull(ScreenTargets.screenRefusal(CHEST, ""));
        assertNull(ScreenTargets.screenRefusal(CHEST, "net.minecraft.client.gui.inventory.GuiChest"));
        assertNull(ScreenTargets.screenRefusal(CHEST, "net.minecraft.client.gui.inventory.GuiContainer"), "a superclass");
        assertEquals(ScreenTargets.REASON_SCREEN_CHANGED,
                ScreenTargets.screenRefusal(CHEST, "net.minecraft.client.gui.GuiScreenBook"));
        assertEquals(ScreenTargets.REASON_SCREEN_CHANGED, ScreenTargets.screenRefusal(CHEST, "GuiChest"),
                "the full name, not the simple one");
    }

    @Test
    @DisplayName("a button by id or exact label: only shown buttons count; two are ambiguous; a disabled one is refused")
    void buttons() {
        ScreenTargets.Button done = button(0, "Done", true, true);
        ScreenTargets.Button options = button(1, "Options...", true, true);
        ScreenTargets.Button hidden = button(2, "Hidden", true, false);
        ScreenTargets.Button off = button(3, "Off", false, true);
        List<ScreenTargets.Button> all = Arrays.asList(done, options, hidden, off);
        assertSame(done, ScreenTargets.button(all, 0, "").found);
        assertSame(options, ScreenTargets.button(all, 99, "Options...").found, "the label wins over the id");
        assertEquals(ScreenTargets.REASON_NO_BUTTON, ScreenTargets.button(all, 7, "").refusal);
        assertEquals(ScreenTargets.REASON_NO_BUTTON, ScreenTargets.button(all, 0, "done").refusal, "exact, case included");
        assertEquals(ScreenTargets.REASON_NO_BUTTON, ScreenTargets.button(all, 0, "Options").refusal, "not a prefix");
        assertEquals(ScreenTargets.REASON_NO_BUTTON, ScreenTargets.button(all, 2, "").refusal, "a hidden button");
        assertEquals(ScreenTargets.REASON_BUTTON_DISABLED, ScreenTargets.button(all, 3, "").refusal);
        assertEquals(ScreenTargets.REASON_BUTTON_DISABLED, ScreenTargets.button(all, 0, "Off").refusal);

        List<ScreenTargets.Button> twice = Arrays.asList(done, button(0, "Done", true, true));
        assertEquals(ScreenTargets.REASON_AMBIGUOUS_BUTTON, ScreenTargets.button(twice, 0, "").refusal);
        assertEquals(ScreenTargets.REASON_AMBIGUOUS_BUTTON, ScreenTargets.button(twice, 5, "Done").refusal);
        List<ScreenTargets.Button> oneShown = Arrays.asList(done, button(0, "Done", true, false));
        assertSame(done, ScreenTargets.button(oneShown, 0, "").found, "a hidden twin does not make it ambiguous");
        assertEquals(ScreenTargets.REASON_NO_BUTTON,
                ScreenTargets.button(Collections.<ScreenTargets.Button>emptyList(), 0, "").refusal);
    }

    @Test
    @DisplayName("a button is clicked at its centre")
    void buttonCentre() {
        ScreenTargets.Button b = new ScreenTargets.Button(4, "Mods", 100, 48, 98, 20, true, true);
        assertEquals(149, b.centreX());
        assertEquals(58, b.centreY());
    }

    @Test
    @DisplayName("a slot by its number; a missing or switched-off slot is noSlot; its centre is 8 in")
    void slots() {
        ScreenTargets.SlotView stone = new ScreenTargets.SlotView(0, 0, 100, 40, "minecraft:stone", 64, true);
        ScreenTargets.SlotView empty = new ScreenTargets.SlotView(27, 9, 100, 100, "empty", 0, true);
        ScreenTargets.SlotView off = new ScreenTargets.SlotView(5, 5, 0, 0, "minecraft:dirt", 1, false);
        List<ScreenTargets.SlotView> all = Arrays.asList(stone, empty, off);
        assertSame(stone, ScreenTargets.slot(all, 0).found);
        assertSame(empty, ScreenTargets.slot(all, 27).found);
        assertTrue(ScreenTargets.slot(all, 27).found.isEmpty());
        assertEquals(ScreenTargets.REASON_NO_SLOT, ScreenTargets.slot(all, 63).refusal);
        assertEquals(ScreenTargets.REASON_NO_SLOT, ScreenTargets.slot(all, 5).refusal, "switched off");
        assertEquals(ScreenTargets.REASON_NO_SLOT,
                ScreenTargets.slot(Collections.<ScreenTargets.SlotView>emptyList(), 0).refusal, "not a container");
        assertEquals(108, stone.centreX());
        assertEquals(48, stone.centreY());
    }
}
