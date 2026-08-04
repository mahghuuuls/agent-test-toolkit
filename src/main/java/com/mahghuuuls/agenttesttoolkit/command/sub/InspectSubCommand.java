package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.command.SubCommand;
import com.mahghuuuls.agenttesttoolkit.inspect.EntityTarget;
import com.mahghuuuls.agenttesttoolkit.inspect.Inspectors;
import com.mahghuuuls.agenttesttoolkit.inspect.Inventories;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Reports current generic state for a player, entity, or block.
 *
 * <p>Detail goes to the log and chat receives a short confirmation, per REQ-078. The log is
 * the evidence channel an agent reads; flooding chat with dozens of fields would help nobody
 * and would push the useful part off screen.
 */
public final class InspectSubCommand implements SubCommand {

    /**
     * The inspection targets this build actually accepts.
     *
     * <p>Public because the capabilities command reports it. Read from here rather than
     * restated there, so the two cannot drift: a target added below appears in capabilities
     * without anyone remembering to update it.
     */
    public static final List<String> TARGETS =
            Collections.unmodifiableList(Arrays.asList("player", "entity", "block", "inventory"));

    @Override
    public String getName() {
        return "inspect";
    }

    @Override
    public String getDescription() {
        return "Report current state of a player, entity or block";
    }

    @Override
    public String getUsage() {
        return "inspect <player [selector]|entity <selector>|block <x> <y> <z>>";
    }

    @Override
    public boolean requiresPlayer() {
        // The subcommands vary: 'player' with no selector and 'block' with relative
        // coordinates need a player, but an explicit selector or absolute coordinates do not.
        // Enforced per target below rather than blanket-refusing the console.
        return false;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            throw new WrongUsageException("/devtool " + getUsage());
        }

        String target = args[0].toLowerCase(Locale.ROOT);
        String[] rest = args.length <= 1 ? new String[0] : Arrays.copyOfRange(args, 1, args.length);

