package com.mahghuuuls.agenttesttoolkit.client;

import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreenWorking;
import net.minecraft.client.gui.GuiSleepMP;
import net.minecraft.client.gui.GuiWinGame;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.multiplayer.GuiConnecting;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The client's refusals for close and respawn (REQ-205, REQ-206), decided without a game. */
class ClientActionExecutorTest {

    @Test
    @DisplayName("close refuses no world, no screen, death, credits, bed and loading screens")
    void closeRefusals() {
        assertEquals("noWorld", ClientActionExecutor.closeRefusal(false, GuiInventory.class));
        assertEquals("noScreen", ClientActionExecutor.closeRefusal(true, null));
        assertEquals("deathScreenUseRespawn", ClientActionExecutor.closeRefusal(true, GuiGameOver.class));
        assertEquals("credits", ClientActionExecutor.closeRefusal(true, GuiWinGame.class));
        assertEquals("bedScreen", ClientActionExecutor.closeRefusal(true, GuiSleepMP.class));
        assertEquals("loadingScreen", ClientActionExecutor.closeRefusal(true, GuiConnecting.class));
        assertEquals("loadingScreen", ClientActionExecutor.closeRefusal(true, GuiDownloadTerrain.class));
        assertEquals("loadingScreen", ClientActionExecutor.closeRefusal(true, GuiScreenWorking.class));
    }

    @Test
    @DisplayName("close takes the inventory, chat and the pause menu")
    void closeAllowed() {
        assertNull(ClientActionExecutor.closeRefusal(true, GuiInventory.class));
        assertNull(ClientActionExecutor.closeRefusal(true, GuiChat.class));
        assertNull(ClientActionExecutor.closeRefusal(true, GuiIngameMenu.class));
    }

    @Test
    @DisplayName("respawn presses only on the death screen of a dead player, and waits for a death still arriving")
    void respawnDecision() {
        assertNull(ClientActionExecutor.respawnRefusal(true, false, false, false, true));
        assertEquals("noWorld", ClientActionExecutor.respawnRefusal(false, false, false, false, true));
        assertEquals("credits", ClientActionExecutor.respawnRefusal(true, true, false, false, false));
        assertEquals("hardcore", ClientActionExecutor.respawnRefusal(true, false, true, false, true));
        // The review's case: a bundle kills and asks at once; the client still sees the old
        // health, and must wait rather than refuse.
        String early = ClientActionExecutor.respawnRefusal(true, false, false, true, false);
        assertEquals("notDeadYet", early);
        assertTrue(ClientActionExecutor.isWaitable(early));
        assertTrue(ClientActionExecutor.isWaitable(ClientActionExecutor.respawnRefusal(true, false, false, false, false)));
        assertFalse(ClientActionExecutor.isWaitable("hardcore"));
        assertFalse(ClientActionExecutor.isWaitable("credits"));
    }

    @Test
    @DisplayName("a rejoin starts from any menu, a modpack's own title screen too, but not while connecting or loading")
    void menuScreens() {
        assertTrue(ClientActionExecutor.isMenuScreen(net.minecraft.client.gui.GuiMainMenu.class));
        assertTrue(ClientActionExecutor.isMenuScreen(net.minecraft.client.gui.GuiMultiplayer.class));
        assertTrue(ClientActionExecutor.isMenuScreen(net.minecraft.client.gui.GuiScreen.class), "a modded menu");
        assertFalse(ClientActionExecutor.isMenuScreen(net.minecraft.client.multiplayer.GuiConnecting.class));
        assertFalse(ClientActionExecutor.isMenuScreen(net.minecraft.client.gui.GuiScreenWorking.class));
        assertFalse(ClientActionExecutor.isMenuScreen(net.minecraft.client.gui.GuiDownloadTerrain.class));
        assertFalse(ClientActionExecutor.isMenuScreen(net.minecraft.client.gui.GuiDisconnected.class));
    }

    @Test
    @DisplayName("reconnect refuses no world, Realms, a world open to LAN, and a server it cannot name")
    void reconnectRefusals() {
        assertNull(ClientActionExecutor.reconnectRefusal(true, false, true, false, false), "single player");
        assertNull(ClientActionExecutor.reconnectRefusal(true, false, false, false, true), "a remote server");
        assertEquals("noWorld", ClientActionExecutor.reconnectRefusal(false, false, false, false, true));
        assertEquals("realms", ClientActionExecutor.reconnectRefusal(true, true, false, false, true));
        assertEquals("openToLan", ClientActionExecutor.reconnectRefusal(true, false, true, true, false));
        assertEquals("noServerData", ClientActionExecutor.reconnectRefusal(true, false, false, false, false));
    }
}
