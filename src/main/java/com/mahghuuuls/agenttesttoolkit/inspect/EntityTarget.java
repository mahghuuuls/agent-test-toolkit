package com.mahghuuuls.agenttesttoolkit.inspect;

import net.minecraft.command.CommandException;
import net.minecraft.command.EntitySelector;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves a selector to exactly one entity, or fails explicitly.
 *
 * <p>Choosing among multiple matches is never allowed. Determinism beats convenience here:
 * an agent that wrote a selector matching two entities has a bug in its setup, and silently
 * inspecting whichever one happened to be first would hide it behind plausible-looking output.
 * Zero matches and several matches are both errors, and they are distinguishable errors.
 *
 * <p>This is why the specification pushes agents toward naming their fixtures, for example
 * summoning with a CustomName and then selecting on it, rather than relying on proximity.
 */
public final class EntityTarget {

    /** Thrown when a selector does not resolve to exactly one entity. */
    public static final class CardinalityException extends CommandException {
        private final int matched;

        CardinalityException(String message, int matched) {
            super(message);
            this.matched = matched;
        }

        public int getMatched() {
            return matched;
        }
    }

    private EntityTarget() {
    }

    /**
     * Decides what a match count means, separately from performing the match.
     *
     * <p>Extracted so the rule is testable without a live selector engine. The
     * matching itself needs Minecraft; the decision does not.
     *
     * @return null when the count is acceptable, otherwise the error to report
     */
    static CardinalityException checkCardinality(int matched) {
        if (matched == 0) {
            return new CardinalityException("Entity selector matched no entities.", 0);
        }
        if (matched > 1) {
            return new CardinalityException(
                    "Entity selector matched " + matched + " entities; expected exactly 1.", matched);
        }
        return null;
    }

    /**
     * @return every entity the selector matches. An @-selector is matched by the vanilla
     * selector engine; anything else is treated as a player name.
     *
     * <p>The player-name path can never return more than one entity, because usernames are
     * unique among connected players. The ambiguity this class exists to catch therefore
     * cannot arise there, which is safe by construction rather than by a check.
     */
    public static List<Entity> matchAll(MinecraftServer server, ICommandSender sender, String selector)
            throws CommandException {
        List<Entity> matches = new ArrayList<Entity>();
        if (selector == null || selector.isEmpty()) {
            return matches;
        }
        if (selector.charAt(0) == '@') {
            matches.addAll(EntitySelector.matchEntities(sender, selector, Entity.class));
            return matches;
        }
        EntityPlayer byName = server.getPlayerList().getPlayerByUsername(selector);
        if (byName != null) {
            matches.add(byName);
        }
        return matches;
    }

    /**
     * Resolves exactly one entity.
     *
     * @throws CardinalityException when the selector matches zero entities or more than one
     */
    public static Entity requireExactlyOne(MinecraftServer server, ICommandSender sender, String selector)
            throws CommandException {
        List<Entity> matches = matchAll(server, sender, selector);
        CardinalityException problem = checkCardinality(matches.size());
        if (problem != null) {
            throw problem;
        }
        return matches.get(0);
    }
}
