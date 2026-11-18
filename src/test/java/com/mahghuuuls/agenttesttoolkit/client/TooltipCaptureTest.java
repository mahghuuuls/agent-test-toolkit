package com.mahghuuuls.agenttesttoolkit.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The lines of the toolkit's own tooltip draw, taken from Forge's event, and the record's choice of lines. */
class TooltipCaptureTest {

    @Test
    @DisplayName("only a draw while armed is kept, and only the first of that draw")
    void keepsTheArmedDrawOnly() {
        TooltipCapture capture = new TooltipCapture();
        capture.offer(Arrays.asList("a hover by the real mouse"));
        capture.arm();
        capture.offer(Arrays.asList("Stone", "Building Blocks"));
        capture.offer(Arrays.asList("another tooltip drawn during the call"));
        assertEquals(Arrays.asList("Stone", "Building Blocks"), capture.disarm());
        capture.offer(Arrays.asList("a later hover"));
        assertNull(capture.disarm(), "nothing is kept once disarmed");
    }

    @Test
    @DisplayName("each draw starts empty; a draw with no tooltip event hands back nothing")
    void eachDrawStartsEmpty() {
        TooltipCapture capture = new TooltipCapture();
        capture.arm();
        capture.offer(Arrays.asList("first frame"));
        capture.disarm();
        capture.arm();
        assertNull(capture.disarm());
    }

    @Test
    @DisplayName("the kept lines are a copy: the drawing's own list may change after the event")
    void keepsACopy() {
        TooltipCapture capture = new TooltipCapture();
        List<String> live = new ArrayList<String>(Arrays.asList("Stone"));
        capture.arm();
        capture.offer(live);
        live.add("wrapped later");
        assertEquals(Arrays.asList("Stone"), capture.disarm());
    }

    @Test
    @DisplayName("the drawn lines are recorded; the built ones only with the reason they stand in")
    void choice() {
        List<String> built = Arrays.asList("Stone");
        List<String> drawn = Arrays.asList("Stone", "Building Blocks");

        TooltipCapture.Choice asDrawn = TooltipCapture.choose(true, drawn, built);
        assertEquals(drawn, asDrawn.lines);
        assertNull(asDrawn.detail);

        TooltipCapture.Choice notDrawn = TooltipCapture.choose(true, null, built);
        assertEquals(built, notDrawn.lines);
        assertEquals(TooltipCapture.DETAIL_NOT_DRAWN, notDrawn.detail);

        TooltipCapture.Choice noMethod = TooltipCapture.choose(false, null, built);
        assertEquals(built, noMethod.lines);
        assertEquals(TooltipCapture.DETAIL_NO_METHOD, noMethod.detail);
    }
}
