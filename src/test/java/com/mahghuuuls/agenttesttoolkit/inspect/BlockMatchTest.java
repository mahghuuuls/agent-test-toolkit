package com.mahghuuuls.agenttesttoolkit.inspect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The state a block search looks for: parsed, checked against the block, matched. */
class BlockMatchTest {

    @Test
    @DisplayName("name=value pairs, comma separated; anything else is not a state")
    void parse() {
        Map<String, String> wanted = BlockMatch.parseState("facing=north,half=top");
        assertEquals("north", wanted.get("facing"));
        assertEquals("top", wanted.get("half"));
        for (String bad : new String[] {"", "facing", "=north", "facing=", "a=b=c", "a=b,,c=d", "a=b,a=c", null}) {
            assertNull(BlockMatch.parseState(bad), String.valueOf(bad));
        }
    }

    @Test
    @DisplayName("a property the block lacks, or a value it cannot take, is named")
    void impossible() {
        Map<String, List<String>> allowed = new HashMap<String, List<String>>();
        allowed.put("facing", Arrays.asList("north", "south", "east", "west"));
        assertNull(BlockMatch.firstImpossible(allowed, BlockMatch.parseState("facing=north")));
        assertEquals("facing=up", BlockMatch.firstImpossible(allowed, BlockMatch.parseState("facing=up")));
        assertEquals("half=top", BlockMatch.firstImpossible(allowed, BlockMatch.parseState("half=top")));
    }

    @Test
    @DisplayName("a state stores a property only when another value would change its metadata")
    void storedAt() {
        // A door's lower half: facing north, east, south, west give metadata 0 to 3.
        assertTrue(BlockMatch.isStoredAt(3, new int[] {0, 1, 2, 3}));
        // Its upper half keeps the hinge and power instead: every facing gives the same 8.
        assertFalse(BlockMatch.isStoredAt(8, new int[] {8, 8, 8, 8}));
    }

    @Test
    @DisplayName("a state matches when it has every wanted value; the others do not matter")
    void matches() {
        Map<String, String> actual = new HashMap<String, String>();
        actual.put("facing", "north");
        actual.put("half", "top");
        assertTrue(BlockMatch.matches(BlockMatch.parseState("facing=north"), actual));
        assertFalse(BlockMatch.matches(BlockMatch.parseState("facing=south"), actual));
        assertTrue(BlockMatch.matches(new HashMap<String, String>(), actual), "no state: any");
    }
}