        if ("player".equals(target)) {
            inspectPlayer(server, sender, rest);
        } else if ("entity".equals(target)) {
            inspectEntity(server, sender, rest);
        } else if ("block".equals(target)) {
            inspectBlock(sender, rest);
        } else if ("inventory".equals(target)) {
            inspectInventory(server, sender, rest);
        } else {
            ToolkitLog.error("Unknown inspect target", target);
            sender.sendMessage(new TextComponentString(
                    "[DevToolkit] Unknown inspect target: " + target
                            + ". Expected " + TARGETS + "."));
        }
    }

    private void inspectPlayer(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        EntityPlayer player;
        if (args.length == 0) {
            if (!(sender instanceof EntityPlayer)) {
                // REQ-005: fail explicitly rather than obscurely. A console caller has no
                // implicit self to inspect.
                ToolkitLog.error("inspect player requires a selector when run without a player sender");
                sender.sendMessage(new TextComponentString(
                        "[DevToolkit] /devtool inspect player requires a selector when run from the console."));
                return;
            }
            player = (EntityPlayer) sender;
        } else {
            Entity entity = resolveOrReport(server, sender, args[0]);
            if (entity == null) {
                return;
            }
            if (!(entity instanceof EntityPlayer)) {
                ToolkitLog.error("Selector did not match a player", args[0]);
                sender.sendMessage(new TextComponentString(
                        "[DevToolkit] Selector matched a non-player entity. Use /devtool inspect entity."));
                return;
            }
            player = (EntityPlayer) entity;
        }
        emit(Inspectors.player(player), sender, "Player inspection written to log.");
    }

    /**
     * REQ-074. Resolves the same way as {@code inspect player}: self when no selector, or the
     * named player.
     */
    private void inspectInventory(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        EntityPlayer player;
        if (args.length == 0) {
            if (!(sender instanceof EntityPlayer)) {
                ToolkitLog.error("inspect inventory requires a selector when run without a player sender");
                sender.sendMessage(new TextComponentString(
                        "[DevToolkit] /devtool inspect inventory requires a selector when run from the console."));
                return;
            }
            player = (EntityPlayer) sender;
        } else {
            Entity entity = resolveOrReport(server, sender, args[0]);
            if (entity == null) {
                return;
            }
            if (!(entity instanceof EntityPlayer)) {
                ToolkitLog.error("Selector did not match a player", args[0]);
                sender.sendMessage(new TextComponentString(
                        "[DevToolkit] Selector matched a non-player entity; only players have inventories here."));
                return;
            }
            player = (EntityPlayer) entity;
        }

        // One record per occupied slot, so the confirmation is sent once rather than per line.
        List<LogRecord> records = Inventories.inventory(player);
        for (LogRecord record : records) {
            write(record, sender);
        }
        sender.sendMessage(new TextComponentString("[DevToolkit] Inventory written to log ("
                + records.size() + " record" + (records.size() == 1 ? "" : "s") + ")."));
    }

    private void inspectEntity(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (args.length == 0) {
            throw new WrongUsageException("/devtool inspect entity <selector>");
        }
        Entity entity = resolveOrReport(server, sender, args[0]);
        if (entity == null) {
            return;
        }
        emit(Inspectors.entity(entity), sender, "Entity inspection written to log.");
    }

    private void inspectBlock(ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 3) {
            throw new WrongUsageException("/devtool inspect block <x> <y> <z>");
        }

        // A relative coordinate is resolved against the sender's position. A console sender
        // has no meaningful position: its default is the world origin, so `~ ~ ~` would not
        // fail, it would quietly report the block at 0,0,0 as though that were what was
        // asked for. That is the exact failure this issue's observability contract warns
        // about, a plausible record with a systematically wrong field, so relative
        // coordinates require a sender that genuinely has a position.
        if (isRelative(args) && sender.getCommandSenderEntity() == null) {
            ToolkitLog.error("Relative coordinates require an entity sender",
                    "x=" + args[0] + " y=" + args[1] + " z=" + args[2]);
            sender.sendMessage(new TextComponentString(
                    "[DevToolkit] Relative coordinates need a player sender. Use absolute coordinates."));
            return;
        }

        BlockPos pos = CommandBase.parseBlockPos(sender, args, 0, false);
        emit(Inspectors.block(sender.getEntityWorld(), pos), sender, "Block inspection written to log.");
    }

    /**
     * True when any of the three coordinate arguments is relative.
     *
     * <p>Public so it can be tested directly. It is pure string handling with no Minecraft
     * dependency, which makes it one of the few genuinely unit-testable pieces of this
     * command, and it guards a defect that produced silently wrong records.
     */
    public static boolean isRelative(String[] args) {
        for (int i = 0; i < 3 && i < args.length; i++) {
            if (args[i] != null && args[i].startsWith("~")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves exactly one entity, reporting a cardinality failure rather than throwing it as
     * a usage error, so the explicit counts required by REQ-070 reach both the log and chat.
     */
    private Entity resolveOrReport(MinecraftServer server, ICommandSender sender, String selector)
            throws CommandException {
        try {
            return EntityTarget.requireExactlyOne(server, sender, selector);
        } catch (EntityTarget.CardinalityException e) {
            ToolkitLog.error(e.getMessage(), "selector=" + selector);
            sender.sendMessage(new TextComponentString("[DevToolkit] " + e.getMessage()));
            return null;
        }
    }

    private void emit(LogRecord record, ICommandSender sender, String chatConfirmation) {
        // Context fields trail the identity fields on inspection records, where they lead on
        // MARK and session records. REQ-033 requires order to be consistent per event type,
        // which holds either way, and building the identity first keeps the inspector free of
        // command-layer types. Noted so the difference reads as deliberate rather than sloppy.
        write(record, sender);
        sender.sendMessage(new TextComponentString("[DevToolkit] " + chatConfirmation));
    }

    /** Stamps and writes one record without sending chat, for multi-record inspections. */
    private void write(LogRecord record, ICommandSender sender) {
        RecordContext.stamp(record, sender);
        SessionStamp.apply(record);
        ToolkitLog.write(record);
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length <= 1) {
            return CommandBase.getListOfStringsMatchingLastWord(args, TARGETS);
        }
        return Collections.emptyList();
    }
}
