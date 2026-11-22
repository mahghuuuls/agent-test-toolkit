package com.mahghuuuls.agenttesttoolkit.wait;

import com.mahghuuuls.agenttesttoolkit.bundle.WaitCondition;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;

import java.util.UUID;

/**
 * Waits for the client's answer to one step a run sent, by its token: the first answer for
 * {@code started}, the final one for {@code done}. What the answer means is
 * {@link ClientWaitDecision}'s to say; this condition only reads what {@link ClientReports} kept.
 *
 * <p>The step's token disappears when its player logs out, and then the wait is cancelled, as a
 * chunk wait is when its player leaves: the client that comes back can never answer it.
 */
public final class ClientActionWait implements WaitCondition {

    public static final String KIND = "client";
    /** The cancellation reason when the step's player left and its token was forgotten. */
    static final String REASON_SENDER_LOST = "senderLost";

    private final UUID player;
    private final String token;
    private final String mode;
    /** The input named by {@code client await}; null for a step's own wait. */
    private final String input;
    private final String step;
    private final long requestTick;
    private final String expectUuid;
    private ClientReports.Report seen;
    private ClientReports.Report target;
    private ClientWaitDecision.Decision decision;

    private ClientActionWait(UUID player, String token, String mode, String input, ClientReports.Step kept) {
        this.player = player;
        this.token = token;
        // An expected target is only known once the action is over.
        this.mode = kept.expectUuid != null ? ClientWaitDecision.MODE_DONE : mode;
        this.input = input;
        this.step = kept.step;
        this.requestTick = kept.requestTick;
        this.expectUuid = kept.expectUuid;
    }

    /**
     * A wait on a step {@link ClientReports} keeps, or null when it keeps none by that token.
     *
     * @param mode  {@link ClientWaitDecision#MODE_STARTED} or {@link ClientWaitDecision#MODE_DONE}
     * @param input the input named by {@code client await}, for the record; null for a step's own wait
     */
    public static ClientActionWait forToken(UUID player, String token, String mode, String input) {
        ClientReports.Step kept = ClientReports.step(player, token);
        return kept == null ? null : new ClientActionWait(player, token, mode, input, kept);
    }

    @Override
    public Poll poll(int elapsedTicks) {
        ClientReports.Step kept = ClientReports.step(player, token);
        if (kept == null) {
            return Poll.cancelled(REASON_SENDER_LOST);
        }
        seen = ClientWaitDecision.MODE_STARTED.equals(mode) ? kept.first : kept.last;
        target = kept.target;
        String reportedUuid = target != null && "entity".equals(target.field("target"))
                ? target.field("entityUuid") : null;
        decision = ClientWaitDecision.decide(mode,
                seen == null ? null : seen.result,
                seen == null ? null : seen.reason,
                seen == null ? null : seen.field("released"),
                seen == null ? null : seen.field("heldTicks"),
                expectUuid, reportedUuid, target == null ? null : target.field("alive"));
        switch (decision.getStatus()) {
            case SATISFIED:
                return Poll.satisfied();
            case FAILED:
                return Poll.failed(decision.getKey(), decision.getDetail());
            default:
                return Poll.pending();
        }
    }

    @Override
    public String kind() {
        return KIND;
    }

    /** The step, its request tick, what was waited for, and what the client last said. */
    @Override
    public void describe(LogRecord record) {
        record.add("step", step);
        record.add("requestTick", requestTick);
        record.add("mode", mode);
        record.add("input", input);
        record.add("result", seen == null ? "none" : seen.result);
        if (seen != null) {
            record.add("released", seen.field("released"));
            record.add("heldTicks", seen.field("heldTicks"));
        }
        if (expectUuid != null) {
            record.add("expectUuid", expectUuid);
            if (target != null) {
                record.add("entityUuid", target.field("entityUuid"));
                record.add("alive", target.field("alive"));
            }
        }
        if (decision != null && decision.getStatus() == ClientWaitDecision.Status.FAILED) {
            record.add("detail", decision.getDetail());
        }
    }
}
