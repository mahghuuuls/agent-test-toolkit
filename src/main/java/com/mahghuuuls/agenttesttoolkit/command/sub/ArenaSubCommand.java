package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.arena.ArenaBuilder;
import com.mahghuuuls.agenttesttoolkit.arena.ArenaGeometry;
import com.mahghuuuls.agenttesttoolkit.arena.ArenaRecord;
import com.mahghuuuls.agenttesttoolkit.arena.ArenaStorage;
import com.mahghuuuls.agenttesttoolkit.command.SubCommand;
import com.mahghuuuls.agenttesttoolkit.config.ToolkitConfig;
import com.mahghuuuls.agenttesttoolkit.config.ToolkitConfigLoader;
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
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Creates, describes, resets and clears the dimension's arena.
 *
 * <p>REQ-061: exactly one arena per dimension. Creating a new one replaces the record without
 * restoring the previous terrain, which is intended: the arena is disposable and so is the
 * world it lives in.
 *
 * <p>No confirmation prompt anywhere, per decision D3. That is not carelessness, it is a
 * requirement: {@code arena reset} is the most-used command in a setup bundle, and a prompt
 * would make it unusable from one. The size limit is the only guard.
 */
public final class ArenaSubCommand implements SubCommand {

    private static final List<String> ACTIONS =
            Arrays.asList("create", "info", "reset", "clear");

    @Override
    public String getName() {
        return "arena";
    }

    @Override
    public String getDescription() {
        return "Create, inspect, reset, or clear this dimension's test arena";
    }

    @Override
    public String getUsage() {
        return "arena <create [w] [h] [l] [block] | info | reset | clear>";
    }

