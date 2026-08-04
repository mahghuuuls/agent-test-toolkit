package com.mahghuuuls.agenttesttoolkit.bundle;

import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;

import java.util.List;

/**
 * One bundle being run: where it is, what has happened, and what remains.
 *
 * <p><b>ARC-004</b> requires a scheduled state machine rather than a loop. The position is
 * explicit state that survives between advances, so IMP-014 can add per-command tick delays by
 * gating {@link #advance} rather than by rewriting execution. A loop would have had to be
 * thrown away at that point, and the interesting parts, the counters and the stop rule, would
 * have been rewritten with it.
 *
 * <p>Free of Minecraft types on purpose. Command dispatch arrives through
 * {@link CommandDispatcher}, so ordering, failure classification, the stop rule and counter
 * accuracy are all checkable without a running game. Those are the rules that would otherwise
 * only be observable by reading a log after a manual test.
 */
public final class BundleExecution {

    /** Receives what happened, so record emission stays out of the state machine. */
    public interface Listener {

        /** REQ-016: called only for a genuine command failure, never for a command that ran
         * and changed nothing. */
        void onCommandFailed(BundleExecution execution, int index, String command, String detail);

        void onFinished(BundleExecution execution);
    }

    private final String bundleName;
    private final List<BundleCommand> commands;
    private final boolean stopOnFailure;
    private final CommandDispatcher dispatcher;
    private final ContextSource contextSource;

    private int position;
    private int executed;
    private int failed;
    private int ticksElapsed;
    private boolean stoppedEarly;
    private boolean finished;

    public BundleExecution(BundleDefinition definition, CommandDispatcher dispatcher,
                           ContextSource contextSource) {
        if (definition == null) {
            throw new IllegalArgumentException("definition must not be null");
        }
        if (dispatcher == null) {
            throw new IllegalArgumentException("dispatcher must not be null");
        }
        if (contextSource == null) {
            throw new IllegalArgumentException("contextSource must not be null");
        }
        this.bundleName = definition.getName();
        this.commands = definition.getCommands();
        this.stopOnFailure = definition.isStopOnFailure();
        this.dispatcher = dispatcher;
        this.contextSource = contextSource;
    }

    /**
     * Runs the commands that are due now.
     *
     * <p>Every remaining command is due, because per-command delays do not exist yet; they are
     * IMP-014's, and this is the method they will gate. The structure is deliberate even though
     * today it means a bundle completes in a single advance.
     */
    public void advance(Listener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        if (finished) {
            return;
        }

        // An empty bundle falls straight through to the completion below rather than being
        // marked finished in the constructor. The parser allows an empty command list, and
        // short-circuiting it earlier would emit a BUNDLE_START with no matching BUNDLE_END,
        // leaving an agent to parse boundaries that do not pair up.
        while (position < commands.size()) {
            BundleCommand command = commands.get(position);
            int index = position;
            position++;
            executed++;

            CommandOutcome outcome = dispatcher.dispatch(command.getCommand());
            if (outcome == null || outcome.succeeded()) {
                continue;
            }

            failed++;
            listener.onCommandFailed(this, index, command.getCommand(), outcome.getDetail());

            if (stopOnFailure) {
                stoppedEarly = true;
                break;
            }
        }

        finished = true;
        listener.onFinished(this);
    }

    /** Called once per server tick while this execution is active, for the duration count. */
    void onTick() {
        ticksElapsed++;
    }

    public String getBundleName() {
        return bundleName;
    }

    /** The context as it is <i>now</i>, not as it was when the bundle started. */
    public RecordContext.Snapshot getContext() {
        RecordContext.Snapshot snapshot = contextSource.snapshot();
        return snapshot == null ? RecordContext.Snapshot.NONE : snapshot;
    }

    public boolean isFinished() {
        return finished;
    }

    /**
     * Commands dispatched, <b>including</b> those that failed.
     *
     * <p>Stated explicitly because an agent reading {@code executed=4 failed=1} has to know
     * whether three succeeded or four did. Successes are {@code executed - failed}.
     */
    public int getExecuted() {
        return executed;
    }

    public int getFailed() {
        return failed;
    }

    /** The bundle's full length, so the number skipped after an early stop is derivable. */
    public int getTotal() {
        return commands.size();
    }

    /** Whether execution halted before the end because {@code stopOnFailure} was in effect. */
    public boolean isStoppedEarly() {
        return stoppedEarly;
    }

    public int getDurationTicks() {
        return ticksElapsed;
    }
}
