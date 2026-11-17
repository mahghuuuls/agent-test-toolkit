package com.mahghuuuls.agenttesttoolkit.client;

import com.mahghuuuls.agenttesttoolkit.client.screen.ScreenClicker;
import com.mahghuuuls.agenttesttoolkit.client.screen.ScreenReader;
import com.mahghuuuls.agenttesttoolkit.client.screen.ScreenTargets;
import com.mahghuuuls.agenttesttoolkit.inbox.ScreenOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.client.config.GuiUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Takes the steps on the open screen: describe it, click a slot, a button or a point, turn the
 * wheel over it, and draw the tooltip of a slot of an open container. Each goes through the
 * screen's own handling, so the game, Forge and other mods react as to a person, and each record
 * names the route it drove; what cannot be driven in the running game is refused.
 *
 * <p>Client thread only, at a client tick end, after the common checks of {@link PlayerInput}.
 * A click is an action: the running holds were ended before it, by the common release rules.
 */
final class ScreenSteps {

    /**
     * The route of every screen step that acts on the screen: a click's or key's screen handler,
     * a container tooltip's drawing over the screen.
     */
    static final String ROUTE_SCREEN = "screen";
    /** A container tooltip is drawn at least this many ticks, about a second, as the inventory view's default. */
    static final int TOOLTIP_TICKS = 20;
    /** And this many ticks past a screenshot's delay, so the picture falls while it shows. */
    static final int TOOLTIP_PAST_SCREENSHOT = 10;

    private final HeldInputs.Records records;
    /** The container tooltip being drawn, until its ticks run out or the screen changes. */
    private Drawn drawn;

    private static final class Drawn {
        final GuiContainer screen;
        final Slot slot;
        final boolean advanced;
        final int[] keys;
        int ticksLeft;

        Drawn(GuiContainer screen, Slot slot, boolean advanced, int[] keys, int ticks) {
            this.screen = screen;
            this.slot = slot;
            this.advanced = advanced;
            this.keys = keys;
            this.ticksLeft = ticks;
        }
    }

    ScreenSteps(HeldInputs.Records records) {
        this.records = records;
    }

    void take(Minecraft mc, Asked asked) {
        ScreenOptions options = asked.screenOptions();
        if (mc.world == null || mc.player == null) {
            refuse(mc, asked, PlayerActionGate.REASON_NO_WORLD, new LinkedHashMap<String, String>());
            return;
        }
        String step = options.getStep();
        if (ScreenOptions.SCREEN.equals(step)) {
            describe(mc, asked);
        } else if (ScreenOptions.SCROLL.equals(step)) {
            scroll(mc, asked, options);
        } else if (ScreenOptions.TOOLTIP.equals(step)) {
            tooltip(mc, asked, options);
        } else {
            click(mc, asked, options);
        }
    }

    /** One tick of the drawn tooltip: it ends after its ticks, or once its screen is not the open one. */
    void tick(Minecraft mc) {
        if (drawn != null && (mc.currentScreen != drawn.screen || --drawn.ticksLeft <= 0)) {
            drawn = null;
        }
    }

    /**
     * Draws the asked tooltip after the open container has drawn itself, at the slot's centre,
     * the way the game draws a hovered slot's tooltip, so the tooltip events fire as for a hover.
     */
    void draw(GuiScreenEvent.DrawScreenEvent.Post event) {
        final Drawn d = drawn;
        if (d == null || event.getGui() != d.screen) {
            return;
        }
        final ItemStack stack = d.slot.getStack();
        if (stack.isEmpty()) {
            return;
        }
        final Minecraft mc = Minecraft.getMinecraft();
        final int x = d.screen.getGuiLeft() + d.slot.xPos + 8;
        final int y = d.screen.getGuiTop() + d.slot.yPos + 8;
        try {
            ModifierKeys.holding(d.keys, new Callable<Void>() {
                @Override
                public Void call() {
                    List<String> lines = linesOf(mc, d.screen, stack, d.advanced);
                    FontRenderer font = stack.getItem().getFontRenderer(stack);
                    GuiUtils.preItemToolTip(stack);
                    GuiUtils.drawHoveringText(lines, x, y, d.screen.width, d.screen.height, -1,
                            font == null ? mc.fontRenderer : font);
                    GuiUtils.postItemToolTip();
                    return null;
                }
            });
        } catch (RuntimeException failure) {
            // Another mod's tooltip handler threw while drawing: the drawing stops rather than
            // throwing out of every frame. The step's record was written when it was taken.
            drawn = null;
        }
    }

