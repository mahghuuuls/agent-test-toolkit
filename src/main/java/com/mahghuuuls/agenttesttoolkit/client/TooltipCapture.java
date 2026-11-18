package com.mahghuuuls.agenttesttoolkit.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The lines of a container tooltip as the screen draws them. The toolkit asks the open screen to
 * draw the slot's tooltip through the screen's own tooltip method, which a screen may override to
 * add lines (the creative search tab adds the item's tab name there); the drawing hands its final
 * lines to Forge's tooltip event, and this keeps the first such list seen while armed, for that one
 * draw only. Pure, so the choice of lines for the record is tested without a game.
 */
final class TooltipCapture {

    /** The screen's tooltip method could not be found in this game: the lines are the built ones. */
    static final String DETAIL_NO_METHOD = "noTooltipMethod";
    /** The screen drew no tooltip through Forge's tooltip drawing: the lines are the built ones. */
    static final String DETAIL_NOT_DRAWN = "notDrawn";

    private boolean armed;
    private List<String> seen;

    /** Starts listening for the one draw the toolkit is about to ask for. */
    void arm() {
        armed = true;
        seen = null;
    }

    /**
     * A tooltip being drawn, as Forge's event gives its lines. Kept only while armed, and only the
     * first: a tooltip that another handler draws during the same call is not the slot's.
     */
    void offer(List<String> lines) {
        if (armed && seen == null && lines != null) {
            seen = new ArrayList<String>(lines);
        }
    }

    /** Stops listening; the lines the draw handed over, or null when it drew none. */
    List<String> disarm() {
        armed = false;
        List<String> out = seen;
        seen = null;
        return out;
    }

    /** What the record carries: the lines, and why they are the built ones when they are. */
    static final class Choice {
        final List<String> lines;
        /** Null when the lines are the drawn ones. */
        final String detail;

        Choice(List<String> lines, String detail) {
            this.lines = Collections.unmodifiableList(lines);
            this.detail = detail;
        }
    }

    /**
     * The drawn lines when the draw handed some over; otherwise the lines the screen's
     * {@code getItemToolTip} built, with the reason: no tooltip method to call, or nothing drawn.
     */
    static Choice choose(boolean methodFound, List<String> drawn, List<String> built) {
        if (!methodFound) {
            return new Choice(built, DETAIL_NO_METHOD);
        }
        if (drawn == null) {
            return new Choice(built, DETAIL_NOT_DRAWN);
        }
        return new Choice(drawn, null);
    }
}
