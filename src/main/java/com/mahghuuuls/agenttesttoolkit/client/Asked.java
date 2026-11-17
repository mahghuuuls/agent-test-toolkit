package com.mahghuuuls.agenttesttoolkit.client;

import com.mahghuuuls.agenttesttoolkit.inbox.InputOptions;
import com.mahghuuuls.agenttesttoolkit.inbox.ScreenOptions;
import com.mahghuuuls.agenttesttoolkit.net.InputRequest;
import com.mahghuuuls.agenttesttoolkit.net.PlayerActionRequest;
import com.mahghuuuls.agenttesttoolkit.net.ScreenRequest;

/**
 * One step a server asked of this client's player, from any message that carries one: a player
 * action, an input step, or a step on the open screen. What every step shares (its name, token
 * and tick, its delay, its screenshot, whether it keeps the running holds) is read once, as the
 * step arrives, by the constructor of its message, so the delay, the release of holds and the
 * records treat them alike.
 */
final class Asked {

    /** The player action; null for the others. */
    final PlayerActionRequest action;
    /** The input step; null for the others. */
    final InputRequest input;
    /** The step on the open screen; null for the others. */
    final ScreenRequest screen;

    private final String step;
    private final String token;
    private final long requestTick;
    private final boolean parsed;
    /** The shared options; as for no options when the step's words were not read. */
    private int delay;
    private String screenshot = "";
    private int screenshotDelay;
    private String screenshotHide = "";

    private Asked(PlayerActionRequest action, InputRequest input, ScreenRequest screen, String step, String token,
                  long requestTick, boolean parsed) {
        this.action = action;
        this.input = input;
        this.screen = screen;
        this.step = step;
        this.token = token;
        this.requestTick = requestTick;
        this.parsed = parsed;
    }

    Asked(PlayerActionRequest action) {
        this(action, null, null, action.getStep(), action.getToken(), action.getRequestTick(), true);
        options(action.getDelay(), action.getScreenshot(), action.getScreenshotDelay(), action.getScreenshotHide());
    }

    Asked(InputRequest input) {
        this(null, input, null, input.getStep(), input.getToken(), input.getRequestTick(), input.isValid());
        if (parsed) {
            InputOptions o = input.getOptions();
            options(o.getDelay(), o.getScreenshot(), o.getScreenshotDelay(), o.getScreenshotHide());
        }
    }

    Asked(ScreenRequest screen) {
        this(null, null, screen, screen.getStep(), screen.getToken(), screen.getRequestTick(), screen.isValid());
        if (parsed) {
            ScreenOptions o = screen.getOptions();
            options(o.getDelay(), o.getScreenshot(), o.getScreenshotDelay(), o.getScreenshotHide());
        }
    }

    private void options(int delay, String screenshot, int screenshotDelay, String screenshotHide) {
        this.delay = delay;
        this.screenshot = screenshot;
        this.screenshotDelay = screenshotDelay;
        this.screenshotHide = screenshotHide;
    }

    /** The input step's words; null for the others. */
    InputOptions options() {
        return input == null ? null : input.getOptions();
    }

    /** The screen step's words; null for the others. */
    ScreenOptions screenOptions() {
        return screen == null ? null : screen.getOptions();
    }

    String step() {
        return step;
    }

    String token() {
        return token;
    }

    long requestTick() {
        return requestTick;
    }

    /**
     * Whether it can be taken: an input or screen step from a newer server may carry words this
     * client does not read, or an aim point that is no number.
     */
    boolean parsed() {
        return parsed;
    }

    int delay() {
        return delay;
    }

    String screenshot() {
        return screenshot;
    }

    int screenshotDelay() {
        return screenshotDelay;
    }

    String screenshotHide() {
        return screenshotHide;
    }

    /** A running hold of another binding, and a running turn, are left running. */
    boolean keepHold() {
        return input != null && parsed && options().isKeepHold();
    }

    /** Which running holds and turn taking this step ends, and with which reason. */
    HoldRelease.Ending ending() {
        String mode = input != null && parsed ? options().getMode() : "";
        return HoldRelease.ending(step, mode, keepHold(), parsed);
    }
}
