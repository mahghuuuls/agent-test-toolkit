package com.mahghuuuls.agenttesttoolkit.fixture;

import com.mahghuuuls.agenttesttoolkit.fixture.FixtureOwnership.Cached;
import com.mahghuuuls.agenttesttoolkit.fixture.FixtureOwnership.Decision;
import com.mahghuuuls.agenttesttoolkit.fixture.FixtureOwnership.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What {@code fixture claim} may do, decided by the tag alone (REQ-209, CYCLE-005 intake D1).
 */
class FixtureOwnershipTest {

    private static final String PIG = "11111111-1111-1111-1111-111111111111";
    private static final String COW = "22222222-2222-2222-2222-222222222222";

    private static List<String> tags(String... tags) {
        return Arrays.asList(tags);
    }

    private static Decision decide(String session, String handle, String uuid, List<String> tags,
                                   Cached cached, String cachedUuid, String other) {
        return FixtureOwnership.decide(session, handle, uuid, tags, cached, cachedUuid, other);
    }

    @Test
    @DisplayName("an untagged entity with a free handle is claimed under the handle's tag")
    void claim() {
        Decision d = decide("s", "a", PIG, Collections.<String>emptyList(), Cached.NONE, null, null);
        assertEquals(Kind.CLAIM, d.kind);
        assertEquals("devtool.fx.s.a", d.detail);
    }

    @Test
    @DisplayName("the intake reproduction: a tag left by an earlier session of the same name is adopted")
    void adoptAfterSessionStop() {
        // Session s claimed the pig as a, then stopped, emptying the cache. A new session s
        // claims it again under a: the tag decides, so it is taken back.
        Decision d = decide("s", "a", PIG, tags("devtool.fx.s.a"), Cached.NONE, null, null);
        assertEquals(Kind.ADOPT, d.kind);
    }

    @Test
    @DisplayName("a stale cache entry does not block adoption")
    void adoptOverStaleCache() {
        Decision d = decide("s", "a", PIG, tags("devtool.fx.s.a"), Cached.STALE, COW, null);
        assertEquals(Kind.ADOPT, d.kind);
    }

    @Test
    @DisplayName("an entity carrying another handle of this session is refused, naming that handle")
    void otherHandle() {
        Decision d = decide("s", "b", PIG, tags("devtool.fx.s.a"), Cached.NONE, null, null);
        assertEquals(Kind.OTHER_HANDLE, d.kind);
        assertEquals("a", d.detail);
    }

    @Test
    @DisplayName("a handle held by another loaded entity is refused, naming that entity")
    void handleOnOtherEntity() {
        Decision d = decide("s", "a", PIG, Collections.<String>emptyList(), Cached.NONE, null, COW);
        assertEquals(Kind.HANDLE_TAKEN, d.kind);
        assertEquals(COW, d.detail);
    }

    @Test
    @DisplayName("a confirmed or unloaded fixture keeps its handle")
    void handleTakenByCache() {
        assertEquals(Kind.HANDLE_TAKEN,
                decide("s", "a", PIG, Collections.<String>emptyList(), Cached.CONFIRMED, COW, null).kind);
        assertEquals(Kind.HANDLE_TAKEN,
                decide("s", "a", PIG, Collections.<String>emptyList(), Cached.UNLOADED, COW, null).kind);
    }

    @Test
    @DisplayName("claiming the same entity under its own handle twice is refused as a duplicate")
    void duplicate() {
        assertEquals(Kind.ALREADY_CLAIMED,
                decide("s", "a", PIG, tags("devtool.fx.s.a"), Cached.CONFIRMED, PIG, null).kind);
    }

    @Test
    @DisplayName("tags of another session, even one sharing a prefix, are ignored")
    void foreignSessions() {
        assertEquals(Kind.CLAIM,
                decide("a", "h", PIG, tags("devtool.fx.a_b.h", "devtool.fx.ab.h", "devtool.fx.a.b.h"),
                        Cached.NONE, null, null).kind);
    }

    @Test
    @DisplayName("a handle with a dot is refused")
    void dottedHandle() {
        assertEquals(Kind.INVALID_HANDLE,
                decide("s", "my.pig", PIG, Collections.<String>emptyList(), Cached.NONE, null, null).kind);
    }

    @Test
    @DisplayName("an entity carrying two handles of this session is refused, naming one of them")
    void twoOwnHandles() {
        Decision d = decide("s", "c", PIG, tags("devtool.fx.s.a", "devtool.fx.s.b"), Cached.NONE, null, null);
        assertEquals(Kind.OTHER_HANDLE, d.kind);
    }

    @Test
    @DisplayName("an unloaded cache entry for this very entity does not block claiming it")
    void unloadedEntryForThisEntity() {
        assertEquals(Kind.ADOPT,
                decide("s", "a", PIG, tags("devtool.fx.s.a"), Cached.UNLOADED, PIG, null).kind);
    }

    // --- The cache entry, read before any refill (review S1) -------------------------------

    @Test
    @DisplayName("a loaded, tagged, living cached entity confirms the handle")
    void cachedConfirmed() {
        assertEquals(Cached.CONFIRMED, FixtureOwnership.cachedFrom(true, true, false, true, true));
    }

    @Test
    @DisplayName("a cached entity that lost its tag, or is dying, frees the handle")
    void cachedStaleWhenUntaggedOrDead() {
        assertEquals(Cached.STALE, FixtureOwnership.cachedFrom(true, false, false, true, true));
        assertEquals(Cached.STALE, FixtureOwnership.cachedFrom(true, true, true, true, true));
    }

    @Test
    @DisplayName("a cached entity gone from a loaded place is stale; one in an unloaded place keeps the handle")
    void cachedByPlace() {
        assertEquals(Cached.STALE, FixtureOwnership.cachedFrom(false, false, false, true, true));
        assertEquals(Cached.UNLOADED, FixtureOwnership.cachedFrom(false, false, false, true, false));
        assertEquals(Cached.UNLOADED, FixtureOwnership.cachedFrom(false, false, false, false, false));
    }

    @Test
    @DisplayName("the review's S1 case: a stale cache entry and the tag on the claimed entity adopt it")
    void staleCacheThenAdopt() {
        // The cache says a = cow, the cow is gone from a loaded place, and the pig carries the
        // tag. The cache entry is read before any refill, so the decision sees STALE.
        Cached cached = FixtureOwnership.cachedFrom(false, false, false, true, true);
        assertEquals(Kind.ADOPT, decide("s", "a", PIG, tags("devtool.fx.s.a"), cached, COW, null).kind);
    }
}
