package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.command.SubCommand;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Names an entity the bundle just made, finds it again, and removes exactly it.
 *
 * <p>The toolkit never spawns. The bundle's own {@code summon} does, and then {@code claim}
 * finds that one entity and stamps it with a tag that says which session owns it and under
 * which handle. From then on {@code ${fixture.<handle>.uuid}} names it, {@code list} says
 * whether it is loaded, unloaded, or gone, and {@code clear} removes it and nothing else.
 *
 * <p>Nothing here infers ownership from a name or a type. Cleanup checks the tag on the
 * entity at the moment of removal, so an entity that merely looks like a fixture, or belongs
 * to another session, is left alone and the refusal is written down.
 */
public final class FixtureSubCommand implements SubCommand {

    private static final List<String> ACTIONS = Arrays.asList("claim", "list", "clear");

    @Override
    public String getName() {
        return "fixture";
    }

    @Override
    public String getDescription() {
        return "Claim, list, and remove entities the session owns";
    }

    @Override
    public String getUsage() {
        return "fixture <claim <handle> <selector|uuid|entityId> | list | clear <handle|all>>";
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
            list(server, sender);
        } else if ("clear".equals(action)) {
            clear(server, sender, session, args);
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
        if (!FixtureTags.isSelectorSafe(handle)) {
            throw new CommandException("[DevToolkit] A handle uses only letters, digits, _ . and -: " + handle);
        }
        if (ToolkitState.getFixtureUuid(handle) != null) {
            // Refused rather than replaced. Two entities under one handle would make the
            // placeholder ambiguous and a later clear a coin flip.
            ToolkitLog.error("Fixture handle already claimed", handle);
            throw new CommandException("[DevToolkit] Handle already claimed in this session: " + handle);
        }

        Entity entity = EntityTarget.requireExactlyOne(server, sender, args[2]);
        if (entity instanceof EntityPlayer) {
            throw new CommandException("[DevToolkit] A player cannot be a fixture.");
        }
        for (String existing : entity.getTags()) {
            if (FixtureTags.ownedBy(existing, session.getName())) {
                // One entity, one handle. Two handles on one entity would let a clear of one
                // leave the other reporting an absence it caused itself.
                ToolkitLog.error("Entity is already a fixture of this session", existing);
                throw new CommandException("[DevToolkit] That entity is already claimed as "
                        + FixtureTags.handleOf(existing, session.getName()) + ".");
            }
        }

        String tag = FixtureTags.tag(session.getName(), handle);
        if (!entity.addTag(tag)) {
            throw new CommandException("[DevToolkit] The entity already carries 1024 tags; cannot claim it.");
        }
        String uuid = entity.getUniqueID().toString();
        ToolkitState.putFixture(handle, uuid);
        remember(entity);

        LogRecord record = begin(sender, "claim", handle);
        record.add("uuid", uuid);
        record.add("entityId", entity.getEntityId());
        ResourceLocation type = EntityList.getKey(entity);
        record.add("entity", type == null ? null : type.toString());
        record.add("tag", tag);
        record.add("dimension", entity.dimension);
        record.addEntityPos("pos", entity.posX, entity.posY, entity.posZ);
        ToolkitLog.write(record);

        sender.sendMessage(new TextComponentString("[DevToolkit] Claimed '" + handle + "' = "
                + uuid + " (" + (type == null ? "unknown" : type.toString()) + "), tag " + tag + "."));
    }

    private void list(MinecraftServer server, ICommandSender sender) {
        Map<String, String> fixtures = ToolkitState.getFixtures();
        if (fixtures.isEmpty()) {
            LogRecord record = begin(sender, "list", null);
            record.add("fixtures", 0);
            ToolkitLog.write(record);
            sender.sendMessage(new TextComponentString("[DevToolkit] No fixtures claimed in this session."));
            return;
        }
        List<String> summary = new ArrayList<String>();
        for (Map.Entry<String, String> entry : fixtures.entrySet()) {
            String handle = entry.getKey();
            String uuid = entry.getValue();
            String state = stateOf(server, uuid);
            LogRecord record = begin(sender, "list", handle);
            record.add("fixtures", fixtures.size());
            record.add("uuid", uuid);
            record.add("state", state);
            ToolkitState.FixturePlace place = ToolkitState.getFixturePlace(uuid);
            if (place != null) {
                record.add("dimension", place.dimension);
                record.addBlockPos("last", place.x, place.y, place.z);
            }
            ToolkitLog.write(record);
            summary.add(handle + "=" + state);
        }
        sender.sendMessage(new TextComponentString("[DevToolkit] " + fixtures.size()
                + " fixture(s): " + String.join(", ", summary) + "."));
    }

    private void clear(MinecraftServer server, ICommandSender sender, DiagnosticSession session,
                       String[] args) throws CommandException {
        if (args.length < 2) {
            throw new WrongUsageException("/devtool fixture clear <handle|all>");
        }
        List<String> handles = new ArrayList<String>();
        if ("all".equalsIgnoreCase(args[1])) {
            handles.addAll(ToolkitState.getFixtures().keySet());
        } else {
            if (ToolkitState.getFixtureUuid(args[1]) == null) {
                throw new CommandException("[DevToolkit] No fixture claimed under handle " + args[1] + ".");
            }
            handles.add(args[1]);
        }

        int removed = 0;
        int refused = 0;
        int missing = 0;
        for (String handle : handles) {
            String uuid = ToolkitState.getFixtureUuid(handle);
            Entity entity = server.getEntityFromUuid(UUID.fromString(uuid));
            LogRecord record = begin(sender, "clear", handle);
            record.add("uuid", uuid);
            if (entity == null) {
                // Not among loaded entities. Said as unloaded or absent, never as removed,
                // and the handle is kept so a later list can still find it.
                String state = stateOf(server, uuid);
                record.add("state", state);
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
            record.add("outcome", "removed");
            ToolkitLog.write(record);
            // The same path vanilla kill takes, so a living fixture dies rather than vanishes
            // and any death observer sees it.
            entity.onKillCommand();
            ToolkitState.removeFixture(handle);
            removed++;
        }
        sender.sendMessage(new TextComponentString("[DevToolkit] Fixtures: " + removed + " removed, "
                + refused + " refused, " + missing + " not loaded."));
    }

    /** {@code loaded}, {@code unloaded} or {@code absent}, from what can be known right now. */
    private static String stateOf(MinecraftServer server, String uuid) {
        Entity entity = server.getEntityFromUuid(UUID.fromString(uuid));
        if (entity != null) {
            remember(entity);
            return "loaded";
        }
        ToolkitState.FixturePlace place = ToolkitState.getFixturePlace(uuid);
        if (place == null) {
            return "unknown";
        }
        WorldServer world = net.minecraftforge.common.DimensionManager.getWorld(place.dimension);
        if (world == null || !world.isBlockLoaded(new BlockPos(place.x, place.y, place.z))) {
            return "unloaded";
        }
        return "absent";
    }

    /** Keeps the last known place current, so unloaded and absent can be told apart later. */
    private static void remember(Entity entity) {
        ToolkitState.putFixturePlace(entity.getUniqueID().toString(), entity.dimension,
                (int) Math.floor(entity.posX), (int) Math.floor(entity.posY), (int) Math.floor(entity.posZ));
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
        if (args.length == 2 && "clear".equalsIgnoreCase(args[0])) {
            List<String> options = new ArrayList<String>(ToolkitState.getFixtures().keySet());
            options.add("all");
            return CommandBase.getListOfStringsMatchingLastWord(args, options);
        }
        return Collections.emptyList();
    }
}
