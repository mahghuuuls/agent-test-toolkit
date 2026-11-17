package com.mahghuuuls.agenttesttoolkit.client;

/**
 * Where the prompt panel goes on the scaled screen, from plain numbers.
 *
 * <p>The right edge holds other things a person needs to see: potion icons and toasts at the
 * top, the scoreboard sidebar in the middle, subtitles at the bottom. The chat is on the left,
 * which is why the panel is on the right at all. The panel sits {@link #EDGE} pixels in from the
 * right, below the potion icons and below every toast shown in that frame (toasts are drawn after
 * the HUD and would cover it), above what is at the bottom, and above or below a shown sidebar;
 * failing both, beside it on its left. Toasts come and go, so the place is worked out again each
 * frame. When there is not even a label's room below the toasts, the panel goes below the potion
 * icons alone, and a passing toast covers it for its few seconds; when the screen is too small
 * even for that, it gives up the reservations rather than leaving the screen: a prompt partly
 * over the scoreboard is still a prompt, one drawn off screen is not.
 *
 * <p>Free of game types, so every placement is tested without a game.
 */
public final class PanelLayout {

    /** The gap kept from the screen's edge and from everything the panel avoids. */
    public static final int EDGE = 4;
    /** The widest the panel grows on a large screen. */
    public static final int MAX_WIDTH = 200;
    /** The least height worth keeping the reservations for: a label with its margins. */
    public static final int MIN_HEIGHT = 20;
    /** The height of one toast row at the top right, as the game stacks them. */
    public static final int TOAST_HEIGHT = 32;
    /** The most toasts the game shows at once; assumed when the shown toasts cannot be read. */
    public static final int MAX_TOASTS = 5;

    /** A rectangle in scaled screen pixels: left, top, width and height. */
    public static final class Rect {
        public final int x;
        public final int y;
        public final int width;
        public final int height;

        public Rect(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = Math.max(0, width);
            this.height = Math.max(0, height);
        }

        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        boolean overlaps(Rect other) {
            return other != null && x < other.right() && other.x < right()
                    && y < other.bottom() && other.y < bottom();
        }

        @Override
        public String toString() {
            return x + "," + y + " " + width + "x" + height;
        }
    }

    private PanelLayout() {
    }

    /** The widest the panel may be on a screen this wide, text wrapped to fit inside it. */
    public static int maxWidth(int screenWidth) {
        return Math.max(0, Math.min(MAX_WIDTH, screenWidth - 2 * EDGE));
    }

    /**
     * The toast rows in use, from the toast overlay's slots: one past the lowest slot holding a
     * toast. Each toast is drawn at its own slot's row, so an empty slot above a shown toast still
     * leaves that toast where it is.
     *
     * @param slots the overlay's slots, one per row, null where a row is empty; null when they
     *              could not be read, which counts as every row in use
     */
    public static int toastRows(Object[] slots) {
        if (slots == null) {
            return MAX_TOASTS;
        }
        for (int i = slots.length - 1; i >= 0; i--) {
            if (slots[i] != null) {
                return i + 1;
            }
        }
        return 0;
    }

    /**
     * The panel's rectangle.
     *
     * @param width     the width the panel's text asks for
     * @param height    the height the panel's text asks for; cut down when there is less room
     * @param top       the lowest pixel row used at the top right by the potion icons; 0 for none
     * @param toastRows the toast rows in use this frame, as {@link #toastRows} gives them; more
     *                  than {@link #MAX_TOASTS} counts as that many
     * @param bottom    the highest pixel row used at the bottom right, by subtitles; the screen
     *                  height when nothing is there
     * @param sidebar   the scoreboard sidebar's rectangle, or null when none is shown
     */
    public static Rect place(int screenWidth, int screenHeight, int width, int height, int top, int toastRows,
                             int bottom, Rect sidebar) {
        int w = Math.min(Math.max(0, width), Math.max(0, Math.min(maxWidth(screenWidth), screenWidth)));
        int h = Math.max(0, height);
        int x = Math.max(0, screenWidth - EDGE - w);
        int least = Math.min(h, MIN_HEIGHT);

        int toastsBottom = clamp(toastRows, 0, MAX_TOASTS) * TOAST_HEIGHT;
        int bandTop = clamp(Math.max(top, toastsBottom) + EDGE, 0, screenHeight);
        int bandBottom = clamp(bottom - EDGE, 0, screenHeight);
        if (bandBottom - bandTop < least) {
            // Not a label's room below the toasts: below the potion icons alone. A toast is gone in
            // seconds, while a prompt with no room at all would not be seen.
            bandTop = clamp(top + EDGE, 0, screenHeight);
        }
        if (bandBottom - bandTop < least) {
            // Not even a label's room between the top and bottom reservations: the whole height
            // instead. With more room than that, a tall panel is cut to the room there is.
            bandTop = 0;
            bandBottom = screenHeight;
        }
        h = Math.min(h, bandBottom - bandTop);

        Rect atTop = new Rect(x, bandTop, w, h);
        if (!atTop.overlaps(sidebar)) {
            return atTop;
        }
        // Overlapping the sidebar from here means the panel does not fit above it.
        int above = sidebar.y - EDGE - bandTop;
        int belowFrom = Math.max(bandTop, sidebar.bottom() + EDGE);
        int below = bandBottom - belowFrom;
        if (h <= below) {
            return new Rect(x, belowFrom, w, h);
        }
        int besideX = sidebar.x - EDGE - w;
        if (besideX >= 0) {
            return new Rect(besideX, bandTop, w, h);
        }
        // Too tall for either side and too wide to go beside: the larger side, cut to fit.
        if (above >= below && above > 0) {
            return new Rect(x, bandTop, w, above);
        }
        if (below > 0) {
            return new Rect(x, belowFrom, w, below);
        }
        return atTop;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
