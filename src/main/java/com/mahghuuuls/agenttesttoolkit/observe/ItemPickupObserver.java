package com.mahghuuuls.agenttesttoolkit.observe;

import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.LoggingCategory;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.SessionStamp;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/**
 * Observes a player picking an item up.
 *
 * <p>FML's pickup event fires on the server once the stack is in the inventory, with a copy
 * whose count is the amount that moved: a pickup that only partly fits says what entered, and
 * the rest is picked up later with its own record. A pickup removes the item entity, so an
 * entity count right after it reads zero; this record is the evidence instead. Filtered on the
 * item entity's position, where the item was.
 */
public final class ItemPickupObserver {

    @SubscribeEvent
    public void onItemPickup(PlayerEvent.ItemPickupEvent event) {
        EntityPlayer player = event.player;
        EntityItem item = event.getOriginalEntity();
        ItemStack stack = event.getStack();
        if (player == null || item == null || stack == null || stack.isEmpty() || player.world == null) {
            return;
        }
        if (!ObserverGate.shouldRecord(LoggingCategory.ITEM_PICKUP, player.world.isRemote,
                player.world.provider.getDimension(), item.posX, item.posY, item.posZ)) {
            return;
        }
        LogRecord record = RecordContext.stamp(
                LogRecord.of(LoggingCategory.ITEM_PICKUP.getEventType()), RecordContext.snapshot(player.world));
        SessionStamp.apply(record);
        record.add("player", player.getName());
        record.add("playerId", player.getEntityId());
        ResourceLocation id = Item.REGISTRY.getNameForObject(stack.getItem());
        record.add("item", id == null ? null : id.toString());
        record.add("meta", stack.getMetadata());
        record.add("count", stack.getCount());
        record.add("entityId", item.getEntityId());
        record.addEntityPos("pos", item.posX, item.posY, item.posZ);
        ToolkitLog.write(record);
    }
}
