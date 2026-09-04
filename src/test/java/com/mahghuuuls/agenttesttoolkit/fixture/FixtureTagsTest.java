package com.mahghuuuls.agenttesttoolkit.fixture;

import com.mahghuuuls.agenttesttoolkit.state.ToolkitState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ownership tag, and the registry that indexes it.
 *
 * <p>Two rules carry the safety of cleanup: a tag is owned by exactly one session, and a
 * handle uses only characters a vanilla selector can name, so the game can be asked to
 * confirm the toolkit's own listing.
 */
class FixtureTagsTest {

    @BeforeEach
    @AfterEach
    void reset() {
        ToolkitState.resetForTesting();
    }

    @Test
    @DisplayName("the alphabet is letters, digits, underscore, dot and hyphen")
    void alphabet() {
        assertTrue(FixtureTags.isSelectorSafe("source"));
        assertTrue(FixtureTags.isSelectorSafe("pig_1.a-b"));
        assertFalse(FixtureTags.isSelectorSafe("two words"));
        assertFalse(FixtureTags.isSelectorSafe("colon:here"));
        assertFalse(FixtureTags.isSelectorSafe(""));
        assertFalse(FixtureTags.isSelectorSafe(null));
    }

    @Test
    @DisplayName("a tag names the session and the handle, with unsafe session characters replaced")
    void tagShape() {
        assertEquals("devtool.fx.imp002.source", FixtureTags.tag("imp002", "source"));
        assertEquals("devtool.fx.my_test.pig", FixtureTags.tag("my test", "pig"));
        assertEquals("my_test", FixtureTags.scope("my test"));
    }

    @Test
    @DisplayName("ownership is by session, and a forged tag from another session is not ours")
    void ownership() {
        assertTrue(FixtureTags.ownedBy("devtool.fx.s1.a", "s1"));
        assertFalse(FixtureTags.ownedBy("devtool.fx.s2.a", "s1"));
        assertFalse(FixtureTags.ownedBy("devtool.fx.s10.a", "s1"));
        assertFalse(FixtureTags.ownedBy("somethingelse", "s1"));
        assertFalse(FixtureTags.ownedBy(null, "s1"));
        assertEquals("a", FixtureTags.handleOf("devtool.fx.s1.a", "s1"));
        assertNull(FixtureTags.handleOf("devtool.fx.s2.a", "s1"));
    }

    @Test
    @DisplayName("the registry keeps handle, uuid and last place, and forgets on clear")
    void registry() {
        ToolkitState.putFixture("source", "0f01c58f-7f2e-4afd-860b-1e8dbd9e197b");
        ToolkitState.putFixturePlace("0f01c58f-7f2e-4afd-860b-1e8dbd9e197b", -1, 10, 64, -20);
        assertEquals("0f01c58f-7f2e-4afd-860b-1e8dbd9e197b", ToolkitState.getFixtureUuid("source"));
        ToolkitState.FixturePlace place =
                ToolkitState.getFixturePlace("0f01c58f-7f2e-4afd-860b-1e8dbd9e197b");
        assertEquals(-1, place.dimension);
        assertEquals(10, place.x);
        assertEquals(-20, place.z);

        ToolkitState.removeFixture("source");
        assertNull(ToolkitState.getFixtureUuid("source"));
        assertTrue(ToolkitState.getFixtures().isEmpty());

        ToolkitState.putFixture("a", "u1");
        ToolkitState.clearFixtures();
        assertTrue(ToolkitState.getFixtures().isEmpty());
        assertNull(ToolkitState.getFixturePlace("0f01c58f-7f2e-4afd-860b-1e8dbd9e197b"));
    }
}
