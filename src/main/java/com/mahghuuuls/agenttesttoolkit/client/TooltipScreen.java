package com.mahghuuuls.agenttesttoolkit.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

/**
 * The player's inventory, drawn as the game draws it (without the recipe book button and the
 * potion effects beside it), with the tooltip of one slot shown as if the mouse were over it,
 * and nothing that the game's own click handling could change.
 *
 * <p>It is a container screen of its own over the player's inventory container rather than the
 * game's inventory screen, because that one replaces itself with the creative inventory in
 * creative, which shows no player slots. The mouse position the game draws with is the slot's
 * centre, so the hover highlight and the tooltip, and every event a mod hooks into them, come
 * from the game's own drawing; the real cursor does not move.
 */
final class TooltipScreen extends GuiContainer {

    private final EntityPlayer player;
    private final int inventoryIndex;
    private final boolean advanced;
    /** Modifier keys read as held while the tooltip is built and drawn. */
    private final int[] keys;
    /** Which modifiers the game read as held while the lines were built; for the record. */
    private String keysSeen = "";

    TooltipScreen(EntityPlayer player, int inventoryIndex, boolean advanced, int[] keys) {
        super(player.inventoryContainer);
        this.player = player;
        this.inventoryIndex = inventoryIndex;
        this.advanced = advanced;
        this.keys = keys;
    }

    /** The slot of the player's inventory at that index, or null. */
    private Slot slot() {
        for (Slot slot : inventorySlots.inventorySlots) {
            if (slot.inventory == player.inventory && slot.getSlotIndex() == inventoryIndex) {
                return slot;
            }
        }
        return null;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        Slot slot = slot();
        int x = slot == null ? mouseX : guiLeft + slot.xPos + 8;
        int y = slot == null ? mouseY : guiTop + slot.yPos + 8;
        drawDefaultBackground();
        super.drawScreen(x, y, partialTicks);
        final int tipX = x;
        final int tipY = y;
        try {
            ModifierKeys.during(keys, new java.util.concurrent.Callable<Void>() {
                @Override
                public Void call() {
                    renderHoveredToolTip(tipX, tipY);
                    return null;
                }
            });
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception unexpected) {
            throw new IllegalStateException(unexpected);
        }
    }

    /** The tooltip lines, normal or advanced as asked, coloured as the game colours them. */
    @Override
    public List<String> getItemToolTip(ItemStack stack) {
        List<String> list = stack.getTooltip(Minecraft.getMinecraft().player,
                advanced ? ITooltipFlag.TooltipFlags.ADVANCED : ITooltipFlag.TooltipFlags.NORMAL);
        for (int i = 0; i < list.size(); ++i) {
            if (i == 0) {
                list.set(i, stack.getItem().getForgeRarity(stack).getColor() + list.get(i));
            } else {
                list.set(i, TextFormatting.GRAY + list.get(i));
            }
        }
        return list;
    }

    /** The lines the drawn tooltip shows, for the record, built with the same keys held. */
    List<String> lines(final ItemStack stack) {
        try {
            return ModifierKeys.during(keys, new java.util.concurrent.Callable<List<String>>() {
                @Override
                public List<String> call() {
                    // Read the way mods read it, while the keys are held.
                    keysSeen = ModifierKeys.seen();
                    return getItemToolTip(stack);
                }
            });
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception unexpected) {
            throw new IllegalStateException(unexpected);
        }
    }

    /** Which of shift, ctrl, alt the game read as held while {@link #lines} built the tooltip. */
    String keysSeen() {
        return keysSeen;
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(I18n.format("container.crafting"), 97, 8, 4210752);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager().bindTexture(INVENTORY_BACKGROUND);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
        GuiInventory.drawEntityOnScreen(guiLeft + 51, guiTop + 75, 30, (float) (guiLeft + 51 - mouseX),
                (float) (guiTop + 75 - 50 - mouseY), mc.player);
    }

    /**
     * Read only: no click, drag, number key or drop key moves an item through the game's own
     * handling here. Another mod's own handlers on container screens are not blocked.
     */
    @Override
    protected void handleMouseClick(Slot slotIn, int slotId, int mouseButton, ClickType type) {
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
    }
}
