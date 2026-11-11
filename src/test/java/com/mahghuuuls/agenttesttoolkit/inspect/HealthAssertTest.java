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
}
