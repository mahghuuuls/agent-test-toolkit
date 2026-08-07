package com.mahghuuuls.agenttesttoolkit.observe;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two rules that make the spawn category usable.
 *
 * <p>Both are invisible from inside the game in opposite ways. If the chunk discriminator is
 * wrong, the log fills with hundreds of records the moment someone walks into fresh terrain, and
 * it looks like the feature working until that happens. If the type exclusion is wrong, one mob
 * death buries the spawn under a burst of drops.
 */
class SpawnClassificationTest {

    // --- chunk-load discriminator ----------------------------------------------------

    @Test
    @DisplayName("an entity not yet in a chunk is a new spawn")
    void notInChunkIsSpawn() {
        // World#spawnEntity posts the event before Chunk#addEntity sets the flag, so a genuine
        // spawn reaches the handler with it still false.
        assertTrue(SpawnClassification.isNewSpawn(false));
    }

    @Test
    @DisplayName("an entity already in a chunk arrived with the chunk, not by spawning")
    void alreadyInChunkIsNotSpawn() {
        // World#loadEntities iterates entities the chunk added while reading NBT, so they
        // reach the event with the flag set. This is the category's largest noise source.
        assertFalse(SpawnClassification.isNewSpawn(true));
    }

    // --- type exclusion --------------------------------------------------------------

    @Test
    @DisplayName("items and experience orbs are excluded by default")
    void itemsAndOrbsExcludedByDefault() {
        assertFalse(SpawnClassification.shouldRecordType(true, false, false), "dropped item");
        assertFalse(SpawnClassification.shouldRecordType(false, true, false), "experience orb");
    }

    @Test
    @DisplayName("everything else is recorded by default")
    void othersRecordedByDefault() {
        // A projectile is the case that matters: it is neither an item nor
        // an orb, so it must survive the default exclusion.
        assertTrue(SpawnClassification.shouldRecordType(false, false, false));
    }

    @Test
    @DisplayName("the opt-in includes items and orbs")
    void optInIncludesThem() {
        assertTrue(SpawnClassification.shouldRecordType(true, false, true));
        assertTrue(SpawnClassification.shouldRecordType(false, true, true));
    }

    @Test
    @DisplayName("the opt-in does not exclude anything that was already included")
    void optInOnlyWidens() {
        // Guards against the exclusion being written as a whitelist by mistake, which would
        // make the opt-in silently narrow the category instead of widening it.
        assertTrue(SpawnClassification.shouldRecordType(false, false, true));
    }
}
