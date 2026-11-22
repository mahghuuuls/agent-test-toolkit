package com.mahghuuuls.agenttesttoolkit.inspect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The NBT path grammar: keys, quoted keys, list indexes, and the mistakes it refuses. */
class NbtPathTest {

    /** Each step as text: a key as itself, an index as {@code [n]}. */
    private static List<String> steps(String path) {
        List<String> out = new ArrayList<String>();
        for (NbtPath.Segment segment : NbtPath.parse(path).getSegments()) {
            out.add(segment.isIndex() ? "[" + segment.getIndex() + "]" : segment.getKey());
        }
        return out;
    }

    private static void assertRefused(String path, String messagePart) {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> NbtPath.parse(path),
                path);
        assertTrue(refused.getMessage().contains(messagePart), path + ": " + refused.getMessage());
    }

    @Test
    @DisplayName("keys are split at dots, and an index follows a key or another index")
    void keysAndIndexes() {
        assertEquals(Arrays.asList("Damage"), steps("Damage"));
        assertEquals(Arrays.asList("tag", "ench", "[0]", "lvl"), steps("tag.ench[0].lvl"));
        assertEquals(Arrays.asList("Inventory", "[12]", "id"), steps("Inventory[12].id"));
        assertEquals(Arrays.asList("a", "[0]", "[3]"), steps("a[0][3]"));
    }

    @Test
    @DisplayName("a quoted key may hold a dot, a bracket, a space, and escaped quotes and backslashes")
    void quotedKeys() {
        assertEquals(Arrays.asList("ForgeCaps", "jawms:player_mana", "currentMana"),
                steps("ForgeCaps.\"jawms:player_mana\".currentMana"));
        assertEquals(Arrays.asList("a.b", "c"), steps("\"a.b\".c"), "a dot inside quotes is not a separator");
        assertEquals(Arrays.asList("x[1]"), steps("\"x[1]\""), "brackets inside quotes are not an index");
        assertEquals(Arrays.asList("Display", "my name"), steps("Display.\"my name\""));
        assertEquals(Arrays.asList("say \"hi\"", "c\\d"), steps("\"say \\\"hi\\\"\".\"c\\\\d\""));
        assertEquals(Arrays.asList("list", "[2]"), steps("\"list\"[2]"), "an index after a quoted key");
    }

    @Test
    @DisplayName("a key with a colon needs no quotes, and reads the same as the quoted form")
    void colonUnquoted() {
        assertEquals(steps("ForgeCaps.\"jawms:player_mana\".currentMana"),
                steps("ForgeCaps.jawms:player_mana.currentMana"));
    }

    @Test
    @DisplayName("an empty path, a stray dot, a leading index, a bad index, an open quote or a bare space is refused")
    void refusals() {
        assertRefused("", "empty");
        assertRefused("a.", "ends with a dot");
        assertRefused("a..b", "expected a key");
        assertRefused(".a", "expected a key");
        assertRefused("[0].a", "expected a key at character 1");
        assertRefused("a[-1]", "not an index");
        assertRefused("a[x]", "not an index");
        assertRefused("a[]", "not an index");
        assertRefused("a[1234567890]", "not an index");
        assertRefused("a[0", "never closed");
        assertRefused("\"a.b", "never closed");
        assertRefused("my name", "double quotes");
        assertRefused("a]b", "expected '.' or '['");
        assertRefused("\"a\"b", "expected '.' or '['");
    }

    @Test
    @DisplayName("a path ends at the first space outside quotes, so a value can follow it")
    void endOfPath() {
        assertEquals(6, NbtPath.end("Damage 5"));
        assertEquals(15, NbtPath.end("Display.\"a b\".c minecraft:stone"));
        assertEquals(5, NbtPath.end("\"a\\\"\" x"),"an escaped quote does not close the quotes");
        assertEquals(3, NbtPath.end("abc"));
    }

    @Test
    @DisplayName("a prefix of the path is shown exactly as it was typed")
    void prefixAsTyped() {
        NbtPath path = NbtPath.parse("ForgeCaps.\"jawms:player_mana\"[0].currentMana");
        assertEquals("ForgeCaps", path.prefix(1));
        assertEquals("ForgeCaps.\"jawms:player_mana\"", path.prefix(2));
        assertEquals("ForgeCaps.\"jawms:player_mana\"[0]", path.prefix(3));
        assertEquals(path.toString(), path.prefix(4));
    }
}
