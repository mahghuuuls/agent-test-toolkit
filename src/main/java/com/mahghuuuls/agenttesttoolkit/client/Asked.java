package com.mahghuuuls.agenttesttoolkit.client;

import com.mahghuuuls.agenttesttoolkit.inbox.InputOptions;
import com.mahghuuuls.agenttesttoolkit.net.InputRequest;
import com.mahghuuuls.agenttesttoolkit.net.PlayerActionRequest;

/**
 * One step a server asked of this client's player, from either message that carries one: a
 * player action, or an input step. What every step shares (its name, token and tick, its delay,
 * its screenshot, whether it keeps the running holds) is read the same way for both, so the
 * delay, the release of holds and the records treat them alike.
 */
final class Asked {

    /** The player action; null for an input step. */
    final PlayerActionRequest action;
    /** The input step; null for a player action. */
    final InputRequest input;

    Asked(PlayerActionRequest action) {
        this.action = action;
        this.input = null;
    }

    Asked(InputRequest input) {
        this.action = null;
        this.input = input;
    }

    /** The input step's words; null for a player action. */
    InputOptions options() {
        return input == null ? null : input.getOptions();
    }

    String step() {
        return action != null ? action.getStep() : input.getStep();
    }

    String token() {
        return action != null ? action.getToken() : input.getToken();
    }

    long requestTick() {
        return action != null ? action.getRequestTick() : input.getRequestTick();
    }

    /**
     * Whether it can be taken: an input step from a newer server may carry words this client does
     * not read, or an aim point that is no number.
     */
    boolean parsed() {
        return action != null || input.isValid();
    }

    int delay() {
        return action != null ? action.getDelay() : parsed() ? options().getDelay() : 0;
    }

    String screenshot() {
        return action != null ? action.getScreenshot() : parsed() ? options().getScreenshot() : "";
    }

    int screenshotDelay() {
        return action != null ? action.getScreenshotDelay() : parsed() ? options().getScreenshotDelay() : 0;
    }

    String screenshotHide() {
        return action != null ? action.getScreenshotHide() : parsed() ? options().getScreenshotHide() : "";
    }

    /** A running hold of another binding, and a running turn, are left running. */
    boolean keepHold() {
        return input != null && parsed() && options().isKeepHold();
    }

    /** Which running holds and turn taking this step ends, and with which reason. */
    HoldRelease.Ending ending() {
        String mode = input != null && parsed() ? options().getMode() : "";
        return HoldRelease.ending(step(), mode, keepHold(), parsed());
    }
}
