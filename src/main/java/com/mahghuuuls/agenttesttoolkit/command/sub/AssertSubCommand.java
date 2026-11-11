package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.command.Senders;
import com.mahghuuuls.agenttesttoolkit.command.SubCommand;
import com.mahghuuuls.agenttesttoolkit.inspect.Assertions;
import com.mahghuuuls.agenttesttoolkit.inspect.Attributes;
import com.mahghuuuls.agenttesttoolkit.inspect.EntityTarget;
import com.mahghuuuls.agenttesttoolkit.inspect.RadiusFilter;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.SessionStamp;
import com.mahghuuuls.agenttesttoolkit.state.ToolkitState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Compares what the game shows against what the bundle declared.
 *
 * <p>Read-only, and bounded to a fixed list of predicates. Every assertion writes one record
 * with the expected and the observed value beside the outcome, and a failed or unevaluable
 * one throws with its own key, so a bundle stops on it exactly as it would on a broken
 * command and can tolerate it by name exactly the same way.
 *
 * <p>The grammar, one predicate per line:
 * <pre>
 * assert entities count &lt;n&gt; within &lt;r&gt; [type &lt;id&gt;] [name &lt;name&gt;]
 * assert entity &lt;uuid&gt; present|absent [at &lt;x&gt; &lt;y&gt; &lt;z&gt;]
 * assert entity &lt;uuid&gt; dimension &lt;d&gt;
 * assert entity &lt;uuid&gt; within &lt;r&gt; of &lt;x&gt; &lt;y&gt; &lt;z&gt;
 * assert player &lt;name&gt; dimension &lt;d&gt;
 * assert player &lt;name&gt; gamemode &lt;mode&gt;
 * assert attribute &lt;target&gt; &lt;attribute&gt; value &lt;n&gt;
 * assert attribute &lt;target&gt; &lt;attribute&gt; modifier &lt;uuid|name&gt; [amount &lt;n&gt;]
 * </pre>
 *
 * <p>An attribute target that matches nothing, several entities, or a non-living one fails
 * before any comparison, with the resolver's own error, and writes no {@code ASSERT} record;
 * there is no observation to compare.
 */
public final class AssertSubCommand implements SubCommand {

    private static final List<String> SUBJECTS = Arrays.asList("entities", "entity", "player", "attribute");
    private static final double MAX_RADIUS = 256.0D;

    @Override
    public String getName() {
        return "assert";
    }

    @Override
    public String getDescription() {
        return "Compare an observation against a declared value and record the outcome";
    }

    @Override
    public String getUsage() {
        return "assert <entities count <n> within <r> [type <id>] [name <name>]"
                + " | entity <uuid> <present|absent [at <x> <y> <z>] | dimension <d> | within <r> of <x> <y> <z>>"
                + " | player <name> <dimension <d> | gamemode <mode> | health <n>>"
                + " | attribute <target> <attribute> <value <n> | modifier <uuid|name> [amount <n>]>>";
    }

    @Override
    public boolean requiresPlayer() {
        return false;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (args.length < 2) {
            throw new WrongUsageException("/devtool " + getUsage());
        }
        String subject = args[0].toLowerCase(Locale.ROOT);
        Assertions.Result result;
        LogRecord record = RecordContext.stamp(LogRecord.of(EventType.ASSERT), sender);
        SessionStamp.apply(record);

        if ("entities".equals(subject)) {
            result = entitiesCount(sender, args, record);
        } else if ("entity".equals(subject)) {
            result = entity(server, sender, args, record);
        } else if ("player".equals(subject)) {
            result = player(server, args, record);
        } else if ("attribute".equals(subject)) {
            result = attribute(server, sender, args, record);
        } else {
            throw new WrongUsageException("/devtool " + getUsage());
        }

        record.add("expected", result.getExpected());
        record.add("observed", result.getObserved());
        record.add("outcome", result.getOutcome().recordName());
        if (result.getNote() != null) {
            record.add("note", result.getNote());
        }
        ToolkitLog.write(record);

        String summary = "expected " + result.getExpected() + ", observed " + result.getObserved();
        if (result.passed()) {
            sender.sendMessage(new TextComponentString("[DevToolkit] Assertion passed: " + summary + "."));
            return;
        }
        // The key is the message, so the dispatcher classifies it and a bundle can tolerate
        // it by name. The summary travels as the argument so the chat line stays readable.
        throw new CommandException(result.failureKey(), summary
                + (result.getNote() == null ? "" : " (" + result.getNote() + ")"));
    }

