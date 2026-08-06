package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.command.Senders;
import com.mahghuuuls.agenttesttoolkit.command.SubCommand;
import com.mahghuuuls.agenttesttoolkit.inspect.RadiusFilter;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.SessionStamp;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextComponentString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Lists entities near the invoking player, one record each.
 *
 * <p>REQ-077. Deliberately not a query system: radius only, no sorting, no filtering, no
 * expression language. TellMe already lists entities for a human audience; the reason this
 * exists is machine-readable output in {@code latest.log} without an external dependency, and
 * public documentation must say that rather than claim a gap. REQ-143.
 */
public final class EntitiesSubCommand implements SubCommand {

    private static final List<String> ACTIONS = Arrays.asList("nearby");

    /** Guards against a mistyped radius sweeping an entire dimension's loaded entities. */
    private static final double MAX_RADIUS = 256.0D;

    @Override
    public String getName() {
        return "entities";
    }

    @Override
    public String getDescription() {
        return "List entities within a radius of you";
    }

    @Override
    public String getUsage() {
        return "entities nearby <radius>";
    }

    @Override
    public boolean requiresPlayer() {
        // The radius is anchored to the invoking player, so there is no sensible console
        // behaviour. Refused explicitly rather than defaulting to the world origin, which
        // would silently list the wrong place.
        return true;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (args.length < 2 || !"nearby".equals(args[0].toLowerCase(Locale.ROOT))) {
            throw new WrongUsageException("/devtool " + getUsage());
        }

        EntityPlayer player = Senders.asPlayer(sender);
        if (player == null) {
            throw new net.minecraft.command.CommandException(
                    "This command must be run by a player, or by a bundle a player started.");
        }
        double radius = CommandBase.parseDouble(args[1], 0.0D, MAX_RADIUS);

        AxisAlignedBB box = new AxisAlignedBB(
                player.posX - radius, player.posY - radius, player.posZ - radius,
                player.posX + radius, player.posY + radius, player.posZ + radius);

        // The box is a coarse pre-filter that the world can answer cheaply; the spherical
        // test is what the radius actually means. Skipping the second step would list corners
        // of the box up to 73 percent further away than asked for.
        List<Entity> inBox =
                player.world.getEntitiesWithinAABBExcludingEntity(player, box);
        List<Entity> matched = new ArrayList<Entity>();
        for (Entity entity : inBox) {
            if (RadiusFilter.within(player.posX, player.posY, player.posZ,
                    entity.posX, entity.posY, entity.posZ, radius)) {
                matched.add(entity);
            }
        }

        if (matched.isEmpty()) {
            // REQ-038: silence is ambiguous. "None found" must be distinguishable from the
            // command not having run.
            LogRecord record = RecordContext.stamp(LogRecord.of(EventType.ENTITY_LIST), sender);
            SessionStamp.apply(record);
            record.addDecimal("radius", radius);
            record.add("matched", 0);
            ToolkitLog.write(record);
            sender.sendMessage(new TextComponentString(
                    "[DevToolkit] No entities within " + radius + " blocks."));
            return;
        }

        for (Entity entity : matched) {
            LogRecord record = RecordContext.stamp(LogRecord.of(EventType.ENTITY_LIST), sender);
            SessionStamp.apply(record);
            record.addDecimal("radius", radius);
            // Repeated on every line so a record read in isolation still says how many there
            // were, which makes a partial or interleaved read detectable.
            record.add("matched", matched.size());

            ResourceLocation id = EntityList.getKey(entity);
            record.add("entity", id == null ? null : id.toString());
            record.add("entityId", entity.getEntityId());
            if (entity.hasCustomName()) {
                record.add("name", entity.getCustomNameTag());
            }
            record.addEntityPos("pos", entity.posX, entity.posY, entity.posZ);
            ToolkitLog.write(record);
        }

        sender.sendMessage(new TextComponentString("[DevToolkit] " + matched.size()
                + (matched.size() == 1 ? " entity" : " entities") + " within " + radius
                + " blocks, written to log."));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                          String[] args) {
        if (args.length <= 1) {
            return CommandBase.getListOfStringsMatchingLastWord(args, ACTIONS);
        }
        return Collections.emptyList();
    }
}
