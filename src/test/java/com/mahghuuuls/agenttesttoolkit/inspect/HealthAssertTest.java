package com.mahghuuuls.agenttesttoolkit.inspect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The health assert compares at the two decimals the records show. */
class HealthAssertTest {

    @Test
    @DisplayName("equal at two decimals passes; a fractional health is compared as written")
    void twoDecimals() {
        assertEquals(Assertions.Outcome.PASS, Assertions.health(20, 20.0F).getOutcome());
        assertEquals(Assertions.Outcome.PASS, Assertions.health(16.5, 16.5F).getOutcome());
        assertEquals(Assertions.Outcome.FAIL, Assertions.health(20, 16.5F).getOutcome());
        assertEquals(Assertions.Outcome.PASS, Assertions.health(16.5, 16.499F).getOutcome(), "as the record shows it");
        assertEquals("16.50", Assertions.health(16.5, 16.5F).getObserved());
    }

    @Test
    @DisplayName("an entity's health: a cow at 10 passes 10; after 3 damage 7 passes and 10 fails with assert.failed")
    void entityHealth() {
        assertEquals(Assertions.Outcome.PASS, Assertions.health(10, 10.0F).getOutcome());
        assertEquals(Assertions.Outcome.PASS, Assertions.health(7, 7.0F).getOutcome());
        Assertions.Result stale = Assertions.health(10, 7.0F);
        assertEquals(Assertions.Outcome.FAIL, stale.getOutcome());
        assertEquals(Assertions.KEY_FAILED, stale.failureKey());
        assertEquals("10.00", stale.getExpected());
        assertEquals("7.00", stale.getObserved());
        assertEquals(Assertions.Outcome.PASS, Assertions.health(0, 0.0F).getOutcome(), "a dying entity's health is compared");
    }

    @Test
    @DisplayName("an entity in an unloaded place is neither pass nor fail: unloaded, with assert.unloaded")
    void unloadedEntity() {
        Assertions.Result unloaded = Assertions.healthUnloaded(10);
        assertEquals(Assertions.Outcome.UNLOADED, unloaded.getOutcome());
        assertEquals(Assertions.KEY_UNLOADED, unloaded.failureKey());
        assertEquals("10.00", unloaded.getExpected());
        assertEquals("notFound", unloaded.getObserved());
    }
}
