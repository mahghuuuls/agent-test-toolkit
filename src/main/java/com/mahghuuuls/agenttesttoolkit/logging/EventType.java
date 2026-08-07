package com.mahghuuuls.agenttesttoolkit.logging;

/**
 * The complete, closed set of toolkit event type names.
 *
 * <p>This vocabulary is closed. Near-synonyms for an existing concept are forbidden: there is
 * one name for one event, and it does not change. An agent parses these names, so stability
 * matters more than expressiveness.
 *
 * <p>Most values are unused until later issues add the features that emit them. They are
 * declared here so the vocabulary exists in one place from the start, rather than growing
 * ad hoc as each feature lands.
 */
public enum EventType {

    BLOCK_PLACE,
    BLOCK_BREAK,
    ENTITY_SPAWN,
    ENTITY_DEATH,
    ENTITY_DAMAGE,
    PLAYER_INTERACT,
    ENTITY_INTERACT,
    ITEM_USE,

    SESSION_START,
    SESSION_STOP,
    MARK,

    BUNDLE_START,
    BUNDLE_END,

    ARENA_CREATE,
    ARENA_RESET,
    ARENA_CLEAR,

    PLAYER_INSPECT,
    ENTITY_INSPECT,
    BLOCK_INSPECT,
    INVENTORY_INSPECT,
    NBT,
    ENTITY_LIST,

    ENVIRONMENT,
    CAPABILITIES,
    STARTUP,
    ERROR
}
