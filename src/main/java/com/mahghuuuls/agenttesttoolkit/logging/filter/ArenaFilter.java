package com.mahghuuuls.agenttesttoolkit.logging.filter;

/**
 * Admits events inside the arena of the dimension the filter was applied in.
 *
 * <p>REQ-045. Bounds are resolved through {@link ArenaBounds} at evaluation time rather than
 * captured when the filter was applied, so recreating the arena moves the filtered region with
 * it. See {@link ArenaBounds} for why that matters.
 *
 * <p>If the arena is removed after the filter is applied, this admits nothing. That is the safe
 * direction: recording everything would flood a log the operator had explicitly narrowed, and
 * {@code log status} reports the filter so the silence is explicable rather than mysterious.
 */
public final class ArenaFilter implements Filter {

    private final int dimension;
    private final ArenaBounds bounds;

    public ArenaFilter(int dimension, ArenaBounds bounds) {
        if (bounds == null) {
            throw new IllegalArgumentException("bounds source must not be null");
        }
        this.dimension = dimension;
        this.bounds = bounds;
    }

    @Override
    public boolean admits(int eventDimension, double x, double y, double z) {
        if (eventDimension != dimension) {
            return false;
        }
        ArenaBounds.Box box = bounds.boundsFor(dimension);
        return box != null && box.contains(x, y, z);
    }

    @Override
    public String describe() {
        ArenaBounds.Box box = bounds.boundsFor(dimension);
        if (box == null) {
            // Stated rather than hidden. A filter admitting nothing because its arena was
            // deleted is the single most confusing state this feature can be in, so status
            // says so outright.
            return "arena dim=" + dimension + " (NO ARENA, admitting nothing)";
        }
        return "arena dim=" + dimension + " " + box;
    }
}
