package com.mahghuuuls.agenttesttoolkit.integration.baubles;

import baubles.api.BaubleType;
import baubles.api.BaublesApi;
import baubles.api.IBauble;
import baubles.api.cap.BaublesCapabilities;
import baubles.api.cap.IBaublesItemHandler;
import com.mahghuuuls.agenttesttoolkit.integration.BaublesBridge;
import com.mahghuuuls.agenttesttoolkit.integration.Integrations;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The only class that talks to Baubles (rule 11). Loaded by name, and only when Baubles is
 * installed; see {@code Integrations}.
 */
public final class BaublesBridgeImpl implements BaublesBridge {

    private final Map<String, int[]> types = new LinkedHashMap<String, int[]>();

    public BaublesBridgeImpl() {
        for (BaubleType type : BaubleType.values()) {
            types.put(type.name().toLowerCase(Locale.ROOT), type.getValidSlots());
        }
    }

    private static IBaublesItemHandler handler(EntityPlayer player) {
        return player == null ? null : BaublesApi.getBaublesHandler(player);
    }

    @Override
    public int slotCount(EntityPlayer player) {
        IBaublesItemHandler handler = handler(player);
        return handler == null ? 0 : handler.getSlots();
    }

    @Override
    public String slotType(int slot) {
        return Integrations.slotTypeName(slot, types);
    }

    @Override
    public ItemStack get(EntityPlayer player, int slot) {
        IBaublesItemHandler handler = handler(player);
        return handler == null || slot < 0 || slot >= handler.getSlots()
                ? ItemStack.EMPTY : handler.getStackInSlot(slot);
    }

    @Override
    public boolean accepts(EntityPlayer player, int slot, ItemStack stack) {
        IBaublesItemHandler handler = handler(player);
        return handler != null && slot >= 0 && slot < handler.getSlots()
                && (stack.isEmpty() || handler.isItemValidForSlot(slot, stack, player));
    }

    /**
     * Puts the stack in the slot the way the Baubles screen does ({@code SlotBauble.putStack}):
     * the old bauble is told it was unequipped, then the new one that it was equipped, unless
     * Baubles has its events blocked. A mod under test that grants bonuses on equip therefore
     * behaves as it would for a player (owner decision 2026-10-27, option A). Baubles' own
     * {@code /baubles} command skips these calls; this does not.
     */
    @Override
    public void set(EntityPlayer player, int slot, ItemStack stack) {
        IBaublesItemHandler handler = handler(player);
        if (handler == null) {
            return;
        }
        boolean events = !handler.isEventBlocked();
        ItemStack previous = handler.getStackInSlot(slot);
        if (events && !previous.isEmpty()) {
            IBauble old = previous.getCapability(BaublesCapabilities.CAPABILITY_ITEM_BAUBLE, null);
            if (old != null) {
                old.onUnequipped(previous, player);
            }
        }
        handler.setStackInSlot(slot, stack);
        if (events && !stack.isEmpty()) {
            IBauble now = stack.getCapability(BaublesCapabilities.CAPABILITY_ITEM_BAUBLE, null);
            if (now != null) {
                now.onEquipped(stack, player);
            }
        }
    }
}
