package com.mahghuuuls.agenttesttoolkit.logging.filter;

import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;

import java.util.UUID;

/**
 * Admits events within a radius of a player wherever that player is now, in the player's
 * current dimension.
 *
 * <p>The opposite choice to {@link RadiusFilter}, and only on request ({@code follow}): a test
 * that teleports the player far away still records what happens around them, which a centre
 * fixed where the filter was applied cannot. The position is read at each event, as
 * {@link ArenaFilter} reads its arena, through {@link PlayerPlaces}, so this package needs no
 * world or player list.
 *
 * <p>While the player is not online nothing is admitted, and one {@code LOGGING} record with
 * {@code filter=paused reason=playerGone} says so at the first event that finds the player gone;
 * an empty stretch of log is then explicable rather than mysterious. The filter learns the player
 * is back only when an event of its category finds them online, so a later absence is said again
 * only after such an event; a player who leaves and returns between two events of the category
 * is one absence to it.
 */
public final class FollowRadiusFilter implements Filter {

    /** Where players are now, read at evaluation time. */
    public interface PlayerPlaces {

        /** The player's current place, or null while the player is not online. */
        Place placeOf(UUID player);

        /**
         * The context fields of the world of that dimension, so a record this filter makes leads
         * with {@code side} and {@code worldTick} as every other record does.
         */
        RecordContext.Snapshot contextOf(int dimension);

        /** Writes a record this filter made, with the session fields the rest of the log carries. */
        void write(LogRecord record);
    }

    /** A player's dimension and position. */
    public static final class Place {
        final int dimension;
        final double x;
        final double y;
        final double z;

        public Place(int dimension, double x, double y, double z) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private final String category;
    private final UUID player;
    private final String playerName;
    private final double radius;
    private final PlayerPlaces places;
    /** True from the first event that found the player gone until one finds them back. */
    private volatile boolean paused;

    public FollowRadiusFilter(String category, UUID player, String playerName, double radius, PlayerPlaces places) {
        if (player == null || places == null) {
            throw new IllegalArgumentException("player and places must not be null");
        }
        this.category = category;
        this.player = player;
        this.playerName = playerName;
        this.radius = radius;
        this.places = places;
    }

    @Override
    public boolean admits(int eventDimension, double eventX, double eventY, double eventZ) {
        Place place = places.placeOf(player);
        if (place == null) {
            if (!paused) {
                paused = true;
                places.write(RecordContext.stamp(LogRecord.of(EventType.LOGGING), places.contextOf(eventDimension))
                        .add("filter", "paused")
                        .add("reason", "playerGone")
                        .add("category", category)
                        .add("player", playerName)
                        .add("playerUuid", player.toString()));
            }
            return false;
        }
        paused = false;
        if (eventDimension != place.dimension || radius < 0) {
            return false;
        }
        double dx = eventX - place.x;
        double dy = eventY - place.y;
        double dz = eventZ - place.z;
        return (dx * dx + dy * dy + dz * dz) <= (radius * radius);
    }

    @Override
    public String describe() {
        String text = "radius=" + String.format(java.util.Locale.ROOT, "%.1f", radius) + " follow player=" + playerName;
        // Said outright, as an arena filter without its arena says it: a filter admitting
        // nothing because its player left is otherwise the most confusing state it can be in.
        return places.placeOf(player) == null ? text + " (PLAYER GONE, admitting nothing)" : text;
    }
}
