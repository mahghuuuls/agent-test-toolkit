package com.mahghuuuls.agenttesttoolkit.wait;

import com.mahghuuuls.agenttesttoolkit.bundle.WaitCondition;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;

import java.util.function.BooleanSupplier;

/**
 * The armed checkpoint and the most recent cue, and the wait that an armed checkpoint is.
 *
 * <p>A checkpoint is the toolkit's way of talking to the human at a precise moment: arm it,
 * and the bundle pauses until they say they are ready; cue it, and they see the label large
 * and hear a sound; acknowledge it, and the ticks between are written down. None of that
 * touches the mod under test. Acknowledging records a tick and nothing else, which is the
 * boundary that keeps a checkpoint from ever pretending to be the action it cues.
 *
 * <p>One armed checkpoint at a time. Two would mean two bundles waiting on the same person,
 * and an acknowledgement could not say which it answered.
 *
 * <p>Free of Minecraft types. Whether the operator is still present arrives as a supplier the
 * command layer builds, so the arm, ack and timeout rules are testable without a game.
 */
public final class OperatorCheckpoints {

    public static final String KIND = "checkpoint";

    /** A checkpoint that has been armed and may be acknowledged. Also the wait condition. */
    public static final class Armed implements WaitCondition {
        private final String label;
        private final long armedTick;
        private final BooleanSupplier operatorPresent;
        private long ackTick = -1L;
        private String cancelledReason;

        Armed(String label, long armedTick, BooleanSupplier operatorPresent) {
            this.label = label;
            this.armedTick = armedTick;
            this.operatorPresent = operatorPresent;
        }

        public String getLabel() {
            return label;
        }

        public long getArmedTick() {
            return armedTick;
        }

        /** The world tick of the acknowledgement, or -1 while none has arrived. */
        public long getAckTick() {
            return ackTick;
        }

        public boolean isAcknowledged() {
            return ackTick >= 0L;
        }

        @Override
        public Poll poll(int elapsedTicks) {
            if (ackTick >= 0L) {
                return Poll.satisfied();
            }
            if (cancelledReason != null) {
                return Poll.cancelled(cancelledReason);
            }
            if (operatorPresent != null && !operatorPresent.getAsBoolean()) {
                return Poll.cancelled("operatorLost");
            }
            return Poll.pending();
        }

        /** Ends this checkpoint from outside, such as when its session stops. Polls cancelled from now on. */
        void cancel(String reason) {
            if (ackTick < 0L && cancelledReason == null) {
                cancelledReason = reason;
            }
        }

        @Override
        public String kind() {
            return KIND;
        }

        @Override
        public void describe(LogRecord record) {
            record.add("label", label);
            record.add("armedTick", armedTick);
            if (ackTick >= 0L) {
                record.add("ackTick", ackTick);
                record.add("latencyTicks", ackTick - armedTick);
            }
        }
    }

    /** What an acknowledgement answered, for the record. */
    public static final class Ack {
        public final String label;
        /** The tick the human was last asked, by arm or by cue, whichever came later. */
        public final long askedTick;
        public final long ackTick;
        public final boolean answeredArm;

        Ack(String label, long askedTick, long ackTick, boolean answeredArm) {
            this.label = label;
            this.askedTick = askedTick;
            this.ackTick = ackTick;
            this.answeredArm = answeredArm;
        }

        public long latencyTicks() {
            return ackTick - askedTick;
        }
    }

    private static volatile Armed armed;
    private static volatile String lastCueLabel;
    private static volatile long lastCueTick = -1L;

    private OperatorCheckpoints() {
    }

    /**
     * Arms a checkpoint.
     *
     * @return the armed checkpoint, to be registered as a wait, or null when one is already
     *         armed, which the caller reports as a failure
     */
    public static Armed arm(String label, long worldTick, BooleanSupplier operatorPresent) {
        if (armed != null && !armed.isAcknowledged()) {
            return null;
        }
        Armed next = new Armed(label, worldTick, operatorPresent);
        armed = next;
        return next;
    }

    /** Records a cue, so a later acknowledgement can be measured from it. */
    public static void cued(String label, long worldTick) {
        lastCueLabel = label;
        lastCueTick = worldTick;
    }

    /**
     * Acknowledges whatever the human was last asked.
     *
     * <p>An armed checkpoint is answered first, because a bundle is waiting on it. Otherwise
     * the most recent cue is answered, so the latency of a cue-only checkpoint is still
     * measured. With neither, there is nothing to answer.
     *
     * @return what was answered, or null when nothing was armed or cued
     */
    public static Ack ack(long worldTick) {
        Armed current = armed;
        if (current != null && !current.isAcknowledged()) {
            current.ackTick = worldTick;
            long asked = Math.max(current.armedTick, lastCueTick);
            // A cue that moved the ask is answered by this ack too; it must not answer twice.
            lastCueLabel = null;
            lastCueTick = -1L;
            return new Ack(current.label, asked, worldTick, true);
        }
        if (lastCueTick >= 0L) {
            Ack answered = new Ack(lastCueLabel, lastCueTick, worldTick, false);
            // A cue is answered once. A second ack with nothing new to answer is an error,
            // not a second measurement of the same moment.
            lastCueLabel = null;
            lastCueTick = -1L;
            return answered;
        }
        return null;
    }

    /** The armed, unacknowledged checkpoint, or null. */
    public static Armed getArmed() {
        Armed current = armed;
        return current != null && !current.isAcknowledged() ? current : null;
    }

    /** Forgets the armed checkpoint. Called when its wait ends for any reason. */
    public static void disarm(Armed which) {
        if (armed == which) {
            armed = null;
        }
    }

    /**
     * Forgets everything. Called when the session stops, and by tests.
     *
     * <p>A checkpoint armed for a session must not outlive it: the bundle waiting on it is
     * told, through the condition, that the wait is cancelled, rather than left to time out
     * fifty ticks after the session it belonged to has gone.
     */
    public static void reset() {
        Armed current = armed;
        if (current != null) {
            current.cancel("sessionStopped");
        }
        armed = null;
        lastCueLabel = null;
        lastCueTick = -1L;
    }
}
