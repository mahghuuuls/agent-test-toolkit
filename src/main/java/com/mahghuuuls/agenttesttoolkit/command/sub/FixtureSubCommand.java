package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.command.SubCommand;
import com.mahghuuuls.agenttesttoolkit.fixture.FixtureLookup;
import com.mahghuuuls.agenttesttoolkit.fixture.FixtureOwnership;
import com.mahghuuuls.agenttesttoolkit.fixture.FixtureRemovals;
import com.mahghuuuls.agenttesttoolkit.fixture.FixtureTags;
import com.mahghuuuls.agenttesttoolkit.inspect.EntityTarget;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.DiagnosticSession;
import com.mahghuuuls.agenttesttoolkit.state.SessionStamp;
import com.mahghuuuls.agenttesttoolkit.state.ToolkitState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Names an entity the bundle just made, finds it again, and removes exactly it.
 *
 * <p>The toolkit never spawns. The bundle's own {@code summon} does, and then {@code claim}
 * finds that one entity and stamps it with a tag that says which session owns it and under
 * which handle. From then on {@code ${fixture.<handle>.uuid}} names it, {@code list} says
 * whether it is loaded, unloaded, or gone, and {@code clear} removes it and nothing else.
 *
 * <p>{@code clear} takes a fixture out of the world without a death: no drops, no death events,
 * and {@code removed} is written only once it is gone (REQ-210).
 *
 * <p>Nothing here infers ownership from a name or a type, and nothing trusts the registry over
 * the entity: every action decides by the tag, through {@link FixtureLookup} and
 * {@link FixtureOwnership}. A tag left by an earlier session of the same name can therefore be
 * claimed again under its handle, or released, and an entity that merely looks like a fixture,
 * or belongs to another session, is left alone and the refusal is written down.
 */
public final class FixtureSubCommand implements SubCommand {

    private static final List<String> ACTIONS = Arrays.asList("claim", "list", "clear", "release");

    @Override
    public String getName() {
        return "fixture";
    }

    @Override
    public String getDescription() {
        return "Claim, list, release, and remove entities the session owns";
    }

    @Override
    public String getUsage() {
        return "fixture <claim <handle> <selector|uuid|entityId> | list | clear <handle|all> | release <handle|all>>";
    }

