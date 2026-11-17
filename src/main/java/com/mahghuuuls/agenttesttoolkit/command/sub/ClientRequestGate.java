package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.inbox.InputOptions;
import com.mahghuuuls.agenttesttoolkit.inbox.PlayerActionOptions;
import com.mahghuuuls.agenttesttoolkit.inbox.ScreenOptions;
import com.mahghuuuls.agenttesttoolkit.net.ClientActionRequest;
import com.mahghuuuls.agenttesttoolkit.net.ToolkitChannel;

/**
 * Whether the server refuses a client step at once, and with which key, from what it knows
 * about the sender: one place for every step, so the lifecycle steps, the player actions and the
 * newer input and screen steps cannot drift apart, and every refusal is tested without a game.
 *
 * <p>{@link #neededVersion} is the one owner of the toolkit version each step needs: the version
 * check of {@link #refusal} and the versions a refusal's message names both read it.
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

    /** Which message a step travels in, which decides what the client must be able to read. */
    enum Kind {
        /** Reconnect, respawn, close, quit. */
        LIFECYCLE,
        /** The player actions' own message. */
        PLAYER_ACTION,
        /** The input and screen messages. */
        NEW_STEP
    }

    /** One step as the gate sees it: its name, its message, and the words that raise its version. */
    static final class Step {
        final String name;
        final Kind kind;
        /** A reconnect's {@code logout}. */
        final boolean logout;
        /** A player action's screenshot that leaves the chat or the HUD out. */
        final boolean hideScreenshot;

        private Step(String name, Kind kind, boolean logout, boolean hideScreenshot) {
            this.name = name;
            this.kind = kind;
            this.logout = logout;
            this.hideScreenshot = hideScreenshot;
        }

        static Step lifecycle(String name, boolean logout) {
            return new Step(name, Kind.LIFECYCLE, logout, false);
        }

        static Step playerAction(String name, boolean hideScreenshot) {
            return new Step(name, Kind.PLAYER_ACTION, false, hideScreenshot);
        }

        static Step newStep(String name) {
            return new Step(name, Kind.NEW_STEP, false, false);
        }

        /** A lifecycle step or a player action, told apart by its name, as the command words are. */
        static Step of(String name, boolean logout, boolean hideScreenshot) {
            return PlayerActionOptions.isStep(name) ? playerAction(name, hideScreenshot) : lifecycle(name, logout);
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

    /** The first toolkit that says hello and takes a client step; the mod list already proves it. */
    static final int[] FIRST_VERSION = {1, 4, 0};

    private ClientRequestGate() {
    }

    /**
     * The toolkit version a client needs for this step: the input and screen steps need the
     * message only 1.9.0 reads; a player action needs 1.7.0, and 1.8.0 when its screenshot leaves
     * something out; a reconnect's logout 1.7.0; a quit 1.5.0; any other step the first version.
     */
    static int[] neededVersion(Step step) {
        switch (step.kind) {
            case NEW_STEP:
                return ToolkitChannel.VERSION_1_9_0;
            case PLAYER_ACTION:
                return step.hideScreenshot ? ToolkitChannel.VERSION_1_8_0 : ToolkitChannel.VERSION_1_7_0;
            default:
                if (step.logout) {
                    return ToolkitChannel.VERSION_1_7_0;
                }
                return ClientActionRequest.QUIT.equals(step.name) ? ToolkitChannel.VERSION_1_5_0 : FIRST_VERSION;
        }
    }

    /** The refusal of this step, or null when it may be sent. */
    static Refusal refusal(Step step, Facts facts) {
        if (step.kind == Kind.LIFECYCLE) {
            return lifecycleRefusal(step, facts);
        }
        // The version first: an older client has no handler for the step's message and would
        // drop the connection on it, whatever it allows.
        if (!facts.speaksActions) {
            return Refusal.NO_CLIENT;
        }
        if (tooOld(step, facts)) {
            return Refusal.CLIENT_TOO_OLD;
        }
        if (!Boolean.TRUE.equals(facts.allowed)) {
            return Refusal.NOT_ALLOWED;
        }
        if (facts.busy) {
            return Refusal.BUSY;
        }
        if (facts.dead && needsLivingPlayer(step)) {
            // A dead player's click would act on nothing.
            return Refusal.DEAD;
        }
        return null;
    }

    private static Refusal lifecycleRefusal(Step step, Facts facts) {
        if (ClientActionRequest.RESPAWN.equals(step.name) && !facts.dead) {
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
        if (tooOld(step, facts)) {
            return Refusal.CLIENT_TOO_OLD;
        }
        if (!Boolean.TRUE.equals(facts.allowed)) {
            return Refusal.NOT_ALLOWED;
        }
        return null;
    }

    /**
     * Whether the client's hello is older than the step needs. Judged once a hello has come; a
     * quit's version only for a client that allows actions, as when quit was added, so a 1.4.0
     * client that does not allow them is still told {@code notAllowed}.
     */
    private static boolean tooOld(Step step, Facts facts) {
        int[] needed = neededVersion(step);
        if (needed == FIRST_VERSION) {
            return false;
        }
        boolean judged = ClientActionRequest.QUIT.equals(step.name) && step.kind == Kind.LIFECYCLE
                ? Boolean.TRUE.equals(facts.allowed) : facts.allowed != null;
        return judged && !atLeast(facts.helloVersion, needed);
    }

    /**
     * Whether a dead player cannot take this step. Choosing a slot, opening the inventory,
     * listing the bindings, the camera view and describing the screen are sent to a dead player.
     */
    private static boolean needsLivingPlayer(Step step) {
        if (step.kind == Kind.PLAYER_ACTION) {
            return PlayerActionOptions.needsLivingPlayer(step.name);
        }
        return InputOptions.needsLivingPlayer(step.name) && ScreenOptions.needsLivingPlayer(step.name);
    }

    /**
     * The arguments of a refusal's message. A version refusal folds the client's version and the
     * one needed into the player argument: an older client shows the message from its own
     * language file, which has one placeholder, so only text inside that first argument reaches
     * it. The two versions follow on their own for whoever reads the arguments.
     */
    static Object[] arguments(Refusal refusal, String player, String helloVersion, Step step) {
        if (refusal != Refusal.NO_CLIENT && refusal != Refusal.CLIENT_TOO_OLD) {
            return new Object[] {player};
        }
        String has = helloVersion == null || helloVersion.isEmpty() ? "none" : helloVersion;
        String needs = ToolkitChannel.versionText(neededVersion(step));
        return new Object[] {player + " (client toolkit " + has + ", this step needs " + needs + ")", has, needs};
    }

    private static boolean atLeast(String version, int[] minimum) {
        return version != null && ToolkitChannel.atLeast(version, minimum);
    }
}
