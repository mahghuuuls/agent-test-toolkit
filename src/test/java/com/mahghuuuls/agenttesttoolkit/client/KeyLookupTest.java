package com.mahghuuuls.agenttesttoolkit.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Finding a key binding by its name, and listing bindings with their shared names marked. */
class KeyLookupTest {

    private static KeyLookup.Entry entry(String name, int keyCode) {
        return new KeyLookup.Entry(name, "key.categories.movement", keyCode, "K" + keyCode, "NONE", "IN_GAME");
    }

    private static final List<KeyLookup.Entry> ENTRIES = Arrays.asList(
            entry("key.forward", 17), entry("key.jump", 57), entry("key.mod.skill", 19),
            entry("key.mod.skill", 20), entry("key.mod.unset", 0), entry("waila.keybind.wailaconfig", 82));

    @Test
    @DisplayName("one exact name finds its binding; none, several, or one without a key are refused")
    void find() {
        KeyLookup.Found jump = KeyLookup.find(ENTRIES, "key.jump");
        assertNull(jump.refusal);
        assertEquals(1, jump.index);
        assertEquals(5, KeyLookup.find(ENTRIES, "waila.keybind.wailaconfig").index);
        assertEquals("unknownKey", KeyLookup.find(ENTRIES, "key.nothing").refusal);
        assertEquals("unknownKey", KeyLookup.find(ENTRIES, "key.Jump").refusal, "names match exactly");
        assertEquals("unknownKey", KeyLookup.find(ENTRIES, "key.").refusal, "a prefix is no name");
        assertEquals("ambiguousKey", KeyLookup.find(ENTRIES, "key.mod.skill").refusal);
        assertEquals(-1, KeyLookup.find(ENTRIES, "key.mod.skill").index);
        assertEquals("unbound", KeyLookup.find(ENTRIES, "key.mod.unset").refusal);
    }

    @Test
    @DisplayName("a listing keeps the order, filters by prefix, and marks both bindings that share a name")
    void listMarksDuplicates() {
        List<KeyLookup.Listed> all = KeyLookup.list(ENTRIES, "");
        assertEquals(6, all.size());
        assertEquals("key.forward", all.get(0).entry.name);
        assertFalse(all.get(0).duplicate);
        assertTrue(all.get(2).duplicate);
        assertTrue(all.get(3).duplicate);
        assertEquals(19, all.get(2).entry.keyCode);
        assertEquals(20, all.get(3).entry.keyCode);
        List<KeyLookup.Listed> mod = KeyLookup.list(ENTRIES, "key.mod.");
        assertEquals(3, mod.size());
        assertEquals(1, KeyLookup.list(ENTRIES, "waila.").size());
        assertEquals(0, KeyLookup.list(ENTRIES, "nothing.").size());
        assertEquals(6, KeyLookup.list(ENTRIES, null).size(), "no prefix lists all");
    }

    @Test
    @DisplayName("a name is marked shared even when the other binding with it is past the limit")
    void duplicatePastTheLimit() {
        List<KeyLookup.Entry> many = new ArrayList<KeyLookup.Entry>();
        many.add(entry("key.twice", 30));
        for (int i = 0; i < KeyLookup.MAX_LISTED; i++) {
            many.add(entry("key.n" + i, 1));
        }
        many.add(entry("key.twice", 31));
        List<KeyLookup.Listed> listed = KeyLookup.list(many, "key.");
        assertEquals(KeyLookup.MAX_LISTED, listed.size(), "bounded");
        assertTrue(listed.get(0).duplicate);
        assertEquals(KeyLookup.MAX_LISTED + 2, KeyLookup.matching(many, "key."), "the count says how many there were");
    }

    @Test
    @DisplayName("a press counted on another binding on the key, or on none, is named; one on the binding asked is not")
    void countedElsewhere() {
        List<String> names = Arrays.asList("key.mod.skill", "key.jump", "key.other");
        assertNull(KeyLookup.countedElsewhere(names, new int[] {0, 0, 0}, new int[] {0, 1, 0}, 1));
        assertEquals("key.mod.skill",
                KeyLookup.countedElsewhere(names, new int[] {0, 0, 0}, new int[] {1, 0, 0}, 1),
                "the active binding on the key took it");
        assertEquals("none", KeyLookup.countedElsewhere(names, new int[] {0, 0, 0}, new int[] {0, 0, 0}, 1),
                "no binding was active, as with a modifier not held");
        assertNull(KeyLookup.countedElsewhere(names, new int[] {0, -1, 0}, new int[] {1, -1, 0}, 1),
                "the count cannot be read: nothing is claimed");
    }

    @Test
    @DisplayName("before a queued key goes down: the binding the game's lookup finds active is where it counts")
    void countedOnActive() {
        assertNull(KeyLookup.countedOnActive(true, true, "key.jump"), "the named binding is the active one");
        assertEquals("key.mod.skill", KeyLookup.countedOnActive(true, false, "key.mod.skill"),
                "another binding on the key is active, as with its modifier held");
        assertEquals("none", KeyLookup.countedOnActive(true, false, null),
                "no binding on the key is active, as with the named one's modifier not held");
        assertNull(KeyLookup.countedOnActive(false, false, null), "the lookup cannot be read: nothing is claimed");
    }

    @Test
    @DisplayName("a queued press counted elsewhere is never queued and fails; a queued hold goes down and names it")
    void queuedPressCountedElsewhere() {
        String elsewhere = KeyLookup.countedOnActive(true, false, "key.mod.skill");
        assertEquals(PressStages.Act.NOT_COUNTED, PressStages.beforeAdd(PressStages.Act.ADD_PRESS, elsewhere, false));
        assertEquals(PressStages.Stage.OVER,
                PressStages.after(PressStages.Stage.QUEUE_DUE, PressStages.Act.NOT_COUNTED, true));
        assertEquals(PressStages.Act.ADD_PRESS, PressStages.beforeAdd(PressStages.Act.ADD_PRESS, elsewhere, true),
                "a hold goes down all the same, as the default route's does");
        String here = KeyLookup.countedOnActive(true, true, "key.jump");
        assertEquals(PressStages.Act.ADD_PRESS, PressStages.beforeAdd(PressStages.Act.ADD_PRESS, here, false));
        assertEquals(PressStages.Act.CANCEL, PressStages.beforeAdd(PressStages.Act.CANCEL, elsewhere, false),
                "a blocked key-down stays refused for its own reason");
    }
}
