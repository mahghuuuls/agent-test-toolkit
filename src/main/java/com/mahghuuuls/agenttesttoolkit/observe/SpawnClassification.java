package com.mahghuuuls.agenttesttoolkit.observe;

/**
 * Whether an entity joining a world is a genuine spawn, and whether it is worth recording.
 *
 * <p>Both decisions are separated from the Forge handler so they can be checked without a
 * running game. They are the whole substance of the category: the handler around them is a
 * gate, a stamp and a write.
 */
public final class SpawnClassification {

    private SpawnClassification() {
    }

    /**
     * Distinguishes a new spawn from an entity arriving because a chunk loaded.
     *
     * <p>Established by reading the 1.12.2 sources, not inferred. {@code World#spawnEntity}
     * posts {@code EntityJoinWorldEvent} at {@code World.java:1316} and only then calls
     * {@code getChunk(i, j).addEntity(entityIn)} at line 1318; {@code Chunk#addEntity} is what
     * sets {@code addedToChunk = true}, at {@code Chunk.java:788}. {@code World#loadEntities},
     * at {@code World.java:3404}, iterates entities that came <b>from</b> a chunk which already
     * added them while reading NBT, so they reach the event with the flag already set.
     *
     * <p>So at event time a new spawn reads false and a chunk-loaded entity reads true.
     *
     * <p>The candidates that look obvious both fail. {@code ticksExisted} is 0 for chunk-loaded
     * entities too, because it is not persisted to NBT. UUIDs are identical either way, since a
     * loaded entity restores the one it was saved with.
     *
     * <p>This is the category's largest noise risk. Walking into fresh terrain loads hundreds of
     * entities, and recording them would bury the spawn an operator actually asked about.
     *
     * @param addedToChunk the entity's {@code addedToChunk} flag, read at event time
     */
    public static boolean isNewSpawn(boolean addedToChunk) {
        return !addedToChunk;
    }

    /**
     * Whether an entity type is excluded by default.
     *
     * <p>REQ: items and experience orbs are excluded unless explicitly opted in, because one
     * mob death produces a burst of both and they are almost never the subject of a test. The
     * check is on the type only, so an excluded spawn costs a comparison rather than the string
     * work of building a record that is then discarded.
     *
     * <p>Takes booleans rather than an {@code Entity} so the rule stays testable. The handler
     * supplies them with {@code instanceof}.
     *
     * @param isItem      whether the entity is a dropped item
     * @param isExpOrb    whether the entity is an experience orb
     * @param includeAll  the opt-in, from configuration
     */
    public static boolean shouldRecordType(boolean isItem, boolean isExpOrb, boolean includeAll) {
        if (includeAll) {
            return true;
        }
        return !isItem && !isExpOrb;
    }
}
