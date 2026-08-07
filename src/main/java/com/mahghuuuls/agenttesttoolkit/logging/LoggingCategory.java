package com.mahghuuuls.agenttesttoolkit.logging;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The closed set of generic event categories the toolkit can observe.
 *
 * <p>Every category is disabled by default and never persists across a process restart, per
 * That is deliberate: a stale diagnostic setting producing unexplained log volume in
 * a later test is worse than having to enable it again.
 *
 * <p>The category name and the event type name differ in two cases, {@code player_interaction}
 * against {@code PLAYER_INTERACT} and {@code entity_interaction} against {@code ENTITY_INTERACT}.
 * Both spellings come from the owner specification, which uses one vocabulary for the command
 * surface and another for the record vocabulary. Kept rather than harmonised, because changing
 * either would break an interface someone types or an agent greps.
 */
public enum LoggingCategory {

    BLOCK_PLACE("block_place", EventType.BLOCK_PLACE),
    BLOCK_BREAK("block_break", EventType.BLOCK_BREAK),
    ENTITY_SPAWN("entity_spawn", EventType.ENTITY_SPAWN),
    ENTITY_DEATH("entity_death", EventType.ENTITY_DEATH),
    ENTITY_DAMAGE("entity_damage", EventType.ENTITY_DAMAGE),
    PLAYER_INTERACTION("player_interaction", EventType.PLAYER_INTERACT),
    ENTITY_INTERACTION("entity_interaction", EventType.ENTITY_INTERACT),
    ITEM_USE("item_use", EventType.ITEM_USE);

    private static final Map<String, LoggingCategory> BY_NAME;

    static {
        Map<String, LoggingCategory> byName = new LinkedHashMap<String, LoggingCategory>();
        for (LoggingCategory category : values()) {
            byName.put(category.categoryName, category);
        }
        BY_NAME = Collections.unmodifiableMap(byName);
    }

    private final String categoryName;
    private final EventType eventType;

    LoggingCategory(String categoryName, EventType eventType) {
        this.categoryName = categoryName;
        this.eventType = eventType;
    }

    /** The name typed on the command line and shown by status. */
    public String getCategoryName() {
        return categoryName;
    }

    /** The record vocabulary entry this category emits. */
    public EventType getEventType() {
        return eventType;
    }

    /** @return the category, or null when the name is not recognised. */
    public static LoggingCategory byName(String name) {
        if (name == null) {
            return null;
        }
        return BY_NAME.get(name.toLowerCase(Locale.ROOT));
    }

    /** Every category name, in declaration order, for help and tab completion. */
    public static Iterable<String> allNames() {
        return BY_NAME.keySet();
    }
}
