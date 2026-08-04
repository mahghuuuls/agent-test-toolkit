package com.mahghuuuls.agenttesttoolkit.inspect;

/**
 * Decides whether a point lies within a radius of another.
 *
 * <p>Separated from the entity listing so the containment rule is checkable without a world.
 * The rule looks trivial and has two traps that a manual test would not reliably catch: whether
 * an entity exactly on the boundary is included, and whether the comparison is spherical or a
 * cube. Both are invisible by eye and would show up as an entity that "should have been listed".
 *
 * <p>Spherical, and inclusive at the boundary. Spherical because a radius that means a box
 * would surprise anyone reading the command, and inclusive because an entity standing exactly
 * at the stated distance is within it in ordinary language.
 */
public final class RadiusFilter {

    private RadiusFilter() {
    }

    /**
     * Compared as squared distances, which avoids a square root per entity and, more usefully
     * here, avoids the rounding a square root introduces exactly at the boundary.
     */
    public static boolean within(double originX, double originY, double originZ,
                                 double x, double y, double z, double radius) {
        if (radius < 0) {
            return false;
        }
        double dx = x - originX;
        double dy = y - originY;
        double dz = z - originZ;
        return (dx * dx + dy * dy + dz * dz) <= (radius * radius);
    }
}
