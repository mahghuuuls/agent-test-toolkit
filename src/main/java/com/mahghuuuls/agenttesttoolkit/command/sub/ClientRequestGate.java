package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.inbox.PlayerActionOptions;
import com.mahghuuuls.agenttesttoolkit.net.ClientActionRequest;
import com.mahghuuuls.agenttesttoolkit.net.ToolkitChannel;

/**
 * Whether the server refuses a client step at once, and with which key, from what it knows
 * about the sender: one place for every step, so the lifecycle steps and the player actions
 * cannot drift apart, and every refusal is tested without a game.
 */
final class ClientRequestGate {

    /** A refusal: the failure key a bundle can tolerate, and the log line's text. */
    enum Refusal {
        NOT_DEAD(ClientSubCommand.KEY_NOT_DEAD, "Client respawn refused: not dead"),
        DEAD(ClientSubCommand.KEY_DEAD, "Client action refused: dead"),
        BUSY(ClientSubCommand.KEY_BUSY, "Client action refused: a reconnect is under way"),
        NO_CLIENT(ClientSubCommand.KEY_NO_CLIENT, "Client action target has no toolkit 1.4.0 or later"),
        CLIENT_TOO_OLD(ClientSubCommand.KEY_NO_CLIENT, "Client step needs a newer toolkit on the client"),
        NOT_ALLOWED(ClientSubCommand.KEY_NOT_ALLOWED, "Client has not allowed actions");

        final String key;
        final String message;

        Refusal(String key, String message) {
            this.key = key;
            this.message = message;
        }
    }

    /** What the server knows about the sender and its client when the step is asked. */
    static final class Facts {
        final boolean dead;
        final boolean busy;
        /** The client has the toolkit at 1.4.0 or later, so it says hello and reads a step. */
        final boolean speaksActions;
        /** What the client said in its hello; null before any hello. */
        final Boolean allowed;
        /** The version the client said hello with; null before any hello. */
        final String helloVersion;

        Facts(boolean dead, boolean busy, boolean speaksActions, Boolean allowed, String helloVersion) {
            this.dead = dead;
            this.busy = busy;
            this.speaksActions = speaksActions;
            this.allowed = allowed;
            this.helloVersion = helloVersion;
        }
    }

    private ClientRequestGate() {
    }

    /** The refusal of this step, or null when it may be sent. */
    static Refusal refusal(String step, boolean logout, Facts facts) {
        return refusal(step, logout, false, facts);
    }

    /**
     * The refusal of this step, or null when it may be sent; {@code hideScreenshot} when a
     * player action's screenshot is asked to leave chat or the HUD out.
     */
    static Refusal refusal(String step, boolean logout, boolean hideScreenshot, Facts facts) {
        if (PlayerActionOptions.isStep(step)) {
            return actionRefusal(step, hideScreenshot, facts);
        }
        if (ClientActionRequest.RESPAWN.equals(step) && !facts.dead) {
            // Known here at once; the client hears of the death a tick later.
            return Refusal.NOT_DEAD;
        }
        if (facts.busy) {
            // A second step would be lost in the leave, or answered in place of the reconnect's report.
            return Refusal.BUSY;
        }
        if (!facts.speaksActions) {
            return Refusal.NO_CLIENT;
        }
        if (ClientActionRequest.QUIT.equals(step) && Boolean.TRUE.equals(facts.allowed)
                && !atLeast(facts.helloVersion, ToolkitChannel.VERSION_1_5_0)) {
            // A 1.4.0 client would drop a step it does not know, and never answer.
            return Refusal.CLIENT_TOO_OLD;
        }
        if (logout && facts.allowed != null && !atLeast(facts.helloVersion, ToolkitChannel.VERSION_1_7_0)) {
            // An older client reads the reconnect without the flag and leaves without logging out.
            return Refusal.CLIENT_TOO_OLD;
        }
        if (!Boolean.TRUE.equals(facts.allowed)) {
            return Refusal.NOT_ALLOWED;
        }
        return null;
    }

    /**
     * A player action: the version first, since an older client has no handler for the action's
     * message and would drop the connection on it, whatever it allows.
     */
    private static Refusal actionRefusal(String step, boolean hideScreenshot, Facts facts) {
        if (!facts.speaksActions) {
            return Refusal.NO_CLIENT;
        }
        if (facts.allowed != null && !atLeast(facts.helloVersion, ToolkitChannel.VERSION_1_7_0)) {
            return Refusal.CLIENT_TOO_OLD;
        }
        if (hideScreenshot && facts.allowed != null && !atLeast(facts.helloVersion, ToolkitChannel.VERSION_1_8_0)) {
            // A 1.7.0 client would take the picture with the chat or HUD it was asked to leave out.
            return Refusal.CLIENT_TOO_OLD;
        }
        if (!Boolean.TRUE.equals(facts.allowed)) {
            return Refusal.NOT_ALLOWED;
        }
        if (facts.busy) {
            return Refusal.BUSY;
        }
        if (PlayerActionOptions.needsLivingPlayer(step) && facts.dead) {
            // A dead player's click would act on nothing.
            return Refusal.DEAD;
        }
        return null;
    }

    private static boolean atLeast(String version, int[] minimum) {
        return version != null && ToolkitChannel.atLeast(version, minimum);
    }
}