    /** {@code entities count <n> within <r> [type <id>] [name <name>]}, around the caller. */
    private Assertions.Result entitiesCount(ICommandSender sender, String[] args, LogRecord record)
            throws CommandException {
        if (args.length < 5 || !"count".equals(args[1]) || !"within".equals(args[3])) {
            throw new WrongUsageException("/devtool assert entities count <n> within <r> [type <id>] [name <name>]");
        }
        EntityPlayer player = Senders.asPlayer(sender);
        if (player == null) {
            throw new CommandException("[DevToolkit] entities count is anchored to a player.");
        }
        long expected = CommandBase.parseLong(args[2], 0L, Long.MAX_VALUE);
        double radius = CommandBase.parseDouble(args[4], 0.0D, MAX_RADIUS);
        String type = null;
        String name = null;
        if ((args.length - 5) % 2 != 0) {
            throw new WrongUsageException("/devtool assert entities count <n> within <r> [type <id>] [name <name>]");
        }
        for (int i = 5; i + 1 < args.length; i += 2) {
            if ("type".equals(args[i])) {
                type = args[i + 1];
            } else if ("name".equals(args[i])) {
                name = args[i + 1];
            } else {
                throw new WrongUsageException("/devtool assert entities count <n> within <r> [type <id>] [name <name>]");
            }
        }

        AxisAlignedBB box = new AxisAlignedBB(
                player.posX - radius, player.posY - radius, player.posZ - radius,
                player.posX + radius, player.posY + radius, player.posZ + radius);
        long observed = 0;
        for (Entity entity : player.world.getEntitiesWithinAABBExcludingEntity(player, box)) {
            if (!RadiusFilter.within(player.posX, player.posY, player.posZ,
                    entity.posX, entity.posY, entity.posZ, radius)) {
                continue;
            }
            if (type != null) {
                ResourceLocation id = EntityList.getKey(entity);
                if (id == null || !type.equals(id.toString())) {
                    continue;
                }
            }
            if (name != null && !(entity.hasCustomName() && name.equals(entity.getCustomNameTag()))) {
                continue;
            }
            observed++;
        }

        record.add("predicate", "entities.count");
        record.addDecimal("radius", radius);
        if (type != null) {
            record.add("type", type);
        }
        if (name != null) {
            record.add("name", name);
        }
        return Assertions.count(expected, observed);
    }

    /** The three {@code entity <uuid> ...} forms. */
    private Assertions.Result entity(MinecraftServer server, ICommandSender sender, String[] args,
                                     LogRecord record) throws CommandException {
        if (args.length < 3) {
            throw new WrongUsageException("/devtool assert entity <uuid> <present|absent|dimension|within> ...");
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(args[1]);
        } catch (IllegalArgumentException notAUuid) {
            throw new CommandException("[DevToolkit] Not a UUID: " + args[1]);
        }
        Entity found = server.getEntityFromUuid(uuid);
        record.add("uuid", uuid.toString());
        String form = args[2].toLowerCase(Locale.ROOT);

        if ("present".equals(form) || "absent".equals(form)) {
            record.add("predicate", "entity." + form);
            Boolean regionLoaded = null;
            if (args.length > 3 && "at".equals(args[3]) && args.length < 7) {
                throw new WrongUsageException("/devtool assert entity <uuid> present|absent [at <x> <y> <z>]");
            }
            if (args.length >= 7 && "at".equals(args[3])) {
                BlockPos at = CommandBase.parseBlockPos(sender, args, 4, false);
                record.addBlockPos("at", at.getX(), at.getY(), at.getZ());
                regionLoaded = sender.getEntityWorld().isBlockLoaded(at);
            } else {
                // A claimed fixture remembers where it was last seen, which is the place to
                // check for loading when nobody typed one.
                ToolkitState.FixturePlace last = ToolkitState.getFixturePlace(uuid.toString());
                if (last != null) {
                    BlockPos at = new BlockPos(last.x, last.y, last.z);
                    record.addBlockPos("at", last.x, last.y, last.z);
                    record.add("dimension", last.dimension);
                    WorldServer world = net.minecraftforge.common.DimensionManager.getWorld(last.dimension);
                    regionLoaded = world != null && world.isBlockLoaded(at);
                }
            }
            if (regionLoaded != null) {
                record.add("regionLoaded", regionLoaded.booleanValue());
            }
            boolean dead = found != null && !found.isEntityAlive();
            if (dead) {
                record.add("dead", true);
            }
            return Assertions.presence("present".equals(form), found != null, dead, regionLoaded);
        }

        if (found == null) {
            record.add("predicate", "entity." + form);
            return Assertions.presence(true, false, null);
        }

        if ("dimension".equals(form)) {
            if (args.length < 4) {
                throw new WrongUsageException("/devtool assert entity <uuid> dimension <d>");
            }
            record.add("predicate", "entity.dimension");
            return Assertions.equalsText(args[3], Integer.toString(found.dimension));
        }

        if ("within".equals(form)) {
            if (args.length < 8 || !"of".equals(args[4])) {
                throw new WrongUsageException("/devtool assert entity <uuid> within <r> of <x> <y> <z>");
            }
            double radius = CommandBase.parseDouble(args[3], 0.0D, MAX_RADIUS);
            BlockPos of = CommandBase.parseBlockPos(sender, args, 5, false);
            record.add("predicate", "entity.within");
            record.addBlockPos("of", of.getX(), of.getY(), of.getZ());
            record.addEntityPos("pos", found.posX, found.posY, found.posZ);
            double dx = found.posX - (of.getX() + 0.5D);
            double dy = found.posY - of.getY();
            double dz = found.posZ - (of.getZ() + 0.5D);
            return Assertions.withinRadius(radius, Math.sqrt(dx * dx + dy * dy + dz * dz));
        }

        throw new WrongUsageException("/devtool assert entity <uuid> <present|absent|dimension|within> ...");
    }

