package com.mahghuuuls.agenttesttoolkit.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where the prompt panel goes beside the right-edge parts of the HUD. */
class PanelLayoutTest {

    /** A 1920x1080 window at GUI scale 3. */
    private static final int W = 640;
    private static final int H = 360;

    private static void assertOnScreen(PanelLayout.Rect rect, int width, int height) {
        assertTrue(rect.x >= 0 && rect.y >= 0 && rect.right() <= width && rect.bottom() <= height,
                rect + " on " + width + "x" + height);
    }

    private static boolean overlaps(PanelLayout.Rect a, PanelLayout.Rect b) {
        return a.x < b.right() && b.x < a.right() && a.y < b.bottom() && b.y < a.bottom();
    }

    @Test
    @DisplayName("with no sidebar: 4 pixels in from the right, 4 below a toast at the top")
    void noSidebar() {
        PanelLayout.Rect rect = PanelLayout.place(W, H, 120, 33, 0, 1, H - 55, null);
        assertEquals(W - 4 - 120, rect.x);
        assertEquals(36, rect.y);
        assertEquals(120, rect.width);
        assertEquals(33, rect.height);
    }

    @Test
    @DisplayName("no toast and no potion icons: 4 pixels from the top")
    void noToasts() {
        PanelLayout.Rect rect = PanelLayout.place(W, H, 120, 33, 0, 0, H - 55, null);
        assertEquals(4, rect.y);
    }

    @Test
    @DisplayName("below every toast shown: 1, 3 and 5 rows, and more counted as 5")
    void belowSeveralToasts() {
        assertEquals(PanelLayout.TOAST_HEIGHT + 4, PanelLayout.place(W, H, 120, 33, 0, 1, H - 55, null).y);
        assertEquals(3 * PanelLayout.TOAST_HEIGHT + 4, PanelLayout.place(W, H, 120, 33, 0, 3, H - 55, null).y);
        assertEquals(5 * PanelLayout.TOAST_HEIGHT + 4, PanelLayout.place(W, H, 120, 33, 0, 5, H - 55, null).y);
        assertEquals(5 * PanelLayout.TOAST_HEIGHT + 4, PanelLayout.place(W, H, 120, 33, 0, 9, H - 55, null).y);
    }

    @Test
    @DisplayName("below the potion icons when they reach lower than the toasts, below the toasts when those do")
    void belowPotionRows() {
        assertEquals(55, PanelLayout.place(W, H, 120, 33, 51, 1, H, null).y);
        assertEquals(55, PanelLayout.place(W, H, 120, 33, 51, 0, H, null).y);
        assertEquals(2 * PanelLayout.TOAST_HEIGHT + 4, PanelLayout.place(W, H, 120, 33, 51, 2, H, null).y);
    }

    @Test
    @DisplayName("the toast rows are one past the lowest slot in use; unreadable slots count as all five")
    void toastRows() {
        assertEquals(0, PanelLayout.toastRows(new Object[5]));
        assertEquals(1, PanelLayout.toastRows(new Object[] {"a", null, null, null, null}));
        assertEquals(3, PanelLayout.toastRows(new Object[] {"a", "b", "c", null, null}));
        assertEquals(2, PanelLayout.toastRows(new Object[] {null, "b", null, null, null}),
                "a toast stays at its own row when the one above has gone");
        assertEquals(PanelLayout.MAX_TOASTS, PanelLayout.toastRows(null));
        assertEquals(0, PanelLayout.toastRows(new Object[0]));
    }

    @Test
    @DisplayName("several toasts and a sidebar: below the toasts, then clear of the sidebar")
    void toastsAndSidebar() {
        PanelLayout.Rect sidebar = new PanelLayout.Rect(W - 90, 120, 89, 80);
        PanelLayout.Rect rect = PanelLayout.place(W, H, 120, 33, 0, 4, H - 55, sidebar);
        assertFalse(overlaps(rect, sidebar), rect + " over " + sidebar);
        assertTrue(rect.y >= 4 * PanelLayout.TOAST_HEIGHT, "not under a toast: " + rect);
        assertTrue(rect.bottom() <= H - 55 - PanelLayout.EDGE, "above the subtitles: " + rect);
    }

