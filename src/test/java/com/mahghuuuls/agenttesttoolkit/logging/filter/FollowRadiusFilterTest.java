package com.mahghuuuls.agenttesttoolkit.logging.filter;

import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The following radius filter with a player who moves, changes dimension, leaves and comes back:
 * the centre is where the player is at each event, and an absence admits nothing and is said
 * once.
 */
class FollowRadiusFilterTest {

    private static final UUID DEV = UUID.fromString("11111111-2222-3333-4444-555555555555");

    /** A player list of one, moved by the test, and the records the filter wrote. */
    private static final class OnePlayer implements FollowRadiusFilter.PlayerPlaces {
        FollowRadiusFilter.Place place;
        final List<LogRecord> written = new ArrayList<LogRecord>();

        @Override
        public FollowRadiusFilter.Place placeOf(UUID player) {
            return DEV.equals(player) ? place : null;
        }

        @Override
        public RecordContext.Snapshot contextOf(int dimension) {
            return new RecordContext.Snapshot(RecordContext.SERVER, 1000L + dimension);
        }

        @Override
        public void write(LogRecord record) {
            written.add(record);
        }
    }

    @Test
    @DisplayName("the centre moves with the player: after a 3000-block teleport, events by the player count and events at spawn do not")
    void followsTheMovingPlayer() {
        OnePlayer players = new OnePlayer();
        players.place = new FollowRadiusFilter.Place(0, 0, 64, 0);
        Filter filter = new FollowRadiusFilter("entity_damage", DEV, "Dev", 16, players);
        assertTrue(filter.admits(0, 5, 64, 5), "near the player at spawn");
        assertFalse(filter.admits(0, 3002, 64, 0), "far from the player at spawn");

        players.place = new FollowRadiusFilter.Place(0, 3000, 64, 0);
        assertTrue(filter.admits(0, 3002, 64, 0), "next to the player after the teleport");
        assertFalse(filter.admits(0, 5, 64, 5), "the spawn, where the player no longer is");

        players.place = new FollowRadiusFilter.Place(-1, 3000, 64, 0);
        assertFalse(filter.admits(0, 3002, 64, 0), "the player's old dimension");
        assertTrue(filter.admits(-1, 3002, 64, 0), "the player's current dimension");
        assertTrue(filter.admits(-1, 3016, 64, 0), "the boundary is inclusive, as the fixed radius's");
        assertTrue(players.written.isEmpty(), "no record while the player is online");
    }

    @Test
    @DisplayName("a player not online admits nothing, with one LOGGING filter=paused reason=playerGone per absence")
    void missingPlayerPauses() {
        OnePlayer players = new OnePlayer();
        Filter filter = new FollowRadiusFilter("entity_damage", DEV, "Dev", 16, players);
        assertFalse(filter.admits(0, 0, 64, 0), "gone: nothing admitted");
        assertFalse(filter.admits(0, 0, 64, 0));
        assertEquals(1, players.written.size(), "said once for the absence, not per event");
        String line = players.written.get(0).render();
        assertTrue(line.startsWith("[DevToolkit][LOGGING] side=SERVER worldTick=1000 "), line
                + ": led by the context of the event's world, as every record is");
        for (String field : new String[] {"filter=paused", "reason=playerGone", "category=entity_damage",
                "player=Dev", "playerUuid=" + DEV}) {
            assertTrue(line.contains(field), line + " has " + field);
        }
        assertTrue(filter.describe().contains("PLAYER GONE"), filter.describe());

        players.place = new FollowRadiusFilter.Place(0, 0, 64, 0);
        assertTrue(filter.admits(0, 0, 64, 0), "back: admitted again");
        assertEquals(1, players.written.size());
        players.place = null;
        assertFalse(filter.admits(0, 0, 64, 0));
        assertEquals(2, players.written.size(), "a second absence is said again");
    }

    @Test
    @DisplayName("status names the filter as radius=<n> follow player=<name>")
    void describesItself() {
        OnePlayer players = new OnePlayer();
        players.place = new FollowRadiusFilter.Place(0, 0, 64, 0);
        assertEquals("radius=16.0 follow player=Dev",
                new FollowRadiusFilter("entity_damage", DEV, "Dev", 16, players).describe());
    }
}
