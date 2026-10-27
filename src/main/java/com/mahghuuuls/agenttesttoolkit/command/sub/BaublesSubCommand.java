package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.command.Senders;
import com.mahghuuuls.agenttesttoolkit.command.SubCommand;
import com.mahghuuuls.agenttesttoolkit.integration.BaublesBridge;
import com.mahghuuuls.agenttesttoolkit.integration.Integrations;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.SessionStamp;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;

import java.util.Locale;

/**
 * Sets and clears bauble slots through Baubles' own handler (REQ-190).
 *
 * <p>Never forces: an item Baubles would not accept in a slot is refused with
 * {@code devtool.baubles.rejected}. Without Baubles both forms fail with
 * {@code devtool.baubles.absent}, so a bundle written for Baubles says why it stopped.
 */
public final class BaublesSubCommand implements SubCommand {

    public static final String KEY_ABSENT = "devtool.baubles.absent";
    public static final String KEY_REJECTED = "devtool.baubles.rejected";

    @Override
    public String getName() {
        return "baubles";
    }

    @Override
    public String getDescription() {
        return "Set or clear bauble slots, when Baubles is installed";
    }

    @Override
    public String getUsage() {
        return "baubles <set <slot> <item> [count] [meta] [player] | clear <slot|all> [player]>";
    }

    @Override
    public boolean requiresPlayer() {
        // A named player is enough; checked below.
        return false;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        BaublesBridge baubles = Integrations.baubles();
        if (baubles == null) {
            ToolkitLog.error("Baubles is not installed", args.length == 0 ? null : args[0]);
            throw new CommandException(KEY_ABSENT, "devtool baubles");
        }
        if (args.length < 2) {
            throw new WrongUsageException("/devtool " + getUsage());
        }
        String form = args[0].toLowerCase(Locale.ROOT);
        if ("set".equals(form)) {
            set(server, sender, args, baubles);
        } else if ("clear".equals(form)) {
            clear(server, sender, args, baubles);
        } else {
            throw new WrongUsageException("/devtool " + getUsage());
        }
    }

    private static EntityPlayer target(MinecraftServer server, ICommandSender sender, String[] args, int index)
            throws CommandException {
        if (args.length > index) {
            return CommandBase.getPlayer(server, sender, args[index]);
        }
        EntityPlayer self = Senders.asPlayer(sender);
        if (self == null) {
            throw new CommandException("[DevToolkit] baubles needs a player: name one, or run it as a player.");
        }
        return self;
    }

    private static int slot(String text, EntityPlayer player, BaublesBridge baubles) throws CommandException {
        return CommandBase.parseInt(text, 0, Math.max(0, baubles.slotCount(player) - 1));
    }

    private void set(MinecraftServer server, ICommandSender sender, String[] args, BaublesBridge baubles)
            throws CommandException {
        if (args.length < 3 || args.length > 6) {
            throw new WrongUsageException("/devtool baubles set <slot> <item> [count] [meta] [player]");
        }
        EntityPlayer player = target(server, sender, args, 5);
        int slot = slot(args[1], player, baubles);
        Item item = CommandBase.getItemByText(sender, args[2]);
        int count = args.length > 3 ? CommandBase.parseInt(args[3], 1, 64) : 1;
        int meta = args.length > 4 ? CommandBase.parseInt(args[4], 0) : 0;
        ItemStack stack = new ItemStack(item, count, meta);

        LogRecord record = begin(sender, "set", player, slot, baubles);
        record.add("item", id(item)).add("count", count).add("meta", meta);
        ItemStack previous = baubles.get(player, slot);
        if (!previous.isEmpty()) {
            // Replacing is allowed, but never silent.
            record.add("replaced", id(previous.getItem()));
        }
        // A bauble slot holds one item, as its GUI does; an empty stack is a clear, not a set.
        boolean acceptable = !stack.isEmpty() && count == 1 && baubles.accepts(player, slot, stack);
        if (!acceptable) {
            record.add("outcome", "rejected");
            ToolkitLog.write(record);
            throw new CommandException(KEY_REJECTED, id(item) + " in " + baubles.slotType(slot) + " slot " + slot);
        }
        baubles.set(player, slot, stack);
        record.add("outcome", "set");
        ToolkitLog.write(record);
        sender.sendMessage(new TextComponentString("[DevToolkit] Bauble slot " + slot + " set to " + id(item) + "."));
    }

    private void clear(MinecraftServer server, ICommandSender sender, String[] args, BaublesBridge baubles)
            throws CommandException {
        if (args.length > 3) {
            throw new WrongUsageException("/devtool baubles clear <slot|all> [player]");
        }
        EntityPlayer player = target(server, sender, args, 2);
        int from;
        int to;
        if ("all".equalsIgnoreCase(args[1])) {
            from = 0;
            to = baubles.slotCount(player) - 1;
        } else {
            from = slot(args[1], player, baubles);
            to = from;
        }
        int cleared = 0;
        for (int slot = from; slot <= to; slot++) {
            if (!baubles.get(player, slot).isEmpty()) {
                baubles.set(player, slot, ItemStack.EMPTY);
                cleared++;
            }
        }
        // Empty slots are a success: a teardown runs clear all whatever is equipped.
        LogRecord record = RecordContext.stamp(LogRecord.of(EventType.BAUBLES), sender);
        SessionStamp.apply(record);
        record.add("action", "clear").add("player", player.getName())
                .add("slots", "all".equalsIgnoreCase(args[1]) ? "all" : Integer.toString(from))
                .add("cleared", cleared).add("outcome", "cleared");
        ToolkitLog.write(record);
        sender.sendMessage(new TextComponentString("[DevToolkit] Bauble slots cleared: " + cleared + "."));
    }

    private static LogRecord begin(ICommandSender sender, String action, EntityPlayer player, int slot,
                                   BaublesBridge baubles) {
        LogRecord record = RecordContext.stamp(LogRecord.of(EventType.BAUBLES), sender);
        SessionStamp.apply(record);
        return record.add("action", action).add("player", player.getName())
                .add("slot", slot).add("slotType", baubles.slotType(slot));
    }

    private static String id(Item item) {
        ResourceLocation id = Item.REGISTRY.getNameForObject(item);
        return id == null ? "unknown" : id.toString();
    }
}
