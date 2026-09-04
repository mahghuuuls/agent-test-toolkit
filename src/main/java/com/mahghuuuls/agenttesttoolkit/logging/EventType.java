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

    /**
     * The session's operator was bound or unbound. Carries the player's name and UUID and,
     * when unbound, why: the player left, the session stopped, or a command cleared it.
     */
    OPERATOR,

    BUNDLE_START,
    BUNDLE_END,

    /**
     * One command of a traced bundle, written just before it runs.
     *
     * <p>Carries the tick the command became due and the tick it actually ran, so a reader
     * can see where a delay sat rather than infer it from when effects appeared. Off unless
     * the bundle asks for it, since a record per command doubles a bundle in the log.
     */
    BUNDLE_STEP,

    /**
     * Where a bundle came from, written at every load and reload: one record per file with the
     * raw hash of its bytes, one per loaded bundle with its normalized hash. The same hash
     * reappears on the start and end records of every run, which is what ties evidence to text.
     */
    BUNDLE_SOURCE,

    /**
     * A wait ended, or a single check outside a bundle answered. Carries the condition kind,
     * the outcome, how long was waited against what timeout, and the condition's own fields,
     * such as the first chunk that was still missing.
     */
    AWAIT,

    /**
     * The operator was asked or answered: a checkpoint armed, cued, or acknowledged, with the
     * ticks of each so the latency of a human action can be read rather than guessed.
     */
    CHECKPOINT,

    /**
     * One declared comparison and its outcome: pass, fail, or unloaded. Always carries the
     * expected and the observed value, so a reader can check the comparison rather than
     * trust it. Fail means "not the declared value", never a judgement about the mod.
     */
    ASSERT,

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

    /**
     * A change to which categories are recorded, or a report of the current set.
     *
     * <p>Added because the log could not answer its own most important question. Enabling a
     * category left no trace, so a reader could not tell whether an absent event did not happen
     * or was never being watched for. {@code log status} answered that to chat, where the
     * reader of a log file cannot see it.
     */
    LOG_CONFIG,

    STARTUP,
    ERROR
}