    @Override
    public boolean requiresPlayer() {
        // Checked per action instead: info is useful from the console, create and reset are
        // anchored to a player and refuse it explicitly.
        return false;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (args.length == 0) {
            throw new WrongUsageException("/devtool " + getUsage());
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        if ("create".equals(action)) {
            create(sender, args);
        } else if ("info".equals(action)) {
            info(sender);
        } else if ("reset".equals(action)) {
            resetOrClear(sender, true);
        } else if ("clear".equals(action)) {
            resetOrClear(sender, false);
        } else {
            throw new WrongUsageException("/devtool " + getUsage());
        }
    }

    // --- create ----------------------------------------------------------------------

    private void create(ICommandSender sender, String[] args) throws CommandException {
        EntityPlayer player = requirePlayer(sender, "create");
        if (player == null) {
            return;
        }
        ToolkitConfig config = ToolkitConfigLoader.get();

        int width = args.length > 1 ? CommandBase.parseInt(args[1], 1) : config.getDefaultArenaWidth();
        int height = args.length > 2 ? CommandBase.parseInt(args[2], 1) : config.getDefaultArenaHeight();
        int length = args.length > 3 ? CommandBase.parseInt(args[3], 1) : config.getDefaultArenaLength();
        String blockId = args.length > 4 ? args[4] : config.getDefaultArenaBlock();

        // REQ-063: checked before anything is placed. A partial build followed by an error
        // would leave the world in a state neither the operator nor the toolkit can describe.
        int maximum = config.getMaxArenaDimension();
        if (!ArenaGeometry.withinLimit(width, height, length, maximum)) {
            String message = "[DevToolkit] Arena size " + width + "x" + height + "x" + length
                    + " exceeds the maximum of " + maximum + " per dimension. No blocks changed.";
            ToolkitLog.error("Arena size exceeds maximum",
                    width + "x" + height + "x" + length + " max=" + maximum);
            sender.sendMessage(new TextComponentString(message));
            return;
        }

        if (!ArenaBuilder.isKnownBlock(blockId)) {
            // Reported rather than silently substituted. Building an arena out of a different
            // block than asked for, without saying so, is the kind of quiet difference that
            // wastes a whole test run.
            ToolkitLog.error("Unknown arena construction block, using stone", blockId);
            sender.sendMessage(new TextComponentString(
                    "[DevToolkit] Unknown block '" + blockId + "', using minecraft:stone."));
            blockId = "minecraft:stone";
        }

        World world = player.world;
        ArenaRecord record = new ArenaRecord(
                (int) Math.floor(player.posX), (int) Math.floor(player.posY),
                (int) Math.floor(player.posZ),
                width, height, length, blockId, config.hasArenaCeiling());

        int changed = ArenaBuilder.build(world, record);
        ArenaStorage.get(world).setArena(record);

        ArenaGeometry geometry = record.geometry();
        teleport(player, geometry);

        LogRecord log = RecordContext.stamp(LogRecord.of(EventType.ARENA_CREATE), sender);
        SessionStamp.apply(log);
        addArenaFields(log, world, record);
        log.add("blocksChanged", changed);
        ToolkitLog.write(log);

        sender.sendMessage(new TextComponentString("[DevToolkit] Arena created: "
                + record.getSizeText() + ", " + changed + " blocks changed."));
    }

    // --- info ------------------------------------------------------------------------

    private void info(ICommandSender sender) {
        World world = sender.getEntityWorld();
        ArenaRecord record = ArenaStorage.get(world).getArena();
        if (record == null) {
            sender.sendMessage(new TextComponentString(
                    "[DevToolkit] No arena in dimension " + world.provider.getDimension() + "."));
            return;
        }

        ArenaGeometry geometry = record.geometry();
        sender.sendMessage(new TextComponentString("[DevToolkit] Arena in dimension "
                + world.provider.getDimension() + ": " + record.getSizeText()
                + " of " + record.getBlockId() + (record.hasCeiling() ? ", ceiling" : ", open top")));
        sender.sendMessage(new TextComponentString("  origin " + record.getOriginX() + " "
                + record.getOriginY() + " " + record.getOriginZ()
                + "   start " + geometry.getStartX() + " " + geometry.getStartY() + " "
                + geometry.getStartZ()));
        sender.sendMessage(new TextComponentString("  interior x " + geometry.getMinX() + ".."
                + geometry.getMaxX() + "  y " + geometry.getMinY() + ".." + geometry.getMaxY()
                + "  z " + geometry.getMinZ() + ".." + geometry.getMaxZ()));
    }

    // --- reset and clear -------------------------------------------------------------

    /**
     * Reset rebuilds the shell and lighting; clear only empties the interior.
     *
     * <p>Both remove non-player entities inside the bounds, and both are idempotent: running
     * either twice leaves the same state, which REQ-069 requires because a setup bundle may
     * run reset every time it executes.
     */
    private void resetOrClear(ICommandSender sender, boolean rebuild) throws CommandException {
        World world = sender.getEntityWorld();
        ArenaRecord record = ArenaStorage.get(world).getArena();
        if (record == null) {
            ToolkitLog.error("No arena in this dimension",
                    "dimension=" + world.provider.getDimension());
            sender.sendMessage(new TextComponentString(
                    "[DevToolkit] No arena in dimension " + world.provider.getDimension()
                            + ". Create one first."));
            return;
        }

        ArenaGeometry geometry = record.geometry();
        int removed = removeEntities(world, geometry);
        int changed = rebuild
                ? ArenaBuilder.build(world, record)
                : ArenaBuilder.clearInterior(world, record);

        int otherPlayers = countOtherPlayers(world, geometry, sender);

        if (rebuild && sender instanceof EntityPlayer) {
            teleport((EntityPlayer) sender, geometry);
        }

        LogRecord log = RecordContext.stamp(
                LogRecord.of(rebuild ? EventType.ARENA_RESET : EventType.ARENA_CLEAR), sender);
        SessionStamp.apply(log);
        addArenaFields(log, world, record);
        log.add("blocksChanged", changed);
        log.add("entitiesRemoved", removed);
        if (otherPlayers > 0) {
            // Out of scope to move them, per the issue. Noted so an unexplained player inside
            // the arena during a test is traceable rather than mysterious.
            log.add("otherPlayersInside", otherPlayers);
        }
        ToolkitLog.write(log);

        sender.sendMessage(new TextComponentString("[DevToolkit] Arena "
                + (rebuild ? "reset" : "cleared") + ": " + changed + " blocks, "
                + removed + " entities removed."
                + (otherPlayers > 0 ? " " + otherPlayers + " other player(s) inside." : "")));
    }

    /**
     * Removes every non-player entity within the bounds, dropped items included.
     *
     * <p>Players are never removed. Killing a bystanding player to tidy an arena would be a
     * far worse outcome than leaving them there.
     */
    private int removeEntities(World world, ArenaGeometry geometry) {
        int removed = 0;
        for (Entity entity : world.getEntitiesWithinAABB(Entity.class, bounds(geometry))) {
            if (entity instanceof EntityPlayer) {
                continue;
            }
            // The box is the cheap query; contains is the exact test. Same shape as the
            // nearby-entity listing, and it keeps the containment rule unit-testable.
            if (!geometry.contains(entity.posX, entity.posY, entity.posZ)) {
                continue;
            }
            entity.setDead();
            removed++;
        }
        return removed;
    }

    private int countOtherPlayers(World world, ArenaGeometry geometry, ICommandSender sender) {
        int count = 0;
        Entity self = sender.getCommandSenderEntity();
        for (EntityPlayer player
                : world.getEntitiesWithinAABB(EntityPlayer.class, bounds(geometry))) {
            if (player != self && geometry.contains(player.posX, player.posY, player.posZ)) {
                count++;
            }
        }
        return count;
    }

    /** The interior, as a box. Grown by one on the maximum side because block maxima are inclusive. */
    private static AxisAlignedBB bounds(ArenaGeometry geometry) {
        return new AxisAlignedBB(
                geometry.getMinX(), geometry.getMinY(), geometry.getMinZ(),
                geometry.getMaxX() + 1.0D, geometry.getMaxY() + 1.0D, geometry.getMaxZ() + 1.0D);
    }

    private void teleport(EntityPlayer player, ArenaGeometry geometry) {
        // Centred on the block rather than its corner, so the player does not end up half
        // inside a wall on a one-wide arena.
        double x = geometry.getStartX() + 0.5D;
        double z = geometry.getStartZ() + 0.5D;
        if (player instanceof EntityPlayerMP) {
            ((EntityPlayerMP) player).connection.setPlayerLocation(
                    x, geometry.getStartY(), z, player.rotationYaw, player.rotationPitch);
        } else {
            player.setPositionAndUpdate(x, geometry.getStartY(), z);
        }
    }

    private EntityPlayer requirePlayer(ICommandSender sender, String action) {
        if (sender instanceof EntityPlayer) {
            return (EntityPlayer) sender;
        }
        // REQ-005: fail explicitly. The arena is positioned relative to the caller, and
        // defaulting to the world origin would build it somewhere nobody asked for.
        ToolkitLog.error("arena " + action + " requires a player sender");
        sender.sendMessage(new TextComponentString(
                "[DevToolkit] /devtool arena " + action + " requires a player sender."));
        return null;
    }

    private static void addArenaFields(LogRecord log, World world, ArenaRecord record) {
        ArenaGeometry geometry = record.geometry();
        log.add("dimension", world.provider.getDimension());
        log.addBlockPos("origin", record.getOriginX(), record.getOriginY(), record.getOriginZ());
        log.add("width", record.getWidth());
        log.add("height", record.getHeight());
        log.add("length", record.getLength());
        log.addBlockPos("min", geometry.getMinX(), geometry.getMinY(), geometry.getMinZ());
        log.addBlockPos("max", geometry.getMaxX(), geometry.getMaxY(), geometry.getMaxZ());
        log.addBlockPos("start", geometry.getStartX(), geometry.getStartY(), geometry.getStartZ());
        log.add("block", record.getBlockId());
        log.add("ceiling", record.hasCeiling());
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