    @Override
    public boolean requiresPlayer() {
        return false;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (args.length == 0) {
            throw new WrongUsageException("/devtool " + getUsage());
        }
        DiagnosticSession session = ToolkitState.getActiveSession();
        if (session == null) {
            throw new CommandException("[DevToolkit] No active session. Fixtures belong to a session.");
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if ("claim".equals(action)) {
            claim(server, sender, session, args);
        } else if ("list".equals(action)) {
            list(server, sender, session);
        } else if ("clear".equals(action)) {
            clear(server, sender, session, args);
        } else if ("release".equals(action)) {
            release(server, sender, session, args);
        } else {
            throw new WrongUsageException("/devtool " + getUsage());
        }
    }

    private void claim(MinecraftServer server, ICommandSender sender, DiagnosticSession session,
                       String[] args) throws CommandException {
        if (args.length < 3) {
            throw new WrongUsageException("/devtool fixture claim <handle> <selector|uuid|entityId>");
        }
        String handle = args[1];
        if (!FixtureTags.isHandleSafe(handle)) {
            throw new CommandException("[DevToolkit] A handle uses only letters, digits, _ and -: " + handle);
        }
        Entity entity = EntityTarget.requireExactlyOne(server, sender, args[2]);
        if (entity instanceof EntityPlayer) {
            throw new CommandException("[DevToolkit] A player cannot be a fixture.");
        }

        FixtureLookup lookup = new FixtureLookup(server, session.getName());
        String uuid = entity.getUniqueID().toString();
        // Read from the cache entry as it stands, before any lookup refills it.
        String cachedUuid = ToolkitState.getFixtureUuid(handle);
        FixtureOwnership.Cached cached = lookup.cachedState(handle);
        FixtureOwnership.Decision decision = FixtureOwnership.decide(session.getName(), handle, uuid,
                entity.getTags(), cached, cachedUuid, lookup.otherCarrier(handle, entity));
        switch (decision.kind) {
            case OTHER_HANDLE:
                ToolkitLog.error("Entity is already a fixture of this session", decision.detail);
                throw new CommandException("[DevToolkit] That entity is already claimed as "
                        + decision.detail + ". Claim it under that handle, or release it first.");
            case HANDLE_TAKEN:
                // Refused rather than replaced. Two entities under one handle would make the
                // placeholder ambiguous and a later clear a coin flip.
                ToolkitLog.error("Fixture handle already claimed", handle + " = " + decision.detail);
                throw new CommandException("[DevToolkit] Handle " + handle + " already belongs to "
                        + decision.detail + " in this session.");
            case ALREADY_CLAIMED:
                ToolkitLog.error("Fixture handle already claimed", handle);
                throw new CommandException("[DevToolkit] Handle already claimed in this session: " + handle);
            case CLAIM:
            case ADOPT:
                break;
            default:
                // INVALID_HANDLE: the handle was checked above, so this is never reached.
                throw new CommandException("[DevToolkit] A handle uses only letters, digits, _ and -: " + handle);
        }

        boolean adopted = decision.kind == FixtureOwnership.Kind.ADOPT;
        String tag = decision.detail;
        if (!adopted && !entity.addTag(tag)) {
            throw new CommandException("[DevToolkit] The entity already carries 1024 tags; cannot claim it.");
        }
        ToolkitState.putFixture(handle, uuid);
        FixtureLookup.remember(entity);

        LogRecord record = begin(sender, "claim", handle);
        record.add("uuid", uuid);
        record.add("entityId", entity.getEntityId());
        ResourceLocation type = EntityList.getKey(entity);
        record.add("entity", type == null ? null : type.toString());
        record.add("tag", tag);
        if (adopted) {
            record.add("adopted", true);
        }
        record.add("dimension", entity.dimension);
        record.addEntityPos("pos", entity.posX, entity.posY, entity.posZ);
        ToolkitLog.write(record);

        sender.sendMessage(new TextComponentString("[DevToolkit] " + (adopted ? "Adopted '" : "Claimed '")
                + handle + "' = " + uuid + " (" + (type == null ? "unknown" : type.toString()) + "), tag "
                + tag + "."));
    }

    private void list(MinecraftServer server, ICommandSender sender, DiagnosticSession session) {
        FixtureLookup lookup = new FixtureLookup(server, session.getName());
        List<String> handles = lookup.handles();
        if (handles.isEmpty()) {
            LogRecord record = begin(sender, "list", null);
            record.add("fixtures", 0);
            ToolkitLog.write(record);
            sender.sendMessage(new TextComponentString("[DevToolkit] No fixtures claimed in this session."));
            return;
        }
        List<String> summary = new ArrayList<String>();
        for (String handle : handles) {
            FixtureLookup.Found found = lookup.resolve(handle);
            if (found == null) {
                continue;
            }
            LogRecord record = begin(sender, "list", handle);
            record.add("fixtures", handles.size());
            record.add("uuid", found.uuid);
            record.add("state", found.state.recordName());
            record.add("carriers", found.carriers);
            ToolkitState.FixturePlace place = ToolkitState.getFixturePlace(found.uuid);
            if (place != null) {
                record.add("dimension", place.dimension);
                record.addBlockPos("last", place.x, place.y, place.z);
            }
            ToolkitLog.write(record);
            summary.add(handle + "=" + found.state.recordName());
        }
        sender.sendMessage(new TextComponentString("[DevToolkit] " + summary.size()
                + " fixture(s): " + String.join(", ", summary) + "."));
    }

    private void clear(MinecraftServer server, ICommandSender sender, DiagnosticSession session,
                       String[] args) throws CommandException {
        if (args.length < 2) {
            throw new WrongUsageException("/devtool fixture clear <handle|all>");
        }
        FixtureLookup lookup = new FixtureLookup(server, session.getName());
        List<String> handles = handlesFor(lookup, args[1]);

        int removed = 0;
        int refused = 0;
        int missing = 0;
        for (String handle : handles) {
            FixtureLookup.Found found = lookup.resolve(handle);
            if (found == null) {
                continue;
            }
            String uuid = found.uuid;
            Entity entity = found.entity;
            LogRecord record = begin(sender, "clear", handle);
            record.add("uuid", uuid);
            if (found.state == FixtureLookup.State.DUPLICATE) {
                // Two living entities carry one handle's tag. Both are this session's, but
                // removing one and calling the handle cleared would leave the other behind.
                record.add("outcome", "refused");
                record.add("reason", "duplicate");
                record.add("carriers", found.carriers);
                ToolkitLog.write(record);
                refused++;
                continue;
            }
            if (found.state == FixtureLookup.State.REMOVING) {
                // Already dying and leaving the world; nothing left to remove, and not counted
                // as a removal, so one UUID never gets two removed records.
                record.add("state", found.state.recordName());
                record.add("outcome", "alreadyRemoving");
                ToolkitLog.write(record);
                ToolkitState.removeFixture(handle);
                continue;
            }
            if (entity == null) {
                // Not among loaded entities. Said as unloaded or absent, never as removed,
                // and the handle is kept so a later list can still find it.
                record.add("state", found.state.recordName());
                record.add("outcome", "notLoaded");
                ToolkitLog.write(record);
                missing++;
                continue;
            }
            String expectedTag = FixtureTags.tag(session.getName(), handle);
            if (!entity.getTags().contains(expectedTag)) {
                // The registry says this is ours; the entity does not. The entity wins.
                record.add("outcome", "refused");
                record.add("reason", "notOwned");
                record.add("tags", String.join(",", entity.getTags()));
                ToolkitLog.write(record);
                refused++;
                continue;
            }
            record.add("entityId", entity.getEntityId());
            ResourceLocation type = EntityList.getKey(entity);
            record.add("entity", type == null ? null : type.toString());
            record.addEntityPos("pos", entity.posX, entity.posY, entity.posZ);
            // Removed without a death; the record, with its outcome, is written once the entity
            // is gone, at the end of the next tick (REQ-210).
            FixtureRemovals.remove(server, entity, handle, record);
            removed++;
        }
        sender.sendMessage(new TextComponentString("[DevToolkit] Fixtures: " + removed + " removal(s) scheduled, "
                + refused + " refused, " + missing + " not loaded."));
    }

    private void release(MinecraftServer server, ICommandSender sender, DiagnosticSession session,
                         String[] args) throws CommandException {
        if (args.length < 2) {
            throw new WrongUsageException("/devtool fixture release <handle|all>");
        }
        FixtureLookup lookup = new FixtureLookup(server, session.getName());
        List<String> handles = handlesFor(lookup, args[1]);

        int released = 0;
        int refused = 0;
        int missing = 0;
        for (String handle : handles) {
            FixtureLookup.Found found = lookup.resolve(handle);
            if (found == null) {
                continue;
            }
            LogRecord record = begin(sender, "release", handle);
            record.add("uuid", found.uuid);
            String tag = FixtureTags.tag(session.getName(), handle);
            if (found.state == FixtureLookup.State.DUPLICATE) {
                record.add("outcome", "refused");
                record.add("reason", "duplicate");
                record.add("carriers", found.carriers);
                ToolkitLog.write(record);
                refused++;
                continue;
            }
            if (found.entity != null && !found.entity.getTags().contains(tag)) {
                // Loaded, but the entity no longer says it is ours. The entity wins.
                record.add("outcome", "refused");
                record.add("reason", "notOwned");
                record.add("tags", String.join(",", found.entity.getTags()));
                ToolkitLog.write(record);
                refused++;
                continue;
            }
            if (found.entity == null) {
                // The tag is the only thing a release changes, and it can be reached only on a
                // loaded entity.
                record.add("state", found.state.recordName());
                record.add("outcome", "notLoaded");
                ToolkitLog.write(record);
                missing++;
                continue;
            }
            found.entity.removeTag(tag);
            ToolkitState.removeFixture(handle);
            record.add("entityId", found.entity.getEntityId());
            record.add("outcome", "released");
            ToolkitLog.write(record);
            released++;
        }
        sender.sendMessage(new TextComponentString("[DevToolkit] Fixtures: " + released
                + " released, " + refused + " refused, " + missing + " not loaded."));
    }

    /** One handle, or every handle the cache or a loaded entity knows; an unknown handle fails. */
    private static List<String> handlesFor(FixtureLookup lookup, String which) throws CommandException {
        if ("all".equalsIgnoreCase(which)) {
            return lookup.handles();
        }
        if (lookup.resolve(which) == null) {
            throw new CommandException("[DevToolkit] No fixture claimed under handle " + which + ".");
        }
        return Collections.singletonList(which);
    }

    private static LogRecord begin(ICommandSender sender, String action, String handle) {
        LogRecord record = RecordContext.stamp(LogRecord.of(EventType.FIXTURE), sender);
        SessionStamp.apply(record);
        record.add("action", action);
        record.add("handle", handle);
        return record;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                          String[] args) {
        if (args.length <= 1) {
            return CommandBase.getListOfStringsMatchingLastWord(args, ACTIONS);
        }
        if (args.length == 2 && ("clear".equalsIgnoreCase(args[0]) || "release".equalsIgnoreCase(args[0]))) {
            List<String> options = new ArrayList<String>(ToolkitState.getFixtures().keySet());
            options.add("all");
            return CommandBase.getListOfStringsMatchingLastWord(args, options);
        }
        return Collections.emptyList();
    }
}
