package com.mahghuuuls.agenttesttoolkit.client;

import net.minecraft.client.gui.GuiScreen;

/**
 * The screen shown while the single-player logout leave proves the game is paused. It pauses
 * single player as the pause menu does, so the integrated server stops running its network step
 * before the quitting packet is sent. It offers no buttons: Escape closes it as any screen, which
 * the leave reads as an interruption and gives up without sending anything.
 */
public final class LeavePauseScreen extends GuiScreen {

    private static final String TEXT = "Leaving...";

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, TEXT, width / 2, height / 2 - 4, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
