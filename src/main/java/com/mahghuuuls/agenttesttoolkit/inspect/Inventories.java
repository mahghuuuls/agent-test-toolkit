package com.mahghuuuls.agenttesttoolkit.inspect;

import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;

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

    /** One occupied slot, captured before any record is built. */
    private static final class Slot {
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