    @Test
    @DisplayName("no label's room below five toasts: below the potion icons instead, still on screen")
    void toastsLeaveNoRoom() {
        // An 854x480 window at GUI scale 3, with subtitles on.
        int width = 284;
        int height = 160;
        PanelLayout.Rect rect = PanelLayout.place(width, height, 120, 33, 0, 5, height - 55, null);
        assertEquals(4, rect.y);
        assertTrue(rect.bottom() <= height - 55 - PanelLayout.EDGE, "above the subtitles: " + rect);
        assertOnScreen(rect, width, height);
    }

    @Test
    @DisplayName("a sidebar with room above it: the panel stays above, clear of it")
    void sidebarRoomAbove() {
        // A short sidebar centred on the right edge.
        PanelLayout.Rect sidebar = new PanelLayout.Rect(W - 90, 160, 89, 40);
        PanelLayout.Rect rect = PanelLayout.place(W, H, 120, 33, 0, 1, H - 55, sidebar);
        assertFalse(overlaps(rect, sidebar), rect + " over " + sidebar);
        assertTrue(rect.bottom() <= sidebar.y - PanelLayout.EDGE);
    }

    @Test
    @DisplayName("a tall sidebar leaving no room above: the panel goes below it")
    void sidebarBelow() {
        PanelLayout.Rect sidebar = new PanelLayout.Rect(W - 90, 60, 89, 150);
        PanelLayout.Rect rect = PanelLayout.place(W, H, 120, 33, 0, 1, H - 55, sidebar);
        assertFalse(overlaps(rect, sidebar), rect + " over " + sidebar);
        assertEquals(sidebar.bottom() + PanelLayout.EDGE, rect.y);
        assertTrue(rect.bottom() <= H - 55 - PanelLayout.EDGE, "above the subtitles");
    }

    @Test
    @DisplayName("no room above or below: beside the sidebar on its left")
    void sidebarBeside() {
        PanelLayout.Rect sidebar = new PanelLayout.Rect(W - 90, 40, 89, 260);
        PanelLayout.Rect rect = PanelLayout.place(W, H, 120, 33, 0, 1, H - 55, sidebar);
        assertFalse(overlaps(rect, sidebar), rect + " over " + sidebar);
        assertEquals(sidebar.x - PanelLayout.EDGE - 120, rect.x);
    }

    @Test
    @DisplayName("a narrow sidebar beside a wide panel is still avoided")
    void sidebarNarrow() {
        PanelLayout.Rect sidebar = new PanelLayout.Rect(W - 40, 150, 39, 60);
        PanelLayout.Rect rect = PanelLayout.place(W, H, 200, 33, 0, 1, H, sidebar);
        assertFalse(overlaps(rect, sidebar));
    }

    @Test
    @DisplayName("a small screen narrows the panel and cuts it to the room there is")
    void smallScreen() {
        // An 854x480 window at GUI scale 3.
        int width = 284;
        int height = 160;
        assertEquals(200, PanelLayout.maxWidth(width));
        PanelLayout.Rect rect = PanelLayout.place(width, height, 400, 200, 0, 1, height - 55, null);
        assertEquals(200, rect.width);
        assertEquals(36, rect.y);
        assertEquals(height - 55 - 4 - 36, rect.height, "cut, not pushed into the subtitles");
        assertOnScreen(rect, width, height);
    }

    @Test
    @DisplayName("a very small screen gives up the reservations but never leaves the screen")
    void verySmallScreen() {
        for (int width = 0; width <= 60; width += 7) {
            for (int height = 0; height <= 60; height += 7) {
                for (int toasts = 0; toasts <= 5; toasts += 5) {
                    PanelLayout.Rect sidebar =
                            new PanelLayout.Rect(Math.max(0, width - 20), height / 3, 19, height / 3);
                    PanelLayout.Rect rect = PanelLayout.place(width, height, 150, 33, 51, toasts, height - 55,
                            sidebar);
                    assertOnScreen(rect, width, height);
                    assertTrue(rect.width <= Math.max(0, width - 8) || width < 8, rect + " width on " + width);
                }
            }
        }
        PanelLayout.Rect tiny = PanelLayout.place(40, 30, 150, 33, 51, 0, -25, null);
        assertEquals(0, tiny.y, "the whole height when the reservations leave none");
        assertEquals(30, tiny.height);
        assertEquals(32, tiny.width);
    }
}
