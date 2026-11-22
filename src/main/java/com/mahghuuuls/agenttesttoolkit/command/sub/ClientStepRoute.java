package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.inbox.ClientWaitOptions;
import com.mahghuuuls.agenttesttoolkit.inbox.InputOptions;
import com.mahghuuuls.agenttesttoolkit.inbox.PlayerActionOptions;
import com.mahghuuuls.agenttesttoolkit.inbox.ScreenOptions;
import com.mahghuuuls.agenttesttoolkit.net.ClientActionRequest;

import java.util.Locale;

/**
 * Which message carries the words after {@code client}. Kept apart from the command so the choice
 * is tested without a game: a click a 1.7.0 client already takes must keep going in the player
 * actions' message, and only what needs the newer messages may be refused for an older client.
 * A bundle wait's options are taken off first and kept here: the server acts on them, and the
 * client is sent the step's words as they were before those options existed.
 */
final class ClientStepRoute {

    /** The message a step travels in. */
    enum Message {
        /** The screen message, read from 1.9.0. */
        SCREEN,
        /** The input message, read from 1.9.0. */
        INPUT,
        /** The player actions' message, read from 1.7.0. */
        PLAYER_ACTION,
        /** Reconnect, respawn, close, quit; their own words are read by the command. */
        LIFECYCLE
    }

    final Message message;
    /** The step's name, lower case. */
    final String step;
    final ScreenOptions screen;
    final InputOptions input;
    final PlayerActionOptions action;
    /** How the step waits in its bundle; a step without those options does not wait. */
    final ClientWaitOptions wait;

    private ClientStepRoute(Message message, String step, ScreenOptions screen, InputOptions input,
                            PlayerActionOptions action, ClientWaitOptions wait) {
        this.message = message;
        this.step = step;
        this.screen = screen;
        this.input = input;
        this.action = action;
        this.wait = wait;
    }

    /** The route of these words, or null when they are no step at all and the usage is due. */
    static ClientStepRoute of(String[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        String step = args[0].toLowerCase(Locale.ROOT);
        String[] words = args.clone();
        words[0] = step;
        ClientWaitOptions wait = ClientWaitOptions.split(words);
        if (wait == null) {
            return null;
        }
        words = wait.getWords();
        ScreenOptions screen = ScreenOptions.parse(words);
        if (screen != null) {
            return new ClientStepRoute(Message.SCREEN, step, screen, null, null, wait);
        }
        InputOptions input = InputOptions.parse(words);
        if (input != null && input.travelsAsInput()) {
            return new ClientStepRoute(Message.INPUT, step, null, input, null, wait);
        }
        if (input != null) {
            // A plain click with the binding route spelled out: the click every 1.7.0 client takes.
            words = input.toPlayerActionWords();
        }
        if (PlayerActionOptions.isStep(step)) {
            PlayerActionOptions action = PlayerActionOptions.parse(words);
            return action == null ? null
                    : new ClientStepRoute(Message.PLAYER_ACTION, step, null, null, action, wait);
        }
        return ClientActionRequest.isValidStep(step)
                ? new ClientStepRoute(Message.LIFECYCLE, step, null, null, null, wait) : null;
    }

    /** Whether the step pauses its bundle for the client's answer, so it needs a bundle run to pause. */
    boolean waits() {
        return wait.waits();
    }

    /**
     * Whether a step sent inside a bundle run is kept for waiting on: one that waits now, and every
     * hold, so a later {@code client await} in the same run can find it. Nothing else is kept, so a
     * step without a wait costs nothing beyond what it cost before waits existed.
     */
    boolean keptInRun() {
        return wait.waits() || heldInput() != null;
    }

    /**
     * The input a hold keeps down, which {@code client await} names it by: {@code use} or
     * {@code attack} for a hold of the mouse buttons' input, the binding's name for a key's hold;
     * null for any other step.
     */
    String heldInput() {
        if (message == Message.PLAYER_ACTION && PlayerActionOptions.HOLD.equals(step)) {
            return action.getArgument();
        }
        if (message == Message.INPUT && InputOptions.HOLD_INPUT.equals(step)) {
            return input.getMode();
        }
        if (message == Message.INPUT && InputOptions.KEY.equals(step) && InputOptions.HOLD.equals(input.getMode())) {
            return input.getName();
        }
        return null;
    }

    /** The step as the gate judges it; {@code logout} counts only for a lifecycle step. */
    ClientRequestGate.Step gateStep(boolean logout) {
        switch (message) {
            case SCREEN:
            case INPUT:
                return ClientRequestGate.Step.newStep(step);
            case PLAYER_ACTION:
                return ClientRequestGate.Step.playerAction(step, action.getScreenshotHide());
            default:
                return ClientRequestGate.Step.lifecycle(step, logout);
        }
    }
}
