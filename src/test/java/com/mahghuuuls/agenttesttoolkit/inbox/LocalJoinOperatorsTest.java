package com.mahghuuuls.agenttesttoolkit.inbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The toolkit's own list of the operator rights it gave: written and read back, foreign entries skipped. */
class LocalJoinOperatorsTest {

    @Test
    @DisplayName("the list survives a write and a read, in order")
    void roundTrip() {
        Map<UUID, String> granted = new LinkedHashMap<UUID, String>();
        granted.put(UUID.fromString("a01a21f5-c117-3c1f-b5d0-e055b927f733"), "Developer");
        granted.put(UUID.fromString("11111111-1111-1111-1111-111111111111"), "Other");
        assertEquals(granted, LocalJoinOperators.parse(LocalJoinOperators.format(granted)));
        assertEquals("[]", LocalJoinOperators.format(new LinkedHashMap<UUID, String>()));
    }

    @Test
    @DisplayName("a damaged list is never read as empty, so it is not overwritten and no rights are given")
    void damagedList() {
        for (String damaged : new String[] {"{\"uuid\":\"x\"}", "[{\"uuid\":\"not-a-uuid\",\"name\":\"x\"}]",
                "[{\"name\":\"noUuid\"}]", "[1]", "not json at all ["}) {
            assertThrows(RuntimeException.class, () -> LocalJoinOperators.parse(damaged), damaged);
        }
        assertTrue(LocalJoinOperators.parse("[]").isEmpty());
    }
}
