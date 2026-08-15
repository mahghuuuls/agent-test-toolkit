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
 * <p>Free of Minecraft types on purpose. Command dispatch arrives through
 * {@link CommandDispatcher}, so ordering, failure classification, the stop rule and counter
 * accuracy are all checkable without a running game. Those are the rules that would otherwise
 * only be observable by reading a log after a manual test.
 */
public final class BundleExecution {

    /** Receives what happened, so record emission stays out of the state machine. */
    public interface Listener {

        /** Called only for a genuine command failure, never for a command that ran and
         * changed nothing. */
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

    /** The chain of bundles that led here. Set for nested executions; see BundleCallStack. */
    private BundleCallStack callStack;
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
        this.commands = definition.getCommands();
        this.stopOnFailure = definition.isStopOnFailure();
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
            senderLost = true;
            stoppedEarly = true;
            finished = true;
            listener.onFinished(this);
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
            child = null;

            if (childFailed) {
                failed++;
                listener.onCommandFailed(this, position - 1,
                        "devtool run " + childName, "nested bundle reported failures");
                if (stopOnFailure) {
                    stoppedEarly = true;
                    finished = true;
                    listener.onFinished(this);
                    return;
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

            CommandOutcome outcome =
                    dispatcher.dispatch(command.getCommand(), command.getToleratedFailures());

            // The next command's delay is measured from *now*, the completion of this one,
            // rather than from the bundle's start. That is the behaviour someone editing a
            // list expects: inserting a command shifts everything after it rather than
            // silently compressing the gaps.
            if (position < commands.size()) {
                dueTick = ticksElapsed + commands.get(position).getDelayTicks();
            }

            // The dispatched command may have been `devtool run`, which parks this execution
            // on a child. Checked immediately, so the parent stops here rather than running
            // the rest of its list while the child is still going.
            if (child != null) {
                return;
            }

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

    /**
     * Parks this execution until the given child finishes.
     *
     * <p>Called from the command layer while this execution's {@code devtool run} command is
     * being dispatched, which is the only moment a nested call can be recognised.
     */
    public void awaitChild(BundleExecution childExecution) {
        this.child = childExecution;
    }

    /** The chain that led to this execution, for nesting guards. Never null for a root. */
    public BundleCallStack getCallStack() {
        return callStack == null ? BundleCallStack.root(bundleName) : callStack;
    }

    public void setCallStack(BundleCallStack callStack) {
        this.callStack = callStack;
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
