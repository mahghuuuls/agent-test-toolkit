package com.mahghuuuls.agenttesttoolkit.inspect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a target argument's shape is recognised.
 *
 * <p>The selector-free forms exist so an agent has a handle on an entity that vanilla's
 * filtering cannot take away. They are told apart by syntax alone, and the rules here are
 * the ones a bundle author relies on: a UUID is never mistaken for a name, an integer is
 * never mistaken for a name, and a name is never mistaken for either.
 */
class EntityTargetFormsTest {

    @Test
    @DisplayName("an @ token is a selector")
    void selectorForm() {
        assertEquals(EntityTarget.Form.SELECTOR, EntityTarget.classify("@e[name=pig]"));
        assertEquals(EntityTarget.Form.SELECTOR, EntityTarget.classify("@p"));
    }

    @Test
    @DisplayName("a canonical UUID is a UUID")
    void uuidForm() {
        assertEquals(EntityTarget.Form.UUID,
                EntityTarget.classify("0f01c58f-7f2e-4afd-860b-1e8dbd9e197b"));
        assertEquals(EntityTarget.Form.UUID,
                EntityTarget.classify("150375F5-4A14-34CF-A853-D9DF6B89048F"));
    }

    @Test
    @DisplayName("a plain integer is an entity ID")
    void idForm() {
        assertEquals(EntityTarget.Form.ENTITY_ID, EntityTarget.classify("17"));
        assertEquals(EntityTarget.Form.ENTITY_ID, EntityTarget.classify("0"));
    }

    @Test
    @DisplayName("anything else is a player name, including near misses")
    void nameForm() {
        assertEquals(EntityTarget.Form.PLAYER_NAME, EntityTarget.classify("Developer"));
        assertEquals(EntityTarget.Form.PLAYER_NAME, EntityTarget.classify("mahghuuuls"));
        assertEquals(EntityTarget.Form.PLAYER_NAME, EntityTarget.classify("17a"));
        assertEquals(EntityTarget.Form.PLAYER_NAME, EntityTarget.classify("-5"));
        // Right length and dashes, wrong characters: not a UUID.
        assertEquals(EntityTarget.Form.PLAYER_NAME,
                EntityTarget.classify("zzzzzzzz-zzzz-zzzz-zzzz-zzzzzzzzzzzz"));
        assertEquals(EntityTarget.Form.PLAYER_NAME, EntityTarget.classify(""));
        assertEquals(EntityTarget.Form.PLAYER_NAME, EntityTarget.classify(null));
    }

    @Test
    @DisplayName("the direct-lookup miss says not loaded or absent, never just absent")
    void notLoadedWording() {
        String byUuid = EntityTarget.notLoadedMessage(EntityTarget.Form.UUID,
                "0f01c58f-7f2e-4afd-860b-1e8dbd9e197b");
        String byId = EntityTarget.notLoadedMessage(EntityTarget.Form.ENTITY_ID, "17");
        assertTrue(byUuid.contains("not loaded"), byUuid);
        assertTrue(byUuid.contains("UUID"), byUuid);
        assertTrue(byId.contains("entity ID 17"), byId);
        assertTrue(byId.contains("any dimension"), byId);
    }
}
