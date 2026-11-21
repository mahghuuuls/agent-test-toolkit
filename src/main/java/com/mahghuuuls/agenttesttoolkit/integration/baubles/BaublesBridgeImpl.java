package com.mahghuuuls.agenttesttoolkit.integration.baubles;

import baubles.api.BaubleType;
import baubles.api.BaublesApi;
import baubles.api.IBauble;
import baubles.api.cap.BaublesCapabilities;
import baubles.api.cap.IBaublesItemHandler;
import com.mahghuuuls.agenttesttoolkit.integration.BaublesBridge;
import com.mahghuuuls.agenttesttoolkit.integration.Integrations;
import net.minecraft.command.CommandException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The only class compiled against Baubles, so the rest of the toolkit loads without it. Loaded
 * by name, and only when Baubles is installed; see {@code Integrations}.
 *
 * <p>Calls directly only the handler methods every provider of {@code baubles.api} has; the
 * others go through {@link ProviderMethods}, looked up once here, because official Baubles and
 * Bubbles differ in them.
 */
public final class BaublesBridgeImpl implements BaublesBridge {

    private final Map<String, int[]> types = new LinkedHashMap<String, int[]>();
    private final ProviderMethods methods;

    public BaublesBridgeImpl() {
        for (BaubleType type : BaubleType.values()) {
            types.put(type.name().toLowerCase(Locale.ROOT), type.getValidSlots());
        }
        methods = ProviderMethods.probe(IBaublesItemHandler.class);
    }

    private static IBaublesItemHandler handler(EntityPlayer player) {
        return player == null ? null : BaublesApi.getBaublesHandler(player);
    }

    @Override
    public int slotCount(EntityPlayer player) {
        IBaublesItemHandler handler = handler(player);
        return handler == null ? 0 : handler.getSlots();
    }

    /**
     * The provider's own type for the slot when it has one; otherwise the types' valid slots,
     * which a provider that names types per slot may leave meaningless (all negative).
     */
    @Override
    public String slotType(EntityPlayer player, int slot) throws CommandException {
        IBaublesItemHandler handler = handler(player);
        String named = handler == null ? null : methods.slotTypeName(handler, slot);
        return named != null ? named : Integrations.slotTypeName(slot, types);
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
     * the old bauble is told it was unequipped, then the new one that it was equipped, each at
     * most once, as {@link BaubleEquipCalls} decides. A mod under test that grants bonuses on
     * equip therefore behaves as it would for a player.
     * Baubles' own {@code /baubles} command skips these calls; this does not.
     */
    @Override
    public void set(final EntityPlayer player, final int slot, ItemStack stack) throws CommandException {
        final IBaublesItemHandler handler = handler(player);
        if (handler == null) {
            return;
        }
        // Read before anything changes, so a provider whose flag fails leaves the slot as it was.
        boolean blocked = methods.eventsBlocked(handler);
        BaubleEquipCalls.apply(new BaubleEquipCalls.Slot<ItemStack>() {
            @Override
            public ItemStack get() {
                return handler.getStackInSlot(slot);
            }

            @Override
            public void put(ItemStack stack) {
                handler.setStackInSlot(slot, stack);
            }

            @Override
            public boolean isEmpty(ItemStack stack) {
                return stack.isEmpty();
            }

            @Override
            public boolean equal(ItemStack a, ItemStack b) {
                return ItemStack.areItemStacksEqual(a, b);
            }

            @Override
            public void unequipped(ItemStack stack) {
                IBauble bauble = stack.getCapability(BaublesCapabilities.CAPABILITY_ITEM_BAUBLE, null);
                if (bauble != null) {
                    bauble.onUnequipped(stack, player);
                }
            }

            @Override
            public void equipped(ItemStack stack) {
                IBauble bauble = stack.getCapability(BaublesCapabilities.CAPABILITY_ITEM_BAUBLE, null);
                if (bauble != null) {
                    bauble.onEquipped(stack, player);
                }
            }
        }, stack, blocked);
    }
}
