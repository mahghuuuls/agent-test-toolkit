package com.mahghuuuls.agenttesttoolkit.integration;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * What the toolkit needs from Baubles, in Minecraft types only (ARC-018).
 *
 * <p>Callers never see a Baubles type, so nothing outside {@code integration/baubles} loads one,
 * and the toolkit runs unchanged without Baubles. Knows nothing about what a bauble does.
 */
public interface BaublesBridge {

    /** The number of bauble slots the player has. */
    int slotCount(EntityPlayer player);

    /** The slot's type, lower case, such as {@code ring}. */
    String slotType(int slot);

    /** The stack in the slot; empty when nothing is there. */
    ItemStack get(EntityPlayer player, int slot);

    /** Whether Baubles itself accepts this stack in this slot for this player. */
    boolean accepts(EntityPlayer player, int slot, ItemStack stack);

    /**
     * Puts the stack in the slot, telling the old and new bauble they were unequipped and
     * equipped, as the Baubles screen does. The caller has checked {@link #accepts} first.
     */
    void set(EntityPlayer player, int slot, ItemStack stack);
}