    /**
     * {@code attribute <target> <attribute> value <n>} and
     * {@code attribute <target> <attribute> modifier <uuid|name> [amount <n>]}. A name may hold
     * spaces, as vanilla's {@code Armor modifier} does: every word up to {@code amount} is the name.
     */
    private Assertions.Result attribute(MinecraftServer server, ICommandSender sender, String[] args,
                                        LogRecord record) throws CommandException {
        String usage = "/devtool assert attribute <target> <attribute> <value <n> | modifier <uuid|name> [amount <n>]>";
        if (args.length < 5) {
            throw new WrongUsageException(usage);
        }
        Entity entity = EntityTarget.requireExactlyOne(server, sender, args[1]);
        if (!(entity instanceof EntityLivingBase)) {
            ToolkitLog.error("Target has no attributes", args[1] + " is " + EntityList.getKey(entity));
            throw new CommandException("[DevToolkit] " + args[1] + " is not a living entity, so it has no attributes.");
        }
        record.add("targetUuid", entity.getUniqueID().toString());
        Attributes.Attribute observed = Attributes.read((EntityLivingBase) entity, args[2]);
        // The registry name as Minecraft knows it; lookup ignores case, so the typed text may differ.
        record.add("attribute", observed == null ? args[2] : observed.getName());
        String form = args[3].toLowerCase(Locale.ROOT);
        if ("value".equals(form)) {
            if (args.length != 5) {
                throw new WrongUsageException(usage);
            }
            record.add("predicate", "attribute.value");
            return Assertions.attributeValue(CommandBase.parseDouble(args[4]),
                    observed == null ? null : observed.getValue());
        }
        if ("modifier".equals(form)) {
            ModifierArgs parsed = parseModifier(args, 4, usage);
            record.add("predicate", "attribute.modifier");
            record.add("modifier", parsed.reference);
            return Assertions.modifier(parsed.reference, parsed.amount,
                    observed == null ? null : observed.getModifiers());
        }
        throw new WrongUsageException(usage);
    }

    /** A modifier reference and the optional amount after it. */
    static final class ModifierArgs {
        final String reference;
        final Double amount;

        ModifierArgs(String reference, Double amount) {
            this.reference = reference;
            this.amount = amount;
        }
    }

    /**
     * Reads {@code <uuid|name> [amount <n>]} from {@code args[from]} on. The name may hold spaces;
     * a trailing {@code amount <n>} (any case) is the amount, and a trailing {@code amount} with no
     * number is a usage error rather than part of the name.
     */
    static ModifierArgs parseModifier(String[] args, int from, String usage) throws CommandException {
        int end = args.length;
        if (end > from && "amount".equalsIgnoreCase(args[end - 1])) {
            throw new WrongUsageException(usage);
        }
        if (end - from == 2 && "amount".equalsIgnoreCase(args[from])) {
            // An amount with no reference before it.
            throw new WrongUsageException(usage);
        }
        Double amount = null;
        if (end - from >= 3 && "amount".equalsIgnoreCase(args[end - 2])) {
            amount = CommandBase.parseDouble(args[end - 1]);
            end -= 2;
        }
        if (end <= from) {
            throw new WrongUsageException(usage);
        }
        StringBuilder reference = new StringBuilder(args[from]);
        for (int i = from + 1; i < end; i++) {
            reference.append(' ').append(args[i]);
        }
        return new ModifierArgs(reference.toString(), amount);
    }

    private static final String PLAYER_USAGE = "/devtool assert player <name> <dimension <d>|gamemode <mode>|health <n>>";

    /** {@code player <name> dimension <d>}, {@code gamemode <mode>} and {@code health <n>}. */
    private Assertions.Result player(MinecraftServer server, String[] args, LogRecord record)
            throws CommandException {
        if (args.length < 4) {
            throw new WrongUsageException(PLAYER_USAGE);
        }
        EntityPlayerMP player = server.getPlayerList().getPlayerByUsername(args[1]);
        record.add("player", args[1]);
        String form = args[2].toLowerCase(Locale.ROOT);
        record.add("predicate", "player." + form);
        if (player == null) {
            // The player list is authoritative, so an offline player is a plain mismatch
            // against whatever was declared, with the declared value kept in the record.
            record.add("note", "player not online");
            return Assertions.equalsText(args[3], null);
        }
        if ("dimension".equals(form)) {
            return Assertions.equalsText(args[3], Integer.toString(player.dimension));
        }
        if ("gamemode".equals(form)) {
            return Assertions.equalsText(args[3].toLowerCase(Locale.ROOT),
                    player.interactionManager.getGameType().getName());
        }
        if ("health".equals(form)) {
            return Assertions.health(CommandBase.parseDouble(args[3]), player.getHealth());
        }
        throw new WrongUsageException(PLAYER_USAGE);
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                          String[] args) {
        if (args.length <= 1) {
            return CommandBase.getListOfStringsMatchingLastWord(args, SUBJECTS);
        }
        return Collections.emptyList();
    }
}
