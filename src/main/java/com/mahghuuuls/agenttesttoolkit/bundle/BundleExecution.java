package com.mahghuuuls.agenttesttoolkit.bundle;

import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;

import java.util.List;

/**
 * One bundle being run: where it is, what has happened, and what remains.
 *
 * <p>A scheduled state machine rather than a loop. The position is explicit state that survives
 * between advances, which is what lets per-command tick delays gate {@link #advance} instead of
 * requiring execution to be rewritten. A loop would have had to be thrown away when delays were
 * added, taking the counters and the stop rule with it.
 *
 * <p>Three things can park an execution, checked in a fixed order at the top of every advance:
 * a nested bundle it is waiting on, a {@link WaitCondition} it is waiting for, and a delay that
 * has not elapsed. Each was added after the class existed, and each extended it rather than
 * replacing it, which is the reason it was written this way before any were needed.
 *
 * <p>Free of Minecraft types on purpose. Command dispatch arrives through
 * {@link CommandDispatcher}, so ordering, failure classification, the stop rule and counter
 * accuracy are all checkable without a running game. Those are the rules that would otherwise
 * only be observable by reading a log after a manual test.
 */
public final class BundleExecution {

    /** How a wait ended. */
    public enum WaitOutcome {
        SATISFIED, TIMED_OUT, CANCELLED
    }

    /** Failure key for a wait whose deadline passed. Tolerable by declaration like any key. */
    public static final String KEY_WAIT_TIMEOUT = "devtool.wait.timeout";
    /** Failure key for a wait that became impossible, such as the sender changing dimension. */
    public static final String KEY_WAIT_CANCELLED = "devtool.wait.cancelled";

    /** Receives what happened, so record emission stays out of the state machine. */
    public interface Listener {

        /**
         * Called just before a command is dispatched, with the elapsed tick at which it became
         * due and the elapsed tick it is running on. A default no-op, because most listeners
         * only care about failures and completion.
         */
        default void onCommandStarting(BundleExecution execution, int index, String command,
                                       int dueTick, int elapsedTicks) {
        }

        /**
         * Called when a wait ends, whatever the outcome, before any failure it causes is
         * reported. A default no-op for the same reason as {@link #onCommandStarting}.
         *
         * @param reason null unless the outcome is cancelled
         */
        default void onWaitEnded(BundleExecution execution, int index, String command,
                                 WaitCondition condition, WaitOutcome outcome, String reason,
                                 int waitedTicks, int timeoutTicks) {
        }

        /**
         * Called when a command failed with a key the toolkit tolerates, built in or declared,
         * so the bundle went on. A default no-op so listeners that predate it keep compiling;
         * the recorder writes the record that makes a tolerated step visible.
         */
        default void onCommandTolerated(BundleExecution execution, int index, String command,
                                        String key) {
        }

        /** Called only for a genuine command failure, never for a command that ran and
         * changed nothing. */
        void onCommandFailed(BundleExecution execution, int index, String command, String detail);

        void onFinished(BundleExecution execution);
    }

    /**
     * Told once when this one execution has ended, after its end record: normally, early, or
     * discarded at server stop. Per execution, unlike {@link Listener}, so a caller that started
     * a run can wait for exactly that run.
     */
    public interface CompletionListener {
        void completed(BundleExecution execution, boolean discardedOnServerStop);
    }

    private final List<CompletionListener> completionListeners = new java.util.ArrayList<CompletionListener>();
    private boolean completionNotified;
    /** The detail of the first failure, which with stopOnFailure is the one that stopped it. */
    private String firstFailure;

    private final String bundleName;
    private final String hash;
    private final List<BundleCommand> commands;
    private final boolean stopOnFailure;
    private final boolean traced;
    private final CommandDispatcher dispatcher;
    private final ContextSource contextSource;

    private int position;
    private int executed;
    private int failed;
    private int tolerated;
    private int ticksElapsed;

