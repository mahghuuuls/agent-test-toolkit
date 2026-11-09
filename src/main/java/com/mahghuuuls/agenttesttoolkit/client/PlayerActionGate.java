package com.mahghuuuls.agenttesttoolkit.client;

/**
 * Whether the client may take a player action now, from plain values, so every refusal is
 * tested without a game. Null means it may.
 */
final class PlayerActionGate {

    /** No world is open, so there is nothing to act on. */
    static final String REASON_NO_WORLD = "noWorld";
    /**
     * A screen is open. A click would go to the screen, not the world, and the game ignores the
     * action keys while one is open, so the action would do nothing.
     */
    static final String REASON_SCREEN_OPEN = "screenOpen";
    /** The key the action presses is unbound in the client's controls. */
    static final String REASON_UNBOUND = "unbound";
    /** The inventory slot whose tooltip was asked for holds nothing. */
    static final String REASON_EMPTY_SLOT = "emptySlot";
    /** Modifier keys were asked for, and this client's keyboard cannot be made to hold them. */
    static final String REASON_MODIFIER_UNAVAILABLE = "modifierUnavailable";

    private PlayerActionGate() {
    }

    /**
     * @param worldOpen  a world and its player exist
     * @param screenOpen a screen is open
     * @param usesKey    the action presses a key binding
     * @param keyCode    that binding's code; 0 is unbound
     * @param emptySlot  a tooltip was asked of a slot that holds nothing
     * @param modifierUnavailable modifier keys were asked and cannot be held on this client
     */
    static String refusal(boolean worldOpen, boolean screenOpen, boolean usesKey, int keyCode, boolean emptySlot,
                          boolean modifierUnavailable) {
        if (!worldOpen) {
            return REASON_NO_WORLD;
        }
        if (screenOpen) {
            return REASON_SCREEN_OPEN;
        }
        if (usesKey && keyCode == 0) {
            return REASON_UNBOUND;
        }
        if (emptySlot) {
            return REASON_EMPTY_SLOT;
        }
        if (modifierUnavailable) {
            return REASON_MODIFIER_UNAVAILABLE;
        }
        return null;
    }
}
