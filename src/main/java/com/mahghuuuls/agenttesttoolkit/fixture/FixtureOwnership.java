package com.mahghuuuls.agenttesttoolkit.fixture;

import java.util.Collection;

/**
 * Decides what {@code fixture claim} may do with one entity, from the tag alone.
 *
 * <p>Before 1.4.0 claim trusted the tag on the entity while clear trusted the in-memory
 * registry, which a session stop empties. An entity tagged by an earlier session of the same
 * name could then be neither claimed nor cleared. Here the tag decides, and the registry is
 * only consulted as the cache of what this session already holds.
 *
 * <p>Pure: the caller looks at the world and hands over plain facts, so every case is a unit
 * test (rule 16).
 */
public final class FixtureOwnership {

    /** What the cache knows about the requested handle, as the caller found it in the world. */
    public enum Cached {
        /** No cache entry for the handle. */
        NONE,
        /** The cached entity is loaded and carries the handle's tag. */
        CONFIRMED,
        /** The cached entity is not loaded, so its tag cannot be checked. */
        UNLOADED,
        /** The cached entity is loaded without the tag, or is gone from a loaded place. */
        STALE
    }

    public enum Kind {
        /** Add the tag and cache the handle. */
        CLAIM,
        /** The entity already carries this handle's tag; cache it without changing the entity. */
        ADOPT,
        INVALID_HANDLE,
        /** The entity already carries another handle of this session; {@link Decision#detail} names it. */
        OTHER_HANDLE,
        /** The handle already belongs to another entity; {@link Decision#detail} is its UUID. */
        HANDLE_TAKEN,
        /** The handle is already claimed for this very entity. */
        ALREADY_CLAIMED
    }

    public static final class Decision {
        public final Kind kind;
        public final String detail;

        private Decision(Kind kind, String detail) {
            this.kind = kind;
            this.detail = detail;
        }
    }

    private FixtureOwnership() {
    }

    /**
     * What the cache entry for a handle is worth, from facts about that entry alone, read
     * before anything refills the cache.
     *
     * @param found the cached UUID names a loaded entity
     * @param tagged that entity carries the handle's tag
     * @param dead that entity is dead and about to leave the world
     * @param placeKnown a last place is recorded for the cached UUID
     * @param placeLoaded that last place is in a loaded chunk
     */
    public static Cached cachedFrom(boolean found, boolean tagged, boolean dead,
                                    boolean placeKnown, boolean placeLoaded) {
        if (found) {
            // A dead fixture is on its way out; its handle is free for the next claim.
            return tagged && !dead ? Cached.CONFIRMED : Cached.STALE;
        }
        // Not loaded: kept, because its tag cannot be checked. No recorded place counts as
        // unloaded too; every path that fills the cache also records a place.
        return placeKnown && placeLoaded ? Cached.STALE : Cached.UNLOADED;
    }

    /**
     * @param sessionName the active session
     * @param handle the requested handle
     * @param entityUuid the entity to claim
     * @param entityTags that entity's tags
     * @param cached what the cache holds for the handle
     * @param cachedUuid the cached entity's UUID, when {@code cached} is not NONE
     * @param otherTagged the UUID of another loaded entity carrying the handle's tag, or null
     */
    public static Decision decide(String sessionName, String handle, String entityUuid,
                                  Collection<String> entityTags, Cached cached, String cachedUuid,
                                  String otherTagged) {
        if (!FixtureTags.isHandleSafe(handle)) {
            return new Decision(Kind.INVALID_HANDLE, handle);
        }
        String wanted = FixtureTags.tag(sessionName, handle);
        boolean carriesWanted = false;
        for (String tag : entityTags) {
            String existing = FixtureTags.handleOf(tag, sessionName);
            if (existing == null) {
                continue;
            }
            if (existing.equals(handle)) {
                carriesWanted = true;
            } else {
                // One entity, one handle. Two handles on one entity would let a clear of one
                // leave the other reporting an absence it caused itself.
                return new Decision(Kind.OTHER_HANDLE, existing);
            }
        }
        boolean cachedIsThis = entityUuid.equals(cachedUuid);
        if ((cached == Cached.CONFIRMED || cached == Cached.UNLOADED) && !cachedIsThis) {
            // An unloaded fixture keeps its handle: its tag cannot be checked, so it is not
            // given away.
            return new Decision(Kind.HANDLE_TAKEN, cachedUuid);
        }
        if (cached == Cached.CONFIRMED) {
            return new Decision(Kind.ALREADY_CLAIMED, entityUuid);
        }
        if (otherTagged != null && !otherTagged.equals(entityUuid)) {
            return new Decision(Kind.HANDLE_TAKEN, otherTagged);
        }
        return new Decision(carriesWanted ? Kind.ADOPT : Kind.CLAIM, wanted);
    }
}