    /**
     * The elapsed-tick count at which the command at {@code position} becomes due.
     *
     * <p>Measured in ticks since submission rather than world ticks, so the arithmetic is
     * self-contained and testable without a world. Dimensions keep independent tick counts, so
     * a world clock would also be the wrong reference for a bundle that spans dimensions.
     */
    private int dueTick;

    /**
     * The nested bundle this one is waiting on, or null.
     *
     * <p>A child cannot be run to completion inside the parent's dispatch, because a child may
     * itself contain delays and therefore span ticks. So the parent parks: it stops advancing
     * until the child finishes, then folds the child's outcome in as the result of the single
     * {@code devtool run} command that started it.
     *
     * <p>That is what makes "a child's failure counts as one failed command in the parent"
     * true rather than aspirational. Treating the child as fire-and-forget would let a parent
     * report success while the routine it delegated to failed.
     */
    private BundleExecution child;

    /**
     * The condition this execution is waiting for, or null.
     *
     * <p>Registered by the command that asked to wait, during its own dispatch, the same way a
     * nested bundle registers itself. The deadline is held here rather than in the condition
     * so that no condition can be written without one; a wait that could last forever would
     * hang every bundle after it with no record of why.
     */
    private WaitCondition wait;
    private int waitIndex;
    /** The command text as dispatched, after placeholders, for the records a wait writes. */
    private String waitCommandText;
    /** The text being dispatched right now; read by {@link #await} to remember it. */
    private String dispatchingText;
    private int waitStartedTick;
    private int waitDeadlineTick;
    private int waitTimeoutTicks;

    /** The chain of bundles that led here. Set for nested executions; see BundleCallStack. */
    private BundleCallStack callStack;

    /** Who started this run and how. Set by the submitter; null when it did not say. */
    private RunOrigin origin;

