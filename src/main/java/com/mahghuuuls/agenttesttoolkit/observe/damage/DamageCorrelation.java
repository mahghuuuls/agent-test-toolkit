package com.mahghuuuls.agenttesttoolkit.observe.damage;

/**
 * One logical damage attempt, accumulated across the three stages Forge exposes.
 *
 * <p>Forge fires three events for a single hit and they are not duplicates, they are stages:
 *
 * <ul>
 *   <li><b>attack</b>, the raw incoming amount, fired for every attempt including ones that
 *       will be cancelled or bounce off invulnerability;</li>
 *   <li><b>hurt</b>, the amount before armour, potions and enchantments are applied;</li>
 *   <li><b>damage</b>, the final amount about to be subtracted from health.</li>
 * </ul>
 *
 * <p>The gaps between them carry the diagnostic value. If a mod intended 8.0 and health only
 * dropped 4.0, comparing the stages says immediately whether the mod computed the wrong number
 * or armour absorbed half of it. One event alone cannot distinguish those, which is why
 * all three stages are captured but only one record is emitted.
 *
 * <p>Deliberately free of Minecraft types. The assembly rule is the part most likely to be
 * wrong and the part hardest to exercise in a game, so it is kept unit testable.
 */
public final class DamageCorrelation {

    /** Reached the final stage: damage was applied to health. */
    public static final String OUTCOME_APPLIED = "APPLIED";
    /** Observed but stopped before application, by cancellation or invulnerability. */
    public static final String OUTCOME_NOT_APPLIED = "NOT_APPLIED";

    private final long worldTick;
    private final int dimension;
    private final String side;
    private final String sessionName;
    private final long sessionTick;
    private final String source;
    private final double amountRaw;
    private final double healthBefore;

    private boolean reachedHurt;
    private double amountPreMitigation;

    private boolean reachedDamage;
    private double amountFinal;

    DamageCorrelation(long worldTick, int dimension, String side,
                      String sessionName, long sessionTick, String source,
                      double amountRaw, double healthBefore) {
        this.worldTick = worldTick;
        this.dimension = dimension;
        this.side = side;
        this.sessionName = sessionName;
        this.sessionTick = sessionTick;
        this.source = source;
        this.amountRaw = amountRaw;
        this.healthBefore = healthBefore;
    }

    void recordHurt(double preMitigationAmount) {
        this.reachedHurt = true;
        this.amountPreMitigation = preMitigationAmount;
    }

    void recordDamage(double finalAmount) {
        this.reachedDamage = true;
        this.amountFinal = finalAmount;
    }

    /**
     * The tick in which the attack was observed.
     *
     * <p>Captured at the attack stage rather than when the record is written, because records
     * are flushed at the end of the tick and would otherwise all claim the flush tick.
     */
    public long getWorldTick() {
        return worldTick;
    }

    public int getDimension() {
        return dimension;
    }

    /**
     * The logical side, captured at attack time.
     *
     * <p>Captured rather than asserted at emit time, for the same reason as {@link #worldTick}:
     * the record is written later, and a value hardcoded at that point would be correct only
     * for as long as nothing else can populate the buffer.
     */
    public String getSide() {
        return side;
    }

    /**
     * The active session's name at attack time, or null when none was active.
     *
     * <p>Both session fields are captured at attack time rather than read at flush time, and
     * this is not cosmetic. The session tick counter is advanced by its own tick handler,
     * which is registered before this observer and therefore runs first within the same END
     * phase. Reading the counter at flush would report the value for the tick <em>after</em>
     * the attack, so an {@code ENTITY_DAMAGE} record and a {@code BLOCK_PLACE} record from the
     * same tick would disagree. The design already solved this for {@code worldTick}; the same
     * reasoning applies here.
     */
    public String getSessionName() {
        return sessionName;
    }

    /** The session tick at attack time. Meaningless when {@link #getSessionName()} is null. */
    public long getSessionTick() {
        return sessionTick;
    }

    public String getSource() {
        return source;
    }

    public double getAmountRaw() {
        return amountRaw;
    }

    public double getHealthBefore() {
        return healthBefore;
    }

    public boolean reachedHurt() {
        return reachedHurt;
    }

    public double getAmountPreMitigation() {
        return amountPreMitigation;
    }

    public boolean reachedDamage() {
        return reachedDamage;
    }

    public double getAmountFinal() {
        return amountFinal;
    }

    /**
     * Whether the damage was applied.
     *
     * <p>Defaults to not-applied and only becomes applied if the final stage is reached. That
     * direction matters: a cancelled attack produces a correct record by doing nothing further,
     * rather than by something noticing an absence. An agent gets a stated result instead of
     * having to infer meaning from a missing follow-up.
     */
    public String getOutcome() {
        return reachedDamage ? OUTCOME_APPLIED : OUTCOME_NOT_APPLIED;
    }

    /**
     * Which stage stopped a non-applied attempt.
     *
     * <p>Distinguishes an attack cancelled outright from one that got as far as damage
     * calculation and was then stopped, which have different causes and different fixes.
     *
     * @return null when the damage was applied
     */
    public String getStoppedAt() {
        if (reachedDamage) {
            return null;
        }
        return reachedHurt ? "HURT" : "ATTACK";
    }
}
