package com.mahghuuuls.agenttesttoolkit.logging.filter;

/**
 * Admits events within a radius of a fixed point.
 *
 * <p>The radius is anchored to the player who applied the filter, captured once rather than
 * following them. Following the player would mean the filtered region moved during the test,
 * so an event could be recorded or not depending on where the operator happened to be standing
 * when it fired. A fixed anchor is reproducible; a moving one is not.
 *
 * <p>The containment rule matches {@code inspect.RadiusFilter}: spherical, inclusive at the
 * boundary, compared as squared distances. The arithmetic is deliberately restated here rather
 * than shared, because {@code logging} is the package everything else depends on and pointing it
 * at {@code inspect} would invert that. Both are unit tested against the same boundary cases.
 */
public final class RadiusFilter implements Filter {

    private final int dimension;
    private final double x;
    private final double y;
    private final double z;
    private final double radius;

    public RadiusFilter(int dimension, double x, double y, double z, double radius) {
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
    }

    @Override
    public boolean admits(int eventDimension, double eventX, double eventY, double eventZ) {
        if (eventDimension != dimension) {
            return false;
        }
        if (radius < 0) {
            return false;
        }
        double dx = eventX - x;
        double dy = eventY - y;
        double dz = eventZ - z;
        return (dx * dx + dy * dy + dz * dz) <= (radius * radius);
    }

    @Override
    public String describe() {
        // The anchor is included, not just the radius. "radius=20" alone would leave an agent
        // unable to tell whether an excluded event was outside the region or in the wrong
        // dimension entirely.
        return "radius=" + trim(radius) + " at " + trim(x) + "," + trim(y) + "," + trim(z)
                + " dim=" + dimension;
    }

    private static String trim(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
