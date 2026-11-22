package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.logging.filter.FollowRadiusFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A following filter asks for the server running at each check, so it keeps working across a
 * single-player reconnect, which replaces the integrated server while the filter stays.
 */
class CurrentPlayerPlacesTest {

    private static final UUID DEV = UUID.fromString("11111111-2222-3333-4444-555555555555");

    /** A server's player list with the player at one place, or without the player. */
    private static CurrentPlayerPlaces.Players serverWithDevAt(final FollowRadiusFilter.Place place) {
        return new CurrentPlayerPlaces.Players() {
            @Override
            public FollowRadiusFilter.Place placeOf(UUID player) {
                return DEV.equals(player) ? place : null;
            }
        };
    }

    @Test
    @DisplayName("after the server is replaced, the filter reads the new server's player, never the stopped one's")
    void readsTheServerRunningNow() {
        final AtomicReference<CurrentPlayerPlaces.Players> running = new AtomicReference<CurrentPlayerPlaces.Players>(
                serverWithDevAt(new FollowRadiusFilter.Place(0, 0, 64, 0)));
        CurrentPlayerPlaces places = new CurrentPlayerPlaces(new CurrentPlayerPlaces.Running() {
            @Override
            public CurrentPlayerPlaces.Players players() {
                return running.get();
            }
        });
        FollowRadiusFilter filter = new FollowRadiusFilter("entity_damage", DEV, "Dev", 16, places);
        assertTrue(filter.admits(0, 2, 64, 0), "on the first server, next to the player");

        // The reconnect: the old server stops, then a new one runs with the player elsewhere.
        running.set(null);
        assertNull(places.placeOf(DEV), "between servers nobody is online");
        running.set(serverWithDevAt(new FollowRadiusFilter.Place(0, 3000, 64, 0)));
        assertTrue(filter.admits(0, 3002, 64, 0), "on the new server, next to the player");
        assertFalse(filter.admits(0, 2, 64, 0), "where the player stood on the stopped server");
    }
}
