package com.mahghuuuls.agenttesttoolkit.client.menu;

import java.util.List;
import java.util.Map;

/**
 * The choices {@code mods} makes in a Mods list, from plain values: where the mod is in the list
 * as shown, how far to scroll so its entry is in view, and how to name the filters that hid it.
 *
 * <p>Pure, so the arithmetic and the wording are unit-tested without a game.
 */
final class ModListChoice {

    /** A Forge list draws its entries this far below its top edge. */
    static final int BORDER = 4;

    private ModListChoice() {
    }

    /** The position of the mod with exactly this id in the list as shown; -1 when it is not shown. */
    static int indexOf(List<String> shownIds, String modId) {
        for (int i = 0; i < shownIds.size(); i++) {
            if (modId.equals(shownIds.get(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The scroll distance that puts entry {@code index} in the middle of a list's view, kept
     * within what the list itself allows (0 to the content's height less the view's), so the
     * list does not move it again when it draws.
     *
     * @param slotHeight    one entry's height
     * @param viewHeight    the list's height on screen, its bottom less its top
     * @param contentHeight all the entries' height
     */
    static float scrollFor(int index, int slotHeight, int viewHeight, int contentHeight) {
        float wanted = index * slotHeight + slotHeight / 2.0F - viewHeight / 2.0F;
        float most = contentHeight - (viewHeight - BORDER);
        if (most < 0.0F) {
            most = 0.0F;
        }
        return Math.max(0.0F, Math.min(most, wanted));
    }

    /**
     * The filters a list kept from an earlier opening, as one value: {@code query:<text>} for a
     * search, then the name of each option that is on; {@code none} when none is.
     *
     * @param options each option's name and whether it is on, in the order to write them
     */
    static String filters(String query, Map<String, Boolean> options) {
        StringBuilder out = new StringBuilder();
        if (query != null && !query.trim().isEmpty()) {
            out.append("query:").append(query.trim());
        }
        for (Map.Entry<String, Boolean> option : options.entrySet()) {
            if (Boolean.TRUE.equals(option.getValue())) {
                out.append(out.length() == 0 ? "" : ",").append(option.getKey());
            }
        }
        return out.length() == 0 ? "none" : out.toString();
    }
}