    /** {@code SCREEN_INFO} and its buttons and slots; changes nothing. */
    private void describe(Minecraft mc, Asked asked) {
        ScreenReader.Info info = ScreenReader.read(mc, mc.currentScreen);
        ScreenReader.write(info, "requestTick", String.valueOf(asked.requestTick()));
        Map<String, String> fields = info.screen == null ? new LinkedHashMap<String, String>()
                : ScreenReader.summary(info);
        records.finish(mc, asked, "done", null, info.screenName(), null, fields);
    }

    private void click(Minecraft mc, Asked asked, ScreenOptions options) {
        ScreenReader.Info info = ScreenReader.read(mc, mc.currentScreen);
        Map<String, String> fields = new LinkedHashMap<String, String>();
        String refusal = ScreenTargets.screenRefusal(info.hierarchy, options.getExpect());
        if (refusal != null) {
            refuse(mc, asked, refusal, fields);
            return;
        }
        if (!ScreenClicker.available()) {
            fields.put("detail", String.valueOf(ScreenClicker.failure()));
            refuse(mc, asked, RouteState.REASON_ROUTE_UNAVAILABLE, fields);
            return;
        }
        fields.put("target", options.getTarget());
        int x;
        int y;
        int button = ScreenOptions.RIGHT.equals(options.getMouse()) ? ScreenClicker.RIGHT : ScreenClicker.LEFT;
        Slot slot = null;
        if (ScreenOptions.SLOT.equals(options.getTarget())) {
            ScreenTargets.Pick<ScreenTargets.SlotView> pick = ScreenTargets.slot(info.slots, options.getSlot());
            fields.put("slot", String.valueOf(options.getSlot()));
            if (pick.refusal != null) {
                refuse(mc, asked, pick.refusal, fields);
                return;
            }
            slot = ScreenReader.slot((GuiContainer) info.screen, options.getSlot());
            x = pick.found.centreX();
            y = pick.found.centreY();
            fields.put("mouse", ScreenOptions.KEY.equals(options.getMouse())
                    ? ScreenOptions.KEY + options.getHotbarKey() : options.getMouse());
        } else if (ScreenOptions.BUTTON.equals(options.getTarget())) {
            ScreenTargets.Pick<ScreenTargets.Button> pick = ScreenTargets.button(info.buttons, options.getButtonId(),
                    options.getLabel());
            if (options.getLabel().isEmpty()) {
                fields.put("button", String.valueOf(options.getButtonId()));
            } else {
                fields.put("label", options.getLabel());
            }
            if (!info.buttonsRead) {
                fields.put("detail", "buttonListUnreadable");
                refuse(mc, asked, RouteState.REASON_ROUTE_UNAVAILABLE, fields);
                return;
            }
            if (pick.refusal != null) {
                refuse(mc, asked, pick.refusal, fields);
                return;
            }
            fields.put("button", String.valueOf(pick.found.id));
            fields.put("label", pick.found.label);
            x = pick.found.centreX();
            y = pick.found.centreY();
            button = ScreenClicker.LEFT;
        } else {
            x = options.getX();
            y = options.getY();
            fields.put("mouse", options.getMouse());
        }
        fields.put("x", String.valueOf(x));
        fields.put("y", String.valueOf(y));

        boolean shift = ScreenOptions.SHIFT.equals(options.getMouse());
        boolean key = ScreenOptions.KEY.equals(options.getMouse());
        int keyCode = 0;
        if (shift && !ModifierKeys.available()) {
            refuse(mc, asked, PlayerActionGate.REASON_MODIFIER_UNAVAILABLE, fields);
            return;
        }
        if (key) {
            keyCode = mc.gameSettings.keyBindsHotbar[options.getHotbarKey() - 1].getKeyCode();
            if (keyCode <= 0) {
                // The hotbar key is unbound or on a mouse button: no key to type.
                refuse(mc, asked, PlayerActionGate.REASON_UNBOUND, fields);
                return;
            }
            if (!ScreenClicker.canHover()) {
                fields.put("detail", "noHoveredSlot");
                refuse(mc, asked, RouteState.REASON_ROUTE_UNAVAILABLE, fields);
                return;
            }
        }
        fields.put("route", ROUTE_SCREEN);
        if (shift) {
            fields.put("keys", "shift");
        }
        GuiScreen screen = info.screen;
        try {
            if (key) {
                typeOver(mc, (GuiContainer) screen, slot, options.getHotbarKey(), keyCode);
            } else if (shift) {
                // Held for the call only: a modifier kept down across a tick could set off another
                // mod's own shortcut that reads the real keyboard.
                final int cx = x;
                final int cy = y;
                final GuiScreen target = screen;
                ModifierKeys.during(ModifierKeys.codes("shift"), new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        ScreenClicker.click(target, cx, cy, ScreenClicker.LEFT);
                        return null;
                    }
                });
            } else {
                ScreenClicker.click(screen, x, y, button);
            }
        } catch (Exception failure) {
            records.finish(mc, asked, "failed", failure.getClass().getSimpleName(), info.screenName(),
                    PlayerInput.screenName(mc.currentScreen), fields);
            return;
        }
        if (slot != null) {
            // The client's own copy, as the game predicts it; the server's records say what it kept.
            fields.put("item", ScreenReader.itemName(slot.getStack()));
            fields.put("count", String.valueOf(slot.getStack().isEmpty() ? 0 : slot.getStack().getCount()));
        }
        if (screen instanceof GuiContainer) {
            ItemStack cursor = mc.player.inventory.getItemStack();
            fields.put("cursor", ScreenReader.itemName(cursor));
            fields.put("cursorCount", String.valueOf(cursor.isEmpty() ? 0 : cursor.getCount()));
        }
        records.screenshotAfter(asked, fields);
        records.finish(mc, asked, "done", null, info.screenName(), PlayerInput.screenName(mc.currentScreen), fields);
    }

    /** A number key over a slot: the slot set as the hovered one, then the key typed, as with the mouse over it. */
    private static void typeOver(Minecraft mc, GuiContainer screen, Slot slot, int hotbarKey, int keyCode)
            throws Exception {
        ScreenClicker.hover(screen, slot);
        ScreenClicker.type(screen, (char) ('0' + hotbarKey), keyCode);
    }

    /** The wheel, notch by notch, through the mouse library's queue: the screen reads it as a real wheel. */
    private void scroll(Minecraft mc, Asked asked, ScreenOptions options) {
        ScreenReader.Info info = ScreenReader.read(mc, mc.currentScreen);
        Map<String, String> fields = new LinkedHashMap<String, String>();
        String refusal = ScreenTargets.screenRefusal(info.hierarchy, options.getExpect());
        if (refusal != null) {
            refuse(mc, asked, refusal, fields);
            return;
        }
        MouseQueue queue = MouseQueue.Choice.get();
        if (queue == null) {
            String failure = MouseQueue.Choice.state().takeFailureName();
            if (failure != null) {
                fields.put("detail", failure);
            }
            refuse(mc, asked, RouteState.REASON_ROUTE_UNAVAILABLE, fields);
            return;
        }
        int notches = options.getAmount();
        int step = notches > 0 ? 1 : -1;
        try {
            for (int i = 0; i < Math.abs(notches); i++) {
                queue.wheel(step);
            }
        } catch (RuntimeException failure) {
            MouseQueue.Choice.failed(failure);
            records.finish(mc, asked, "failed", failure.getClass().getSimpleName(), info.screenName(), null, fields);
            return;
        }
        fields.put("route", HeldInputs.ROUTE_QUEUE);
        fields.put("notches", String.valueOf(notches));
        records.screenshotAfter(asked, fields);
        records.finish(mc, asked, "done", null, info.screenName(), null, fields);
    }

    /** The tooltip of a slot of the open container, drawn over it while the container stays open. */
    private void tooltip(Minecraft mc, Asked asked, ScreenOptions options) {
        Map<String, String> fields = new LinkedHashMap<String, String>();
        fields.put("slot", String.valueOf(options.getSlot()));
        GuiScreen open = mc.currentScreen;
        String screenName = PlayerInput.screenName(open);
        if (!(open instanceof GuiContainer)) {
            refuse(mc, asked, ScreenTargets.REASON_NO_SCREEN, fields);
            return;
        }
        GuiContainer container = (GuiContainer) open;
        ScreenReader.Info info = ScreenReader.read(mc, container);
        ScreenTargets.Pick<ScreenTargets.SlotView> pick = ScreenTargets.slot(info.slots, options.getSlot());
        if (pick.refusal != null) {
            refuse(mc, asked, pick.refusal, fields);
            return;
        }
        if (pick.found.isEmpty()) {
            refuse(mc, asked, PlayerActionGate.REASON_EMPTY_SLOT, fields);
            return;
        }
        String keys = options.modifierKeys();
        if (!keys.isEmpty() && !ModifierKeys.available()) {
            refuse(mc, asked, PlayerActionGate.REASON_MODIFIER_UNAVAILABLE, fields);
            return;
        }
        final Slot slot = ScreenReader.slot(container, options.getSlot());
        final ItemStack stack = slot.getStack();
        final boolean advanced = options.isAdvanced();
        final GuiContainer screen = container;
        final Minecraft game = mc;
        int[] codes = ModifierKeys.codes(keys);
        fields.put("advanced", String.valueOf(advanced));
        if (!keys.isEmpty()) {
            fields.put("keys", keys);
        }
        fields.put("route", ROUTE_SCREEN);
        int ticks = Math.max(TOOLTIP_TICKS, asked.screenshotDelay() + TOOLTIP_PAST_SCREENSHOT);
        fields.put("showTicks", String.valueOf(ticks));
        final String[] seen = {""};
        List<String> lines;
        try {
            fields.put("item", ScreenReader.itemName(stack));
            fields.put("count", String.valueOf(stack.getCount()));
            lines = ModifierKeys.holding(codes, new Callable<List<String>>() {
                @Override
                public List<String> call() {
                    seen[0] = ModifierKeys.seen();
                    return linesOf(game, screen, stack, advanced);
                }
            });
        } catch (RuntimeException failure) {
            // Another mod's tooltip handler threw: nothing is drawn and the step failed.
            records.finish(mc, asked, "failed", failure.getClass().getSimpleName(), screenName, null,
                    new LinkedHashMap<String, String>());
            return;
        }
        fields.putAll(TooltipLines.fields(lines));
        if (!keys.isEmpty()) {
            fields.put("keysSeen", seen[0]);
        }
        drawn = new Drawn(container, slot, advanced, codes, ticks);
        records.screenshotAfter(asked, fields);
        records.finish(mc, asked, "done", null, screenName, null, fields);
    }

    /**
     * The lines the open screen gives for the item, through its own tooltip method (which a mod's
     * screen may change), in the normal or the advanced form as asked; the player's own F3+H
     * setting comes back right after.
     */
    private static List<String> linesOf(Minecraft mc, GuiScreen screen, ItemStack stack, boolean advanced) {
        boolean was = mc.gameSettings.advancedItemTooltips;
        mc.gameSettings.advancedItemTooltips = advanced;
        try {
            return screen.getItemToolTip(stack);
        } finally {
            mc.gameSettings.advancedItemTooltips = was;
        }
    }

    private void refuse(Minecraft mc, Asked asked, String reason, Map<String, String> fields) {
        records.finish(mc, asked, "refused", reason, PlayerInput.screenName(mc.currentScreen), null, fields);
    }
}
