package com.mahghuuuls.agenttesttoolkit.client.screen;

import java.util.ArrayList;
import java.util.List;

/**
 * Which slot or button of the open screen a step names, and whether the screen is the one the
 * step expects, from plain values read off the screen.
 *
 * <p>Pure: no game type, so every refusal and its order are unit-tested without a game. The
 * screen itself is read by {@link ScreenReader}; the click is made by {@link ScreenClicker}.
 */
public final class ScreenTargets {

    /** No screen is open. */
    public static final String REASON_NO_SCREEN = "noScreen";
    /** The open screen is not the class the step expected, nor a subclass of it. */
    public static final String REASON_SCREEN_CHANGED = "screenChanged";
    /** No slot of that number in the open container, it is switched off, or the screen has no slots. */
    public static final String REASON_NO_SLOT = "noSlot";
    /** No shown button with that id or label. */
    public static final String REASON_NO_BUTTON = "noButton";
    /** The button is there but disabled, so a click would do nothing. */
    public static final String REASON_BUTTON_DISABLED = "buttonDisabled";
    /** Two or more shown buttons have that id or label: the toolkit does not guess. */
    public static final String REASON_AMBIGUOUS_BUTTON = "ambiguousButton";

    private ScreenTargets() {
    }

    /** One button as read off the screen: its label with formatting codes removed. */
    public static final class Button {
        public final int id;
        public final String label;
        public final int x;
        public final int y;
        public final int width;
        public final int height;
        public final boolean enabled;
        public final boolean visible;

        public Button(int id, String label, int x, int y, int width, int height, boolean enabled, boolean visible) {
            this.id = id;
            this.label = label == null ? "" : label;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.enabled = enabled;
            this.visible = visible;
        }

        /** The point a click on it is made at: its centre, in the screen's GUI units. */
        public int centreX() {
            return x + width / 2;
        }

        public int centreY() {
            return y + height / 2;
        }
    }

    /** One container slot as read off the screen; {@code x} and {@code y} are its top left corner on the screen. */
    public static final class SlotView {
        /** The container's slot number, the one a click names. */
        public final int number;
        /** The slot's index in its own inventory. */
        public final int index;
        public final int x;
        public final int y;
        /** The item's id, or {@code empty}. */
        public final String item;
        public final int count;
        public final boolean enabled;

        public SlotView(int number, int index, int x, int y, String item, int count, boolean enabled) {
            this.number = number;
            this.index = index;
            this.x = x;
            this.y = y;
            this.item = item;
            this.count = count;
            this.enabled = enabled;
        }

        /** The slot's centre, where the game looks for the slot under the mouse; slots are 16 units square. */
        public int centreX() {
            return x + 8;
        }

        public int centreY() {
            return y + 8;
        }

        public boolean isEmpty() {
            return count <= 0 || "empty".equals(item);
        }
    }

    /** What a lookup found: the one target, or the reason there is none. */
    public static final class Pick<T> {
        public final T found;
        public final String refusal;

        private Pick(T found, String refusal) {
            this.found = found;
            this.refusal = refusal;
        }

        static <T> Pick<T> of(T found) {
            return new Pick<T>(found, null);
        }

        static <T> Pick<T> refused(String reason) {
            return new Pick<T>(null, reason);
        }
    }

    /**
     * The refusal of a step on the screen whose class and superclasses are {@code hierarchy}
     * (most specific first; empty or null for no screen): {@code noScreen} with none open, then
     * {@code screenChanged} when {@code expect} is given and names none of them. Null when the
     * step may go on. A superclass counts, so a step can expect a mod's base screen.
     */
    public static String screenRefusal(List<String> hierarchy, String expect) {
        if (hierarchy == null || hierarchy.isEmpty()) {
            return REASON_NO_SCREEN;
        }
        if (expect != null && !expect.isEmpty() && !hierarchy.contains(expect)) {
            return REASON_SCREEN_CHANGED;
        }
        return null;
    }

    /**
     * A button by id (when {@code label} is empty) or by its exact label. Only shown buttons
     * count, since a hidden one cannot be clicked; of those, more than one match is
     * {@code ambiguousButton}, none {@code noButton}, and a disabled one {@code buttonDisabled}.
     */
    public static Pick<Button> button(List<Button> buttons, int id, String label) {
        boolean byLabel = label != null && !label.isEmpty();
        List<Button> matching = new ArrayList<Button>();
        for (Button button : buttons) {
            if (button.visible && (byLabel ? label.equals(button.label) : button.id == id)) {
                matching.add(button);
            }
        }
        if (matching.isEmpty()) {
            return Pick.refused(REASON_NO_BUTTON);
        }
        if (matching.size() > 1) {
            return Pick.refused(REASON_AMBIGUOUS_BUTTON);
        }
        Button one = matching.get(0);
        return one.enabled ? Pick.of(one) : Pick.<Button>refused(REASON_BUTTON_DISABLED);
    }

    /**
     * A container slot by its number. A switched-off slot is not under any point of the screen
     * for the game, so it counts as missing.
     */
    public static Pick<SlotView> slot(List<SlotView> slots, int number) {
        for (SlotView slot : slots) {
            if (slot.number == number) {
                return slot.enabled ? Pick.of(slot) : Pick.<SlotView>refused(REASON_NO_SLOT);
            }
        }
        return Pick.refused(REASON_NO_SLOT);
    }
}
