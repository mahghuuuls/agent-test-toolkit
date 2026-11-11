package com.mahghuuuls.agenttesttoolkit.inspect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The words of find block: a state holds '=', coordinates never do, limit is last. */
class FindArgsTest {

    private static FindArgs parse(String line) {
        return FindArgs.parse(line.split(" "));
    }

    @Test
    @DisplayName("the four forms: with or without a state, with or without a limit")
    void forms() {
        FindArgs plain = parse("block minecraft:stone 0 0 0 10 10 10");
        assertEquals("minecraft:stone", plain.block);
        assertNull(plain.state);
        assertEquals(FindArgs.DEFAULT_LIMIT, plain.limit);
        assertEquals("10", plain.coordinates[5]);

        FindArgs state = parse("block minecraft:log axis=y 0 0 0 10 10 10 limit 5");
        assertEquals("axis=y", state.state);
        assertEquals(5, state.limit);
        assertEquals("0", state.coordinates[0]);

        assertEquals("~-4", parse("block minecraft:stone ~-4 ~ ~-4 ~4 ~4 ~4").coordinates[0]);
    }

    @Test
    @DisplayName("a seventh coordinate, a state without '=', or a bad limit is a usage error")
    void refusals() {
        assertNull(parse("block minecraft:stone 1 0 0 0 10 10 10"), "seven coordinates read as no state");
        assertNull(parse("block minecraft:stone 0 0 0 10 10"));
        assertNull(parse("block minecraft:stone 0 0 0 10 10 10 limit 0"));
        assertNull(parse("block minecraft:stone 0 0 0 10 10 10 limit 1025"));
        assertNull(parse("block minecraft:stone 0 0 0 10 10 10 limit x"));
        assertNull(parse("blocks minecraft:stone 0 0 0 10 10 10"));
    }
}
