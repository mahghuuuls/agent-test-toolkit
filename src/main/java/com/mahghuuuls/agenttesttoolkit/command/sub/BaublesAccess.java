package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.integration.BaublesBridge;
import com.mahghuuuls.agenttesttoolkit.integration.Integrations;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import net.minecraft.command.CommandException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * The one way command code reaches the Baubles bridge, so that every caller writes the same
 * record when the installed provider cannot be driven.
 *
 * <p>The bridge reports such a provider as {@code devtool.baubles.unsupported}, naming the method
 * that failed, but may not write records itself. A bundle's failure record carries only the key,
 * so the method would be lost; the bridge returned here writes an {@code ERROR} record with it as
 * {@code detail} before the failure goes on. A test checks that nothing else asks
 * {@code Integrations} for the bridge.
 */
final class BaublesAccess {

    private BaublesAccess() {
    }

    /** The bridge, recording an unsupported provider; null when Baubles is not installed. */
    static BaublesBridge bridge() {
        BaublesBridge bridge = Integrations.baubles();
        return bridge == null ? null : new Recording(bridge);
    }

    /** Passes every call through; a call that finds the provider unsupported is recorded on its way out. */
    static final class Recording implements BaublesBridge {
        private final BaublesBridge bridge;

        Recording(BaublesBridge bridge) {
            this.bridge = bridge;
        }

        @Override
        public int slotCount(EntityPlayer player) {
            return bridge.slotCount(player);
        }

        @Override
        public String slotType(EntityPlayer player, int slot) throws CommandException {
            try {
                return bridge.slotType(player, slot);
            } catch (CommandException e) {
                throw recorded(e);
            }
        }

        @Override
        public ItemStack get(EntityPlayer player, int slot) {
            return bridge.get(player, slot);
        }

        @Override
        public boolean accepts(EntityPlayer player, int slot, ItemStack stack) {
            return bridge.accepts(player, slot, stack);
        }

        @Override
        public void set(EntityPlayer player, int slot, ItemStack stack) throws CommandException {
            try {
                bridge.set(player, slot, stack);
            } catch (CommandException e) {
                throw recorded(e);
            }
        }

        private static CommandException recorded(CommandException e) {
            if (KEY_UNSUPPORTED.equals(e.getMessage())) {
                Object[] objects = e.getErrorObjects();
                ToolkitLog.error("Baubles provider cannot be driven",
                        objects == null || objects.length == 0 ? null : String.valueOf(objects[0]));
            }
            return e;
        }
    }
}