    /** Receives this execution's records and its end, or null when no result was asked for. */
    private ExecutionSink sink;
    private boolean stoppedEarly;
    private boolean finished;
    private boolean senderLost;

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
        this.hash = definition.getHash();
        this.commands = definition.getCommands();
        this.stopOnFailure = definition.isStopOnFailure();
        this.traced = definition.isTraced();
        this.dispatcher = dispatcher;
        this.contextSource = contextSource;
        // The first command's delay runs from submission. An empty bundle leaves this at zero
        // and falls straight through to completion.
        this.dueTick = commands.isEmpty() ? 0 : commands.get(0).getDelayTicks();
    }

    /**
     * Runs the commands that are due now.
     *
     * <p>A command with no delay runs immediately, so an undelayed bundle completes in a single
     * advance. A delayed one parks here and resumes on a later tick.
     */
    public void advance(Listener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        if (finished) {
            return;
        }

        // Checked before any command runs, so a bundle whose caller disconnected stops rather
        // than executing the remainder against nobody. This falls out of the normal path
        // because the dispatcher re-resolves the sender each time instead of holding a
        // reference to it.
        if (!dispatcher.isSenderAvailable()) {
            if (wait != null) {
                // The wait is ended explicitly, so the log shows it did not simply vanish.
                endWait(listener, WaitOutcome.CANCELLED, "senderLost");
                wait.ended();
                wait = null;
            }
            senderLost = true;
            stoppedEarly = true;
            finished = true;
            finishWith(listener);
            return;
        }

        // An empty bundle falls straight through to the completion below rather than being
        // marked finished in the constructor. The parser allows an empty command list, and
        // short-circuiting it earlier would emit a BUNDLE_START with no matching BUNDLE_END,
        // leaving an agent to parse boundaries that do not pair up.
        // Waiting on a nested bundle. Resolved before anything else, since the parent must not
        // advance past the command that started the child.
        if (child != null) {
            if (!child.isFinished()) {
                return;
            }
            boolean childFailed = child.getFailed() > 0 || child.isSenderLost();
            String childName = child.getBundleName();
            String childFailure = child.getFirstFailure();
            child = null;

            if (childFailed) {
                failed++;
                noteFailure(childFailure != null ? childFailure : "nested bundle reported failures");
                listener.onCommandFailed(this, position - 1,
                        "devtool run " + childName, "nested bundle reported failures");
                if (stopOnFailure) {
                    stoppedEarly = true;
                    finished = true;
                    finishWith(listener);
                    return;
                }
            }
        }

        // Waiting on a condition. Polled once per tick; the deadline turns pending into a
        // timeout. Either failure travels the same path as a thrown command error, so the
        // stop rule and tolerated keys need no special case.
        if (wait != null) {
            WaitCondition.Poll poll = wait.poll(ticksElapsed - waitStartedTick);
            WaitOutcome outcome;
            String reason = null;
            if (poll.getStatus() == WaitCondition.Status.SATISFIED) {
                outcome = WaitOutcome.SATISFIED;
            } else if (poll.getStatus() == WaitCondition.Status.CANCELLED) {
                outcome = WaitOutcome.CANCELLED;
                reason = poll.getReason();
            } else if (ticksElapsed >= waitDeadlineTick) {
                outcome = WaitOutcome.TIMED_OUT;
            } else {
                return;
            }
            endWait(listener, outcome, reason);
            String command = waitCommandText;
            String kind = wait.kind();
            wait.ended();
            wait = null;

            // Told to the caller here, because the wait ended on a tick with no command running
            // and the usual red message from a failing command has nowhere to come from.
            int waited = ticksElapsed - waitStartedTick;
            if (outcome == WaitOutcome.SATISFIED) {
                dispatcher.report("[DevToolkit] Wait for " + kind + " satisfied after " + waited
                        + " ticks.", false);
            } else if (outcome == WaitOutcome.TIMED_OUT) {
                dispatcher.report("[DevToolkit] Wait for " + kind + " timed out after " + waited
                        + " ticks: " + KEY_WAIT_TIMEOUT, true);
            } else {
                dispatcher.report("[DevToolkit] Wait for " + kind + " cancelled after " + waited
                        + " ticks (" + reason + "): " + KEY_WAIT_CANCELLED, true);
            }

            // The next command's delay is measured from the end of the wait, not from the
            // moment the waiting command was dispatched. A wait is the completion of that
            // command, and a delay after it means "after the wait".
            if (position < commands.size()) {
                dueTick = ticksElapsed + commands.get(position).getDelayTicks();
            }

            if (outcome != WaitOutcome.SATISFIED) {
                String key = outcome == WaitOutcome.TIMED_OUT ? KEY_WAIT_TIMEOUT : KEY_WAIT_CANCELLED;
                if (commands.get(waitIndex).getToleratedFailures().contains(key)) {
                    tolerated++;
                    listener.onCommandTolerated(this, waitIndex, command, key);
                } else {
                    failed++;
                    noteFailure(key);
                    listener.onCommandFailed(this, waitIndex, command, key);
                    if (stopOnFailure) {
                        stoppedEarly = true;
                        finished = true;
                        finishWith(listener);
                        return;
                    }
                }
            }
        }

        while (position < commands.size()) {
            // Not yet due, so the execution stays active and unfinished. Returning here rather
            // than falling through is what keeps BUNDLE_END after the last delayed command
            // instead of before it.
            if (ticksElapsed < dueTick) {
                return;
            }

            BundleCommand command = commands.get(position);
            int index = position;
            position++;
            executed++;

            // Placeholders are filled in before anything is announced, so the step record and
            // any failure record show the text that ran. A placeholder that cannot be filled
            // fails the command with its own key, tolerable like any other, and nothing runs.
            String text;
            try {
                text = dispatcher.resolve(command.getCommand());
            } catch (PlaceholderException e) {
                listener.onCommandStarting(this, index, command.getCommand(), dueTick, ticksElapsed);
                dispatcher.report("[DevToolkit] " + e.getMessage() + " (" + e.getKey() + ")", true);
                if (position < commands.size()) {
                    dueTick = ticksElapsed + commands.get(position).getDelayTicks();
                }
                if (command.getToleratedFailures().contains(e.getKey())) {
                    tolerated++;
                    listener.onCommandTolerated(this, index, command.getCommand(), e.getKey());
                    continue;
                }
                failed++;
                noteFailure(e.getKey());
                listener.onCommandFailed(this, index, command.getCommand(), e.getKey());
                if (stopOnFailure) {
                    stoppedEarly = true;
                    break;
                }
                continue;
            }

            listener.onCommandStarting(this, index, text, dueTick, ticksElapsed);

            dispatchingText = text;
            CommandOutcome outcome;
            try {
                outcome = dispatcher.dispatch(text, command.getToleratedFailures());
            } finally {
                dispatchingText = null;
            }

            // The next command's delay is measured from *now*, the completion of this one,
            // rather than from the bundle's start. That is the behaviour someone editing a
            // list expects: inserting a command shifts everything after it rather than
            // silently compressing the gaps.
            if (position < commands.size()) {
                dueTick = ticksElapsed + commands.get(position).getDelayTicks();
            }

            // The dispatched command may have been `devtool run`, which parks this execution
            // on a child, or a wait command, which parks it on a condition. Checked
            // immediately, so the parent stops here rather than running the rest of its list
            // while the child is still going or the condition is still pending.
            if (child != null || wait != null) {
                return;
            }

            if (outcome == null || outcome.succeeded()) {
                if (outcome != null && outcome.getToleratedKey() != null) {
                    tolerated++;
                    listener.onCommandTolerated(this, index, text, outcome.getToleratedKey());
                }
                continue;
            }

            failed++;
            noteFailure(outcome.getDetail());
            listener.onCommandFailed(this, index, text, outcome.getDetail());

            if (stopOnFailure) {
                stoppedEarly = true;
                break;
            }
        }

        finished = true;
        finishWith(listener);
    }

    /** Ends the run: the listener writes the end record, then the sink, if any, is told. */
    private void finishWith(Listener listener) {
        listener.onFinished(this);
        if (sink != null) {
            sink.finished(this);
        }
        notifyCompletion(false);
    }

    private void noteFailure(String detail) {
        if (firstFailure == null) {
            firstFailure = detail;
        }
    }

    /** @return the detail of the first failed command, or null when none failed. */
    public String getFirstFailure() {
        return firstFailure;
    }

    /** Registers a listener told once when this execution ends. */
    public void addCompletionListener(CompletionListener completion) {
        if (completion != null) {
            completionListeners.add(completion);
        }
    }

    /** Called once the end record for a run discarded at server stop has been written. */
    public void notifyDiscarded() {
        notifyCompletion(true);
    }

    private void notifyCompletion(boolean discarded) {
        if (completionNotified) {
            return;
        }
        completionNotified = true;
        for (CompletionListener completion : completionListeners) {
            try {
                completion.completed(this, discarded);
            } catch (RuntimeException e) {
                // Contained, so one listener cannot stop the next, the scheduler, or a server
                // stop that still has bundles to record and an inbox to close.
                com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog.error(
                        "Bundle completion listener failed", bundleName + ": " + e);
            }
        }
    }

    private void endWait(Listener listener, WaitOutcome outcome, String reason) {
        listener.onWaitEnded(this, waitIndex, waitCommandText, wait,
                outcome, reason, ticksElapsed - waitStartedTick, waitTimeoutTicks);
    }

    /**
     * Ends a pending wait as cancelled because the server is stopping.
     *
     * <p>Called when in-flight executions are discarded, so a wait does not simply vanish
     * from the log: the record says the server stopped, which is a different fact from a
     * timeout or a lost caller.
     */
    public void abandonWait(Listener listener) {
        if (wait != null) {
            endWait(listener, WaitOutcome.CANCELLED, "serverStopping");
            wait.ended();
            wait = null;
        }
    }

    /** Called once per server tick while this execution is active, for the duration count. Public so a
     * test in another package can drive the clock the way the scheduler does. */
    public void onTick() {
        ticksElapsed++;
    }

    /**
     * Parks this execution until the given child finishes.
     *
     * <p>Called from the command layer while this execution's {@code devtool run} command is
     * being dispatched, which is the only moment a nested call can be recognised.
     */
    public void awaitChild(BundleExecution childExecution) {
        this.child = childExecution;
    }

    /**
     * Parks this execution until the condition holds, is cancelled, or the timeout elapses.
     *
     * <p>Called from the command layer while a wait command is being dispatched, exactly as
     * {@link #awaitChild} is. The timeout is mandatory and counts from now.
     *
     * @throws IllegalStateException if a wait is already registered, which would mean two
     *                               commands ran in one dispatch, or the timeout is not positive
     */
    public void await(WaitCondition condition, int timeoutTicks) {
        if (condition == null) {
            throw new IllegalArgumentException("condition must not be null");
        }
        if (timeoutTicks <= 0) {
            throw new IllegalArgumentException("timeoutTicks must be positive: " + timeoutTicks);
        }
        if (wait != null) {
            throw new IllegalStateException("already waiting on " + wait.kind());
        }
        this.wait = condition;
        // position has already advanced past the command being dispatched.
        this.waitIndex = position - 1;
        if (dispatchingText != null) {
            this.waitCommandText = dispatchingText;
        } else if (waitIndex >= 0 && waitIndex < commands.size()) {
            this.waitCommandText = commands.get(waitIndex).getCommand();
        } else {
            this.waitCommandText = null;
        }
        this.waitStartedTick = ticksElapsed;
        this.waitTimeoutTicks = timeoutTicks;
        this.waitDeadlineTick = ticksElapsed + timeoutTicks;
    }

    /** Index of the command most recently dispatched, or -1 before the first. */
    public int getLastDispatchedIndex() {
        return position - 1;
    }

    /**
     * Attaches the sink that receives this execution's records.
     *
     * <p>Set before the start record is written and never replaced, so a result file covers
     * the whole run or none of it.
     */
    public void attachSink(ExecutionSink next) {
        if (this.sink != null) {
            throw new IllegalStateException("a sink is already attached");
        }
        this.sink = next;
    }

    /** The attached sink, or null. */
    public ExecutionSink getSink() {
        return sink;
    }

    /** Whether this execution is currently parked on a condition. */
    public boolean isWaiting() {
        return wait != null;
    }

    /** The chain that led to this execution, for nesting guards. Never null for a root. */
    public BundleCallStack getCallStack() {
        return callStack == null ? BundleCallStack.root(bundleName) : callStack;
    }

    public void setCallStack(BundleCallStack callStack) {
        this.callStack = callStack;
    }

    public void setOrigin(RunOrigin origin) {
        this.origin = origin;
    }

    /** @return who started this run and how, or null when the submitter did not say. */
    public RunOrigin getOrigin() {
        return origin;
    }

    public String getBundleName() {
        return bundleName;
    }

    /** The normalized hash of the definition this run came from. */
    public String getHash() {
        return hash;
    }

    /** Whether the bundle asked for a step record per command. */
    public boolean isTraced() {
        return traced;
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

    /** Commands that failed with a tolerated key. Counted among {@link #getExecuted()} and not
     * among {@link #getFailed()}. */
    public int getTolerated() {
        return tolerated;
    }

    /** The bundle's full length, so the number skipped after an early stop is derivable. */
    public int getTotal() {
        return commands.size();
    }

    /** Whether execution halted before the end, by {@code stopOnFailure} or a lost sender. */
    public boolean isStoppedEarly() {
        return stoppedEarly;
    }

    /**
     * Whether execution ended because the caller could no longer be reached.
     *
     * <p>Distinct from {@code stoppedEarly} on purpose. Both mean the bundle did not finish,
     * but "a command failed" and "the player left" call for completely different responses, and
     * an agent should not have to infer which happened from the absence of a failure record.
     */
    public boolean isSenderLost() {
        return senderLost;
    }

    public int getDurationTicks() {
        return ticksElapsed;
    }
}
