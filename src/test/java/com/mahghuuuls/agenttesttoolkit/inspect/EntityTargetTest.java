package com.mahghuuuls.agenttesttoolkit.inspect;

import com.mahghuuuls.agenttesttoolkit.command.sub.InspectSubCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cardinality rule, and relative-coordinate detection.
 *
 * <p>These were missing at first, because the decision was tangled with Minecraft-typed
 * matching. Independent review flagged both the gap and that the decision was separable. It
 * now is, and these cover it: matching still needs a live selector engine, but deciding what
 * a match count means does not.
 */
class EntityTargetTest {

    @Test
    @DisplayName("zero matches is an error naming the count")
    void zeroMatchesIsAnError() {
        EntityTarget.CardinalityException problem = EntityTarget.checkCardinality(0);
        assertNotNull(problem);
        assertEquals(0, problem.getMatched());
        assertTrue(problem.getMessage().contains("no entities"), problem.getMessage());
    }

    @Test
    @DisplayName("exactly one match is accepted")
    void oneMatchIsAccepted() {
        assertNull(EntityTarget.checkCardinality(1));
    }

    @Test
    @DisplayName("more than one match is an error stating how many")
    void manyMatchesIsAnError() {
        // The count matters. "matched several" would leave an agent unable to tell whether
        // its selector was slightly wrong or wildly wrong.
        EntityTarget.CardinalityException two = EntityTarget.checkCardinality(2);
        assertNotNull(two);
        assertEquals(2, two.getMatched());
        assertTrue(two.getMessage().contains("2 entities"), two.getMessage());
        assertTrue(two.getMessage().contains("expected exactly 1"), two.getMessage());

        EntityTarget.CardinalityException many = EntityTarget.checkCardinality(17);
        assertNotNull(many);
        assertEquals(17, many.getMatched());
        assertTrue(many.getMessage().contains("17 entities"), many.getMessage());
    }

    @Test
    @DisplayName("zero and many are distinguishable errors, not one generic failure")
    void zeroAndManyAreDistinguishable() {
        // An agent must be able to tell "my fixture never spawned" from "my selector is too
        // loose", because the two have opposite fixes.
        String zero = EntityTarget.checkCardinality(0).getMessage();
        String many = EntityTarget.checkCardinality(3).getMessage();
        assertFalse(zero.equals(many));
    }

    @Test
    @DisplayName("relative coordinates are detected on any axis")
    void detectsRelativeCoordinates() {
        assertTrue(InspectSubCommand.isRelative(new String[]{"~", "64", "7"}));
        assertTrue(InspectSubCommand.isRelative(new String[]{"-21", "~-1", "7"}));
        assertTrue(InspectSubCommand.isRelative(new String[]{"-21", "64", "~2"}));
        assertTrue(InspectSubCommand.isRelative(new String[]{"~", "~", "~"}));
    }

    @Test
    @DisplayName("fully absolute coordinates are not treated as relative")
    void absoluteCoordinatesAreNotRelative() {
        assertFalse(InspectSubCommand.isRelative(new String[]{"0", "100", "0"}));
        assertFalse(InspectSubCommand.isRelative(new String[]{"-21", "64", "7"}));
    }

    @Test
    @DisplayName("relative detection tolerates a short argument array")
    void toleratesShortArguments() {
        assertFalse(InspectSubCommand.isRelative(new String[0]));
        assertTrue(InspectSubCommand.isRelative(new String[]{"~"}));
    }
}
