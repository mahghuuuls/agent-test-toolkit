package com.mahghuuuls.agenttesttoolkit.state;

import java.util.UUID;

/**
 * The one player a session treats as its operator.
 *
 * <p>Held as an identity, never as a player object. A session outlives any single connection,
 * and a held player would either throw after a disconnect or, worse, keep answering as a ghost.
 * Whoever needs the live player resolves this UUID through the player list at that moment, the
 * same rule bundle senders follow.
 *
 * <p>Immutable and free of Minecraft types, so the rules about when a binding exists can be
 * tested without a game.
 */
public final class OperatorBinding {

    private final UUID uuid;
    private final String name;

    public OperatorBinding(UUID uuid, String name) {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid must not be null");
        }
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name must not be null or empty");
        }
        this.uuid = uuid;
        this.name = name;
    }

    public UUID getUuid() {
        return uuid;
    }

    /** The name as it was when bound. Placeholders substitute this exact text. */
    public String getName() {
        return name;
    }

    /** A selector that resolves to exactly this player while they are online. */
    public String getSelector() {
        return "@p[name=" + name + "]";
    }

    public boolean isPlayer(UUID candidate) {
        return uuid.equals(candidate);
    }

    @Override
    public String toString() {
        return name + " (" + uuid + ")";
    }
}
