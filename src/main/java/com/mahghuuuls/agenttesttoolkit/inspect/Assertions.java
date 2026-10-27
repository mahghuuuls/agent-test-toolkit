package com.mahghuuuls.agenttesttoolkit.inspect;

/**
 * Decides whether an observation matched what a bundle declared.
 *
 * <p>Pure on purpose. The subcommand gathers the observation with the same reads the other
 * inspections use; this class only compares, so the three rules that make a verdict honest
 * can be checked without a world:
 *
 * <ul>
 *   <li>A verdict always travels with the observed value. A reader can check the toolkit's
 *       comparison rather than trust it.</li>
 *   <li>Fail means "not the declared value". It never means the mod under test is wrong;
 *       deciding that stays with the reader.</li>
 *   <li>Absence is never inferred from an unloaded region. Not found in an unloaded chunk is
 *       {@link Outcome#UNLOADED}, which is neither pass nor fail.</li>
 * </ul>
 */
public final class Assertions {

    /** Failure key for a comparison that did not match. Tolerable by declaration. */
    public static final String KEY_FAILED = "devtool.assert.failed";
    /** Failure key for a comparison that could not be made because the region is not loaded. */
    public static final String KEY_UNLOADED = "devtool.assert.unloaded";
    /** Failure key for a modifier name that matches several modifiers. Never resolved by picking one. */
    public static final String KEY_AMBIGUOUS = "devtool.assert.ambiguous";

    public enum Outcome {
        PASS, FAIL, UNLOADED;

        public String recordName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** One comparison: what was expected, what was seen, and what that means. */
    public static final class Result {
        private final Outcome outcome;
        private final String expected;
        private final String observed;
        private final String note;
        private final boolean ambiguous;

        Result(Outcome outcome, String expected, String observed, String note) {
            this(outcome, expected, observed, note, false);
        }

        Result(Outcome outcome, String expected, String observed, String note, boolean ambiguous) {
            this.outcome = outcome;
            this.expected = expected;
            this.observed = observed;
            this.note = note;
            this.ambiguous = ambiguous;
        }

        public Outcome getOutcome() {
            return outcome;
        }

        public String getExpected() {
            return expected;
        }

        public String getObserved() {
            return observed;
        }

        /** Why the outcome is what it is when that is not obvious, or null. */
        public String getNote() {
            return note;
        }

        public boolean passed() {
            return outcome == Outcome.PASS;
        }

        /** The failure key for this outcome, or null for a pass. */
        public String failureKey() {
            switch (outcome) {
                case FAIL:
                    return ambiguous ? KEY_AMBIGUOUS : KEY_FAILED;
                case UNLOADED:
                    return KEY_UNLOADED;
                default:
                    return null;
            }
        }
    }

    private Assertions() {
    }

    /** An exact count. */
    public static Result count(long expected, long observed) {
        return new Result(expected == observed ? Outcome.PASS : Outcome.FAIL,
                Long.toString(expected), Long.toString(observed), null);
    }

    /**
     * Presence or absence of one entity.
     *
     * @param expectPresent what the bundle declared
     * @param found         whether the entity was among loaded entities
     * @param regionLoaded  whether the place it should be is loaded; null when nobody knows
     *                      where that place is
     */
    public static Result presence(boolean expectPresent, boolean found, Boolean regionLoaded) {
        String expected = expectPresent ? "present" : "absent";
        if (found) {
            return new Result(expectPresent ? Outcome.PASS : Outcome.FAIL, expected, "present", null);
        }
        // Not found. Absent is only a fact when the place it would be is loaded; otherwise
        // the honest answer is that nobody looked, and that is neither pass nor fail.
        if (regionLoaded == null) {
            return new Result(Outcome.UNLOADED, expected, "notFound",
                    "no position to check; the entity is not loaded, or is absent");
        }
        if (!regionLoaded) {
            return new Result(Outcome.UNLOADED, expected, "notFound", "region not loaded");
        }
        return new Result(expectPresent ? Outcome.FAIL : Outcome.PASS, expected, "absent", null);
    }

    /** An exact text match, case-sensitive, for dimensions, game modes, types and names. */
    public static Result equalsText(String expected, String observed) {
        boolean same = expected != null && expected.equals(observed);
        return new Result(same ? Outcome.PASS : Outcome.FAIL, expected,
                observed == null ? "none" : observed, null);
    }

    /** A distance no greater than a radius. Both rendered with two decimals for the record. */
    public static Result withinRadius(double radius, double distance) {
        boolean inside = distance <= radius;
        return new Result(inside ? Outcome.PASS : Outcome.FAIL,
                "within " + format(radius), format(distance), null);
    }

    /**
     * An attribute's final value. Equal when the two agree to the two decimals the records
     * show, so a value read from the log always matches the verdict.
     *
     * @param observed null when the entity has no attribute by that name
     */
    public static Result attributeValue(double expected, Double observed) {
        if (observed == null) {
            return new Result(Outcome.FAIL, format(expected), "absent", "no such attribute");
        }
        boolean same = format(expected).equals(format(observed));
        return new Result(same ? Outcome.PASS : Outcome.FAIL, format(expected), format(observed), null);
    }

    /**
     * One modifier, named by UUID or by name, optionally with its amount.
     *
     * <p>A UUID names at most one modifier. A name may name several: vanilla gives all four
     * armor pieces the name {@code Armor modifier}. Several matches fail as ambiguous with
     * {@link #KEY_AMBIGUOUS}; choosing one would make the verdict depend on iteration order.
     *
     * @param modifiers null when the entity has no attribute by that name
     * @param amount    null to check presence only
     */
    public static Result modifier(String reference, Double amount, java.util.List<Attributes.Modifier> modifiers) {
        String expected = amount == null ? "present" : "amount " + format(amount);
        if (modifiers == null) {
            return new Result(Outcome.FAIL, expected, "absent", "no such attribute");
        }
        boolean byUuid = Attributes.looksLikeUuid(reference);
        java.util.List<Attributes.Modifier> matches = new java.util.ArrayList<Attributes.Modifier>();
        for (Attributes.Modifier modifier : modifiers) {
            if (byUuid ? modifier.getUuid().equalsIgnoreCase(reference) : reference.equals(modifier.getName())) {
                matches.add(modifier);
            }
        }
        if (matches.isEmpty()) {
            return new Result(Outcome.FAIL, expected, "absent", null);
        }
        if (matches.size() > 1) {
            return new Result(Outcome.FAIL, expected, matches.size() + " matches",
                    "name matches several modifiers; assert by UUID", true);
        }
        Attributes.Modifier match = matches.get(0);
        String observed = "amount " + format(match.getAmount());
        if (amount == null) {
            return new Result(Outcome.PASS, expected, observed, null);
        }
        boolean same = format(amount).equals(format(match.getAmount()));
        return new Result(same ? Outcome.PASS : Outcome.FAIL, expected, observed, null);
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
