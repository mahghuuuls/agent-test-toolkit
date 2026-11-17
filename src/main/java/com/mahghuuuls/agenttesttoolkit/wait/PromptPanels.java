package com.mahghuuuls.agenttesttoolkit.wait;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The checkpoint prompt panel each player has on screen, as the server knows it, and the one
 * place that takes a panel away.
 *
 * <p>A client shows one panel at a time, so the server keeps one token per player: a newer panel
 * for the same player replaces the older on the client and here. Every way a prompt can end goes
 * through this class (the acknowledgement, an armed checkpoint's wait ending, the refusal of an
 * arm while a bundle already waits, the checkpoint state being reset for a stopped or replaced
 * session or a world change, the player leaving, the server stopping), so no panel is left on a
 * screen with nobody able to remove it.
 *
 * <p>A hide names the token of the panel it is for. A hide that arrives after a newer panel was
 * shown to the same player is ignored here, and the client applies the same check, so a late hide
 * never removes the newer prompt.
 *
 * <p>Free of Minecraft types: sending the hide to a player is handed to a {@link Hider} the mod
 * installs, so the rules are tested without a game. Guarded by the class's lock, because a
 * checkpoint command may arrive through RCON on a thread of its own; the hides themselves are
 * sent after the lock is released.
 */
public final class PromptPanels {

    /** Sends the hide for one panel to its player, when that player is still online. */
    public interface Hider {
        void hide(UUID player, String token);
    }

    private static final Map<UUID, String> SHOWN = new HashMap<UUID, String>();
    private static volatile Hider hider;

    private PromptPanels() {
    }

    /** Installed once at mod initialisation; null sends nothing, as in tests that need no hides. */
    public static void setHider(Hider sender) {
        hider = sender;
    }

    /** Remembers the panel just shown to this player, in place of any panel they had. */
    public static synchronized void shown(UUID player, String token) {
        if (player != null && token != null) {
            SHOWN.put(player, token);
        }
    }

    /** The token of the panel this player has now, or null. */
    public static synchronized String current(UUID player) {
        return player == null ? null : SHOWN.get(player);
    }

    /**
     * Takes away this player's panel when it is still the one with this token.
     *
     * @return true when it was, and the hide was sent; false for a panel already replaced or gone
     */
    public static boolean hide(UUID player, String token) {
        synchronized (PromptPanels.class) {
            if (player == null || token == null || !token.equals(SHOWN.get(player))) {
                return false;
            }
            SHOWN.remove(player);
        }
        send(player, token);
        return true;
    }

    /**
     * Takes away every panel. Called when there is nothing left to answer: at an acknowledgement,
     * which answers the armed checkpoint and the last cue together, and when the checkpoint state
     * is reset.
     *
     * @return how many panels were taken away
     */
    public static int hideAll() {
        Map<UUID, String> taken;
        synchronized (PromptPanels.class) {
            taken = new HashMap<UUID, String>(SHOWN);
            SHOWN.clear();
        }
        for (Map.Entry<UUID, String> panel : taken.entrySet()) {
            send(panel.getKey(), panel.getValue());
        }
        return taken.size();
    }

    /** Forgets a player's panel without a hide: they left, and their client drops it as it disconnects. */
    public static synchronized void forget(UUID player) {
        if (player != null) {
            SHOWN.remove(player);
        }
    }

    /** Forgets every panel without a hide: the server is stopping, and its players are leaving. */
    public static synchronized void forgetAll() {
        SHOWN.clear();
    }

    private static void send(UUID player, String token) {
        Hider current = hider;
        if (current != null) {
            current.hide(player, token);
        }
    }
}
