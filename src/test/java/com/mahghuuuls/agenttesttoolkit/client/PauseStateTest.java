package com.mahghuuuls.agenttesttoolkit.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The pause note changes only when the pause starts, ends, or another screen pauses the game. */
class PauseStateTest {

    private static final String BOOK = "net.minecraft.client.gui.GuiScreenBook";
    private static final String MENU = "net.minecraft.client.gui.GuiIngameMenu";

    @Test
    @DisplayName("a pause writes once, a resume removes once, nothing in between")
    void writeOnce() {
        PauseState state = new PauseState();
        assertEquals(PauseState.Change.NONE, state.update(false, null));
        assertEquals(PauseState.Change.WRITE, state.update(true, BOOK));
        assertEquals(PauseState.Change.NONE, state.update(true, BOOK));
        assertEquals(PauseState.Change.REMOVE, state.update(false, null));
        assertEquals(PauseState.Change.NONE, state.update(false, null));
    }

    @Test
    @DisplayName("another screen pausing the game writes again with its name")
    void anotherScreen() {
        PauseState state = new PauseState();
        state.update(true, BOOK);
        assertEquals(PauseState.Change.WRITE, state.update(true, MENU));
        assertEquals(MENU, state.getScreen());
    }

    @Test
    @DisplayName("an open screen that does not pause, such as the inventory, writes nothing")
    void inventoryDoesNotPause() {
        PauseState state = new PauseState();
        assertEquals(PauseState.Change.NONE, state.update(false, "net.minecraft.client.gui.inventory.GuiInventory"));
    }
}
