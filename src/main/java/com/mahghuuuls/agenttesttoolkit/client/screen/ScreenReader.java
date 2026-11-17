package com.mahghuuuls.agenttesttoolkit.client.screen;

import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the open screen into plain values: its class, its size in its own GUI units, the scale
 * from those units to the window's pixels, its buttons, and a container's slots; and writes them
 * as {@code SCREEN_INFO}, {@code SCREEN_BUTTON} and {@code SCREEN_SLOT} records.
 *
 * <p>Only what every screen keeps the same way is read: the buttons in the screen's own button
 * list (a protected field, found once by its name in the game's mappings) and a container's
 * slots. Widgets a screen keeps in fields of its own (lists, text fields, a mod's own controls)
 * are not listed, and the record says what was. The scale is taken from this screen, since a mod
 * can give one screen a GUI scale of its own. Client thread only.
 */
public final class ScreenReader {

    /** At most this many buttons are written as records; {@code buttons} still counts them all. */
    public static final int MAX_BUTTONS = 100;
    /** At most this many slots are written as records; {@code slots} still counts them all. */
    public static final int MAX_SLOTS = 200;

    /** {@code GuiScreen.buttonList}. */
    private static final String BUTTON_LIST = "field_146292_n";

    private static boolean resolved;
    private static Field buttonList;

    private ScreenReader() {
    }

    /** One screen as read: everything the records and the targets need. */
    public static final class Info {
        /** The screen; null when none is open. */
        public final GuiScreen screen;
        /** The screen's class and its superclasses, most specific first; empty when none is open. */
        public final List<String> hierarchy;
        public final int width;
        public final int height;
        public final double scale;
        public final boolean container;
        public final List<ScreenTargets.Button> buttons;
        public final List<ScreenTargets.SlotView> slots;
        /** False when this game's button list could not be read; then no button is listed. */
        public final boolean buttonsRead;

        Info(GuiScreen screen, List<String> hierarchy, int width, int height, double scale, boolean container,
             List<ScreenTargets.Button> buttons, List<ScreenTargets.SlotView> slots, boolean buttonsRead) {
            this.screen = screen;
            this.hierarchy = hierarchy;
            this.width = width;
            this.height = height;
            this.scale = scale;
            this.container = container;
            this.buttons = buttons;
            this.slots = slots;
            this.buttonsRead = buttonsRead;
        }

        /** The screen's class, or {@code none}. */
        public String screenName() {
            return hierarchy.isEmpty() ? "none" : hierarchy.get(0);
        }

        /** What this record lists: the button list where it could be read, and a container's slots. */
        public String listed() {
            return buttonsRead ? "buttons,slots" : "slots";
        }
    }

    /** The open screen as plain values; with none open, an empty reading. */
    public static Info read(Minecraft mc, GuiScreen screen) {
        if (screen == null) {
            return new Info(null, Collections.<String>emptyList(), 0, 0, 0.0D, false,
                    Collections.<ScreenTargets.Button>emptyList(), Collections.<ScreenTargets.SlotView>emptyList(), true);
        }
        List<ScreenTargets.Button> buttons = new ArrayList<ScreenTargets.Button>();
        List<GuiButton> list = buttons(screen);
        if (list != null) {
            for (GuiButton button : new ArrayList<GuiButton>(list)) {
                if (button != null) {
                    buttons.add(new ScreenTargets.Button(button.id, plain(button.displayString), button.x, button.y,
                            button.width, button.height, button.enabled, button.visible));
                }
            }
        }
        List<ScreenTargets.SlotView> slots = new ArrayList<ScreenTargets.SlotView>();
        boolean container = screen instanceof GuiContainer;
        if (container) {
            GuiContainer gui = (GuiContainer) screen;
            if (gui.inventorySlots != null) {
                for (Slot slot : gui.inventorySlots.inventorySlots) {
                    ItemStack stack = slot.getStack();
                    slots.add(new ScreenTargets.SlotView(slot.slotNumber, slot.getSlotIndex(),
                            gui.getGuiLeft() + slot.xPos, gui.getGuiTop() + slot.yPos, itemName(stack),
                            stack.isEmpty() ? 0 : stack.getCount(), slot.isEnabled()));
                }
            }
        }
        double scale = screen.width > 0 ? (double) mc.displayWidth / screen.width : 0.0D;
        return new Info(screen, hierarchy(screen.getClass()), screen.width, screen.height, scale, container,
                buttons, slots, list != null);
    }

    /** A class and its superclasses by name, most specific first, up to and without {@code Object}. */
    public static List<String> hierarchy(Class<?> type) {
        List<String> names = new ArrayList<String>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            names.add(c.getName());
        }
        return names;
    }

    /** The slot of that number in an open container, or null. */
    public static Slot slot(GuiContainer gui, int number) {
        if (gui.inventorySlots == null) {
            return null;
        }
        for (Slot slot : gui.inventorySlots.inventorySlots) {
            if (slot.slotNumber == number) {
                return slot;
            }
        }
        return null;
    }

    /** An item's registry name, or {@code empty}. */
    public static String itemName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }
        ResourceLocation name = stack.getItem().getRegistryName();
        return name == null ? "unknown" : name.toString();
    }

    /**
     * Writes {@code SCREEN_INFO}, then one {@code SCREEN_BUTTON} per button and one
     * {@code SCREEN_SLOT} per slot, within their caps. Each record carries the request's own
     * field ({@code requestTick} for a server's step, {@code id} for a channel request) so the
     * records of one reading can be told from another's.
     */
    public static void write(Info info, String requestKey, String requestValue) {
        LogRecord head = LogRecord.of(EventType.SCREEN_INFO).add("side", "CLIENT").add("screen", info.screenName());
        if (info.screen != null) {
            head.add("width", info.width)
                    .add("height", info.height)
                    .addDecimal("scale", info.scale)
                    .add("container", info.container)
                    .add("buttons", info.buttons.size())
                    .add("slots", info.slots.size())
                    .add("listed", info.listed());
            if (info.buttons.size() > MAX_BUTTONS || info.slots.size() > MAX_SLOTS) {
                head.add("truncated", true);
            }
        }
        ToolkitLog.write(head.add(requestKey, requestValue));
        for (int i = 0; i < info.buttons.size() && i < MAX_BUTTONS; i++) {
            ScreenTargets.Button b = info.buttons.get(i);
            ToolkitLog.write(LogRecord.of(EventType.SCREEN_BUTTON)
                    .add("side", "CLIENT")
                    .add("id", b.id)
                    .add("label", b.label)
                    .add("x", b.x)
                    .add("y", b.y)
                    .add("width", b.width)
                    .add("height", b.height)
                    .add("enabled", b.enabled)
                    .add("visible", b.visible)
                    .add(requestKey, requestValue));
        }
        for (int i = 0; i < info.slots.size() && i < MAX_SLOTS; i++) {
            ScreenTargets.SlotView s = info.slots.get(i);
            ToolkitLog.write(LogRecord.of(EventType.SCREEN_SLOT)
                    .add("side", "CLIENT")
                    .add("slot", s.number)
                    .add("index", s.index)
                    .add("x", s.x)
                    .add("y", s.y)
                    .add("item", s.item)
                    .add("count", s.count)
                    .add("enabled", s.enabled)
                    .add(requestKey, requestValue));
        }
    }

    /** The summary a step's own record and answer carry. */
    public static Map<String, String> summary(Info info) {
        Map<String, String> fields = new LinkedHashMap<String, String>();
        fields.put("container", String.valueOf(info.container));
        fields.put("buttons", String.valueOf(info.buttons.size()));
        fields.put("slots", String.valueOf(info.slots.size()));
        fields.put("listed", info.listed());
        return fields;
    }

    /** Text without the game's formatting codes, as the record shows a label and a click names it. */
    public static String plain(String text) {
        if (text == null) {
            return "";
        }
        String stripped = TextFormatting.getTextWithoutFormattingCodes(text);
        return stripped == null ? "" : stripped;
    }

    /** The screen's own button list; null when this game's list cannot be read. */
    @SuppressWarnings("unchecked")
    private static List<GuiButton> buttons(GuiScreen screen) {
        Field field = buttonListField();
        if (field == null) {
            return null;
        }
        try {
            Object value = field.get(screen);
            return value instanceof List ? (List<GuiButton>) value : null;
        } catch (IllegalAccessException | RuntimeException unreadable) {
            return null;
        }
    }

    private static synchronized Field buttonListField() {
        if (!resolved) {
            resolved = true;
            try {
                buttonList = ObfuscationReflectionHelper.findField(GuiScreen.class, BUTTON_LIST);
            } catch (RuntimeException notFound) {
                buttonList = null;
            }
        }
        return buttonList;
    }
}
