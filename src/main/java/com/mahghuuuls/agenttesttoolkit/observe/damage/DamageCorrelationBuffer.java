package com.mahghuuuls.agenttesttoolkit.observe.damage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Accumulates in-flight damage correlations and releases them at a tick boundary.
 *
 * <h2>Why entries are held rather than emitted immediately</h2>
 *
 * <p>The final stage is the obvious place to emit, and it is wrong: an attack that is cancelled
 * or hits an invulnerable target never reaches it. Emitting there would silently drop exactly
 * the case an agent most needs, where a mod believes it dealt damage and nothing happened.
 * Holding entries and flushing at the end of the tick means every observed attempt produces a
 * record, including the ones that went nowhere.
 *
 * <h2>Why a list rather than one slot per target</h2>
 *
 * <p>One entity can be damaged several times in a single tick, so a single slot would lose all
 * but the last. Stages arrive synchronously and in order within one attack, so the correct
 * entry to enrich is always the most recent still-open one for that target.
 *
 * <p>Generic over the target type so the assembly rule can be exercised without Minecraft.
 * Production passes entities; tests pass strings.
 *
 * <p>Not thread safe, and does not need to be. All damage events and the tick flush occur on
 * the logical server thread.
 *
 * @param <T> the target reference, used for identity only
 */
public final class DamageCorrelationBuffer<T> {

    /** One pending correlation together with the target it belongs to. */
    public static final class Entry<T> {
        private final T target;
        private final DamageCorrelation correlation;

        Entry(T target, DamageCorrelation correlation) {
            this.target = target;
            this.correlation = correlation;
        }

        public T getTarget() {
            return target;
        }

        public DamageCorrelation getCorrelation() {
            return correlation;
        }
    }

    private final List<Entry<T>> pending = new ArrayList<Entry<T>>();

    /**
     * Opens a correlation for an observed attack attempt.
     *
     * @return the correlation, so a caller may inspect it in tests
     */
    public DamageCorrelation onAttack(T target, long worldTick, int dimension, String side,
                                      String sessionName, long sessionTick, String source,
                                      double amountRaw, double healthBefore) {
        DamageCorrelation correlation = new DamageCorrelation(
                worldTick, dimension, side, sessionName, sessionTick, source, amountRaw, healthBefore);
        pending.add(new Entry<T>(target, correlation));
        return correlation;
    }

    /**
     * Discards everything pending without emitting.
     *
     * <p>Called when the server stops. Entries hold references to entities in a world that is
     * about to be unloaded, and the observer is registered permanently, so without this a
     * crash between the attack stage and the tick-end flush would leave stale entries to be
     * emitted on the first tick of whatever world loads next, describing a dimension and
     * entity that no longer exist. In-flight bundle executions are discarded for the same
     * reason: both are server-bound transient state.
     *
     * @return how many entries were discarded
     */
    public int discardPending() {
        int discarded = pending.size();
        pending.clear();
        return discarded;
    }

    /**
     * Records the pre-mitigation amount against the most recent open correlation for a target.
     *
     * <p>Silently ignored when no correlation is open. That happens legitimately when the
     * category is enabled partway through a tick, so the later stages of an attack whose start
     * was never observed arrive with nothing to attach to. Recording a partial correlation
     * would be worse than dropping it, because its missing raw amount would be indistinguishable
     * from a genuine zero.
     */
    public void onHurt(T target, double preMitigationAmount) {
        DamageCorrelation open = findMostRecentOpen(target, false);
        if (open != null) {
            open.recordHurt(preMitigationAmount);
        }
    }

    /** Records the final amount against the most recent correlation not yet at that stage. */
    public void onDamage(T target, double finalAmount) {
        DamageCorrelation open = findMostRecentOpen(target, true);
        if (open != null) {
            open.recordDamage(finalAmount);
        }
    }

    /**
     * Removes and returns everything accumulated, in the order the attacks were observed.
     */
    public List<Entry<T>> drain() {
        if (pending.isEmpty()) {
            return Collections.emptyList();
        }
        List<Entry<T>> drained = new ArrayList<Entry<T>>(pending);
        pending.clear();
        return drained;
    }

    public boolean isEmpty() {
        return pending.isEmpty();
    }

    public int size() {
        return pending.size();
    }

    /**
     * @param forDamageStage when true, looks for a correlation that has not reached the damage
     *                       stage; otherwise one that has not reached the hurt stage
     */
    private DamageCorrelation findMostRecentOpen(T target, boolean forDamageStage) {
        for (int i = pending.size() - 1; i >= 0; i--) {
            Entry<T> entry = pending.get(i);
            if (entry.target != target) {
                continue;
            }
            DamageCorrelation correlation = entry.correlation;
            boolean open = forDamageStage ? !correlation.reachedDamage() : !correlation.reachedHurt();
            if (open) {
                return correlation;
            }
        }
        return null;
    }
}
