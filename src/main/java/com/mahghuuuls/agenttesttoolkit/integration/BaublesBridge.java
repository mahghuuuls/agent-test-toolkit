package com.mahghuuuls.agenttesttoolkit.integration;

import net.minecraft.command.CommandException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * What the toolkit needs from Baubles, in Minecraft types only (ARC-018).
 *
 * <p>Callers never see a Baubles type, so nothing outside {@code integration/baubles} loads one,
 * and the toolkit runs unchanged without Baubles. Knows nothing about what a bauble does.
 */
public interface BaublesBridge {

    /**
     * The failure of a Baubles provider the toolkit cannot drive: one of its methods failed when
     * called. The command fails with this key, and the message names the method.
     */
    String KEY_UNSUPPORTED = "devtool.baubles.unsupported";

    /** The number of bauble slots the player has. */
    int slotCount(EntityPlayer player);

    /**
     * The slot's type, lower case, such as {@code ring}: as the provider names this player's
     * slot when it does (another mod's type in full, such as {@code somemod:wing}), otherwise
     * from the types that accept the slot; {@code unknown} when neither says.
     */
    String slotType(EntityPlayer player, int slot) throws CommandException;

    /** The stack in the slot; empty when nothing is there. */
    ItemStack get(EntityPlayer player, int slot);

    /** Whether Baubles itself accepts this stack in this slot for this player. */
    boolean accepts(EntityPlayer player, int slot, ItemStack stack);

    /**
     * Puts the stack in the slot, telling the old and new bauble they were unequipped and
     * equipped, as the Baubles screen does, once each. The caller has checked {@link #accepts}
     * first.
     */
    void set(EntityPlayer player, int slot, ItemStack stack) throws CommandException;
}
