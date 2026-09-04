package com.mahghuuuls.agenttesttoolkit.inspect;

import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.SessionStamp;
import net.minecraft.command.CommandException;
import net.minecraft.command.EntitySelector;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Resolves a target argument to exactly one entity, or fails explicitly.
 *
 * <p>Choosing among multiple matches is never allowed. Determinism beats convenience here:
 * an agent that wrote a selector matching two entities has a bug in its setup, and silently
 * inspecting whichever one happened to be first would hide it behind plausible-looking output.
 * Zero matches and several matches are both errors, and they are distinguishable errors.
 *
 * <p>Four argument forms are accepted, told apart by shape rather than by a flag:
 *
 * <ul>
 *   <li>An {@code @}-selector, matched by the vanilla selector engine.</li>
 *   <li>A UUID, looked up directly across every loaded dimension.</li>
 *   <li>An integer entity ID, looked up directly across every loaded dimension.</li>
 *   <li>Anything else, treated as a player name.</li>
 * </ul>
 *
 * <p>The direct forms exist because the selector engine is vanilla's, and when it answers
 * "nothing" the toolkit cannot say why. The entity listing already prints every entity's ID,
 * and every toolkit record prints UUIDs, so an agent always has a selector-free handle on the
 * thing it just saw. The direct forms find loaded entities only; their not-found wording says
 * "not loaded or absent" because the two are indistinguishable from here.
 *
 * <p>When a selector does match nothing, the error record carries what the selector engine
 * consulted: whether the sender held selector permission, which dimensions exist, and how
 * many live entities the sender's dimension holds. That is the difference between a log that
 * says "nothing matched" and one that lets the next reader work out why.
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

    /** The shape of a target argument. Decided by syntax alone, so it is testable. */
    public enum Form {
        SELECTOR, UUID, ENTITY_ID, PLAYER_NAME;

        /** The value written to records, so a reader can tell how an entity was found. */
        public String recordName() {
            switch (this) {
                case SELECTOR:
                    return "selector";
                case UUID:
                    return "uuid";
                case ENTITY_ID:
                    return "entityId";
                default:
                    return "name";
            }
        }
    }

    private EntityTarget() {
    }

    /**
     * Classifies a target argument by its shape.
     *
     * <p>A UUID is the canonical 8-4-4-4-12 hexadecimal form. An entity ID is a plain
     * non-negative integer. Neither can be a valid player name, so there is no ambiguity to
     * resolve, only a shape to recognise.
     */
    public static Form classify(String argument) {
        if (argument == null || argument.isEmpty()) {
            return Form.PLAYER_NAME;
        }
        if (argument.charAt(0) == '@') {
            return Form.SELECTOR;
        }
        if (argument.length() == 36 && argument.charAt(8) == '-' && argument.charAt(13) == '-'
                && argument.charAt(18) == '-' && argument.charAt(23) == '-') {
            try {
                java.util.UUID.fromString(argument);
                return Form.UUID;
            } catch (IllegalArgumentException notAUuid) {
                return Form.PLAYER_NAME;
            }
        }
        boolean allDigits = true;
        for (int i = 0; i < argument.length(); i++) {
            if (!Character.isDigit(argument.charAt(i))) {
                allDigits = false;
                break;
            }
        }
        return allDigits ? Form.ENTITY_ID : Form.PLAYER_NAME;
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

    /** The not-found wording for a direct lookup. Says both possibilities, because both are. */
    static String notLoadedMessage(Form form, String argument) {
        String what = form == Form.UUID ? "UUID" : "entity ID";
        return "No loaded entity has " + what + " " + argument
                + " in any dimension (not loaded, or absent).";
    }

    /**
     * @return every entity the argument denotes. A selector is matched by the vanilla
     * engine; a UUID or entity ID is looked up directly; anything else is a player name.
     *
     * <p>The direct forms and the player-name form can never return more than one entity,
     * so the ambiguity this class exists to catch cannot arise there, which is safe by
     * construction rather than by a check.
     */
    public static List<Entity> matchAll(MinecraftServer server, ICommandSender sender, String selector)
            throws CommandException {
        List<Entity> matches = new ArrayList<Entity>();
        if (selector == null || selector.isEmpty()) {
            return matches;
        }
        switch (classify(selector)) {
            case SELECTOR:
                matches.addAll(EntitySelector.matchEntities(sender, selector, Entity.class));
                if (matches.isEmpty()) {
                    recordZeroMatch(server, sender, selector);
                }
                return matches;
            case UUID:
                Entity byUuid = server.getEntityFromUuid(java.util.UUID.fromString(selector));
                if (byUuid != null) {
                    matches.add(byUuid);
                }
                return matches;
            case ENTITY_ID:
                Entity byId = findById(server, selector);
                if (byId != null) {
                    matches.add(byId);
                }
                return matches;
            default:
                EntityPlayer byName = server.getPlayerList().getPlayerByUsername(selector);
                if (byName != null) {
                    matches.add(byName);
                }
                return matches;
        }
    }

    /**
     * Resolves exactly one entity.
     *
     * @throws CardinalityException when a selector matches zero entities or more than one
     * @throws CommandException when a direct lookup finds nothing loaded
     */
    public static Entity requireExactlyOne(MinecraftServer server, ICommandSender sender, String selector)
            throws CommandException {
        List<Entity> matches = matchAll(server, sender, selector);
        Form form = classify(selector);
        if (matches.isEmpty() && (form == Form.UUID || form == Form.ENTITY_ID)) {
            // Not a cardinality problem: a direct handle denotes at most one entity, and the
            // question is only whether it is loaded. Worded so the reader does not go looking
            // for an entity that may simply be in an unloaded chunk.
            recordDirectMiss(server, sender, form, selector);
            throw new CommandException(notLoadedMessage(form, selector));
        }
        CardinalityException problem = checkCardinality(matches.size());
        if (problem != null) {
            throw problem;
        }
        return matches.get(0);
    }

    /**
     * Entity IDs are assigned per process and are not unique across dimensions, so every
     * loaded dimension is asked and the first answer wins. The caller records the entity's
     * own dimension, which is what makes the answer unambiguous in the log.
     */
    private static Entity findById(MinecraftServer server, String argument) {
        int id;
        try {
            id = Integer.parseInt(argument);
        } catch (NumberFormatException tooLarge) {
            return null;
        }
        for (WorldServer world : server.worlds) {
            if (world == null) {
                continue;
            }
            Entity entity = world.getEntityByID(id);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    /**
     * A direct handle found nothing loaded. Recorded so a bundle log shows it beside the
     * failure, with the dimensions that were searched: an entity left behind in a dimension
     * that has since unloaded is the common cause, and the list is what shows it.
     */
    private static void recordDirectMiss(MinecraftServer server, ICommandSender sender, Form form,
                                         String argument) {
        LogRecord record = RecordContext.stamp(LogRecord.of(EventType.ERROR), sender);
        SessionStamp.apply(record);
        record.add("message", notLoadedMessage(form, argument));
        record.add("target", argument);
        addDimensionContext(record, server, sender);
        ToolkitLog.write(record);
    }

    /**
     * The loaded dimensions and the sender's own, as fields. Returns the sender's world so a
     * caller that wants to count its entities can, or null when the sender has none.
     */
    private static net.minecraft.world.World addDimensionContext(LogRecord record, MinecraftServer server,
                                                                  ICommandSender sender) {
        StringBuilder dimensions = new StringBuilder();
        int count = 0;
        for (WorldServer world : server.worlds) {
            if (world == null) {
                continue;
            }
            if (dimensions.length() > 0) {
                dimensions.append(',');
            }
            dimensions.append(world.provider.getDimension());
            count++;
        }
        // Both the count and the ids: a single id reads like a count of zero otherwise.
        record.add("dimensionCount", count);
        record.add("dimensionIds", dimensions.toString());

        net.minecraft.world.World senderWorld = sender.getEntityWorld();
        if (senderWorld != null) {
            record.add("senderDimension", senderWorld.provider.getDimension());
        }
        return senderWorld;
    }

    /**
     * Writes what the selector engine had to work with when it found nothing.
     *
     * <p>Read before anyone can act on the failure, so the record is evidence rather than a
     * reconstruction. Each field is one cheap read: a permission check the engine itself
     * performs first, the dimension list it scans, and the size of the entity list it filters.
     */
    private static void recordZeroMatch(MinecraftServer server, ICommandSender sender, String selector) {
        LogRecord record = RecordContext.stamp(LogRecord.of(EventType.ERROR), sender);
        SessionStamp.apply(record);
        record.add("message", "Entity selector matched no entities");
        record.add("selector", selector);
        record.add("selectorPermission", sender.canUseCommand(1, "@"));
        net.minecraft.world.World senderWorld = addDimensionContext(record, server, sender);
        if (senderWorld != null) {
            int live = 0;
            for (Entity entity : senderWorld.loadedEntityList) {
                if (entity != null && entity.isEntityAlive()) {
                    live++;
                }
            }
            record.add("liveEntities", live);
        }
        ToolkitLog.write(record);
    }
}
