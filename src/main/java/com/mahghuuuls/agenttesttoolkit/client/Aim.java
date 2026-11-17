package com.mahghuuuls.agenttesttoolkit.client;

/**
 * The turns of the view, from plain numbers, so the direction to a point, the way round and the
 * steps of a slow turn are tested without a game.
 *
 * <p>Angles are the game's: yaw 0 looks south (+Z) and grows toward west (-X); pitch 90 looks
 * straight down.
 */
final class Aim {

    /** The mouse turns the view by this share of the amount it is given. */
    static final double MOUSE_SCALE = 0.15;

    private Aim() {
    }

    /**
     * The yaw and pitch that look from the eye to the point. A point straight above or below
     * the eye has no horizontal direction, so the current yaw is kept for it.
     */
    static double[] toward(double currentYaw, double eyeX, double eyeY, double eyeZ, double x, double y, double z) {
        double dx = x - eyeX;
        double dy = y - eyeY;
        double dz = z - eyeZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double yaw = horizontal < 1.0E-9 ? currentYaw : Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = horizontal < 1.0E-9 && Math.abs(dy) < 1.0E-9 ? 0.0 : Math.toDegrees(-Math.atan2(dy, horizontal));
        return new double[] {yaw, clampPitch(pitch)};
    }

    /**
     * The yaw change that reaches the target the short way round, from -180 (excluded) to 180;
     * the yaw the player ends with may differ from the target by whole turns.
     */
    static double yawChange(double fromYaw, double toYaw) {
        double change = (toYaw - fromYaw) % 360.0;
        if (change > 180.0) {
            change -= 360.0;
        } else if (change <= -180.0) {
            change += 360.0;
        }
        return change;
    }

    /** Pitch within the game's limits, straight up to straight down. */
    static double clampPitch(double pitch) {
        return Math.max(-90.0, Math.min(90.0, pitch));
    }

    /**
     * The change made in one tick of a turn spread evenly over {@code ticks} ticks; {@code step}
     * counts from 1. Each step is the difference of two running totals, so the steps add up to
     * the whole change exactly whatever the rounding.
     */
    static double step(double total, int ticks, int step) {
        if (ticks <= 1) {
            return step == 1 ? total : 0.0;
        }
        return total * step / ticks - total * (step - 1) / ticks;
    }

    /** What to give the mouse turn so the view moves by this many degrees of yaw. */
    static float mouseYaw(double degrees) {
        return (float) (degrees / MOUSE_SCALE);
    }

    /** What to give the mouse turn so the pitch moves by this many degrees; the mouse's pitch is reversed. */
    static float mousePitch(double degrees) {
        return (float) (-degrees / MOUSE_SCALE);
    }
}
