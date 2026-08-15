package com.mahghuuuls.agenttesttoolkit.inspect;

import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Reports a player's occupied inventory slots.
 *
 * <p>Empty slots are omitted: a creative-mode player carries 41 slots and listing the
 * empty ones would bury the three that matter under noise, which is the opposite of what a
 * machine-readable log is for.
 *
 * <p>One record per occupied slot rather than one joined record. A joined value would be a
 * single very long line, and the per-slot form matches how nearby entity listing reports.
 * Every record carries {@code occupiedSlots}, so a line read in isolation still says how many
 * there were in total and a partial read is detectable.
 *
 * <p>NBT presence is indicated but never expanded here. The full tag can be enormous; that is
 * what the separate NBT command is for, and mixing the two would make an inventory listing
 * unpredictably large.
 */
public final class Inventories {

    /**
     * Most slots reported for one container.
     *
     * <p>A vanilla double chest is 54 and a large modded container can be far more. The cap
     * bounds a single inspection rather than the log as a whole; a listing that floods the file
     * makes the surrounding records unreadable, which is the same reason spawn logging is
     * filtered rather than unlimited.
     */
    public static final int MAX_CONTAINER_SLOTS = 128;

    /** Slot groupings, named so an agent can tell a hotbar slot from a boot. */
    public static final String SECTION_MAIN = "main";
    public static final String SECTION_ARMOR = "armor";
    public static final String SECTION_OFFHAND = "offhand";

    private Inventories() {
    }

    public static List<LogRecord> inventory(EntityPlayer player) {
        List<Slot> slots = new ArrayList<Slot>();
        collect(slots, SECTION_MAIN, player.inventory.mainInventory);
        collect(slots, SECTION_ARMOR, player.inventory.armorInventory);
        collect(slots, SECTION_OFFHAND, player.inventory.offHandInventory);

        List<LogRecord> records = new ArrayList<LogRecord>();
        if (slots.isEmpty()) {
            // An empty inventory still produces one record. Emitting nothing would be
            // indistinguishable from the command failing to run, which is exactly the
            // ambiguity an explicit zero exists to remove.
            records.add(base(player).add("occupiedSlots", 0));
            return records;
        }

        for (Slot slot : slots) {
            LogRecord record = base(player)
                    .add("occupiedSlots", slots.size())
                    .add("section", slot.section)
                    .add("slot", slot.index)
                    .add("item", slot.itemId)
                    .add("count", slot.count)
                    .add("meta", slot.meta);
            if (slot.hasNbt) {
                record.add("hasNbt", true);
            }
            records.add(record);
        }
        return records;
    }

    /**
     * Reports a container's occupied slots, in the same shape as a player's inventory.
     *
     * <p>Contents were always obtainable through the NBT command, which writes the tile
     * entity's full tag. This exists because that tag is one blob subject to truncation, while
     * these records are per slot and parseable, which is what the log is for.
     *
     * <p>Bounded at {@link #MAX_CONTAINER_SLOTS}. A modded container can carry hundreds of
     * slots, and an inspection that floods the log defeats the purpose of inspecting.
     * Truncation is reported rather than silent, in the same way the NBT command reports it.
     *
     * @param blockId the container's registry name, for attribution
     * @param slots   occupied slots already collected from whichever interface the block exposes
     */
    public static List<LogRecord> container(String blockId, BlockPos pos, List<Slot> slots) {
        List<LogRecord> records = new ArrayList<LogRecord>();
        if (slots.isEmpty()) {
            // Same reason as an empty player inventory: emitting nothing would be
            // indistinguishable from the command not running.
            records.add(containerBase(blockId, pos).add("occupiedSlots", 0));
            return records;
        }

        boolean truncated = slots.size() > MAX_CONTAINER_SLOTS;
        int reported = truncated ? MAX_CONTAINER_SLOTS : slots.size();

        for (int i = 0; i < reported; i++) {
            Slot slot = slots.get(i);
            LogRecord record = containerBase(blockId, pos)
                    .add("occupiedSlots", slots.size())
                    .add("slot", slot.index)
                    .add("item", slot.itemId)
                    .add("count", slot.count)
                    .add("meta", slot.meta);
            if (slot.hasNbt) {
                record.add("hasNbt", true);
            }
            if (truncated) {
                // Carried on every record rather than only the last, so a line read in
                // isolation still says the listing is incomplete.
                record.add("truncated", true).add("reportedSlots", reported);
            }
            records.add(record);
        }
        return records;
    }

    /** Collects occupied slots from a vanilla inventory. */
    public static List<Slot> collectFrom(IInventory inventory) {
        List<Slot> slots = new ArrayList<Slot>();
        if (inventory == null) {
            return slots;
        }
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            add(slots, i, inventory.getStackInSlot(i));
        }
        return slots;
    }

    /** Collects occupied slots from a Forge item handler, which many modded blocks expose. */
    public static List<Slot> collectFrom(IItemHandler handler) {
        List<Slot> slots = new ArrayList<Slot>();
        if (handler == null) {
            return slots;
        }
        for (int i = 0; i < handler.getSlots(); i++) {
            add(slots, i, handler.getStackInSlot(i));
        }
        return slots;
    }

    private static void add(List<Slot> into, int index, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ResourceLocation id = Item.REGISTRY.getNameForObject(stack.getItem());
        into.add(new Slot(null, index, id == null ? null : id.toString(),
                stack.getCount(), stack.getMetadata(), stack.hasTagCompound()));
    }

    private static LogRecord containerBase(String blockId, BlockPos pos) {
        LogRecord record = LogRecord.of(EventType.INVENTORY_INSPECT).add("block", blockId);
        record.addBlockPos("pos", pos.getX(), pos.getY(), pos.getZ());
        return record;
    }

    private static LogRecord base(EntityPlayer player) {
        return LogRecord.of(EventType.INVENTORY_INSPECT).add("player", player.getName());
    }

    private static void collect(List<Slot> into, String section, NonNullList<ItemStack> stacks) {
        if (stacks == null) {
            return;
        }
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            ResourceLocation id = Item.REGISTRY.getNameForObject(stack.getItem());
            into.add(new Slot(section, i, id == null ? null : id.toString(),
                    stack.getCount(), stack.getMetadata(), stack.hasTagCompound()));
        }
    }

    /**
     * One occupied slot, captured before any record is built.
     *
     * <p>Public because container collection happens against whichever interface a block
     * exposes, and the caller decides which. The capture is deliberately separated from record
     * building so the two collection paths produce the same thing.
     */
    public static final class Slot {
        private final String section;
        private final int index;
        private final String itemId;
        private final int count;
        private final int meta;
        private final boolean hasNbt;

        Slot(String section, int index, String itemId, int count, int meta, boolean hasNbt) {
            this.section = section;
            this.index = index;
            this.itemId = itemId;
            this.count = count;
            this.meta = meta;
            this.hasNbt = hasNbt;
        }
    }
}
