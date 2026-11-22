package com.mahghuuuls.agenttesttoolkit.wait;

import com.mahghuuuls.agenttesttoolkit.bundle.BundleExecution;
import com.mahghuuuls.agenttesttoolkit.bundle.WaitCondition;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;

/**
 * Waits until the client reports the latest picture a run asked under one label: saved passes,
 * failed fails with {@link #KEY_FAILED}. The request and the report meet in {@link ClientReports},
 * so a report that came before the wait was asked counts as well.
 *
 * <p>Only a request made by the same run is waited on, so a picture with the same label asked by
 * another run or typed by a person can never answer it.
 */
public final class ScreenshotWait implements WaitCondition {

    public static final String KIND = "screenshot";
    /** The client reported that the picture could not be saved. */
    public static final String KEY_FAILED = "devtool.screenshot.failed";

    private final ClientReports.Picture picture;

    private ScreenshotWait(ClientReports.Picture picture) {
        this.picture = picture;
    }

    /** A wait on the latest picture this run asked under this label; null when it asked none. */
    public static ScreenshotWait forLatest(BundleExecution execution, String label) {
        ClientReports.Picture picture = ClientReports.latestPicture(execution, label);
        return picture == null ? null : new ScreenshotWait(picture);
    }

    /** The player whose client takes the picture waited on. */
    public java.util.UUID getPlayer() {
        return picture.getPlayer();
    }

    @Override
    public Poll poll(int elapsedTicks) {
        if (picture.saved != null) {
            return picture.saved.booleanValue() ? Poll.satisfied()
                    : Poll.failed(KEY_FAILED, "label=" + picture.label + " requestTick=" + picture.requestTick
                            + (picture.reason == null ? "" : " reason=" + picture.reason));
        }
        if (picture.playerLeft) {
            return Poll.cancelled(ClientActionWait.REASON_SENDER_LOST);
        }
        return Poll.pending();
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public void describe(LogRecord record) {
        record.add("label", picture.label);
        record.add("requestTick", picture.requestTick);
        if (picture.saved != null) {
            record.add("saved", picture.saved.booleanValue());
            record.add("file", picture.file);
            record.add("frameClientTick", picture.frameClientTick);
        }
    }
}
