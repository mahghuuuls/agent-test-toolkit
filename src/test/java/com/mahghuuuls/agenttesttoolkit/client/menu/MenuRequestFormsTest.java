package com.mahghuuuls.agenttesttoolkit.client.menu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The channel's requests beyond the menu steps: screen, close, screenshot, mods, and open's answer to a question. */
class MenuRequestFormsTest {

    private static MenuRequest parse(String line) {
        return MenuRequest.parse("r1", line);
    }

    private static void assertBad(String line) {
        MenuRequest request = parse(line);
        assertFalse(request.isAccepted(), line);
        assertEquals(MenuRequest.REASON_BAD_ARGUMENTS, request.getRefusal(), line);
    }

    @Test
    @DisplayName("screen takes nothing")
    void screen() {
        assertEquals(MenuRequest.SCREEN, parse("screen").getStep());
        assertEquals(MenuRequest.SCREEN, parse("SCREEN").getStep(), "the step in any case");
        assertBad("screen all");
    }

    @Test
    @DisplayName("close takes one class name, at most 200 characters")
    void close() {
        MenuRequest book = parse("close net.minecraft.client.gui.GuiScreenBook");
        assertTrue(book.isAccepted());
        assertEquals(Collections.singletonList("net.minecraft.client.gui.GuiScreenBook"), book.getArgs());
        assertTrue(parse("close com.example.Outer$Inner").isAccepted(), "a nested class");
        assertBad("close");
        assertBad("close a b");
        assertBad("close 1bad.Name");
        assertBad("close a..b");
        StringBuilder longName = new StringBuilder("a");
        for (int i = 0; i < 200; i++) {
            longName.append('b');
        }
        assertBad("close " + longName);
    }

    @Test
    @DisplayName("open takes onQuestion=report|confirm|cancel as its last word, report by default")
    void openAnswer() {
        assertEquals(MenuRequest.REPORT, parse("open test1").getOnQuestion());
        MenuRequest confirm = parse("open New World onQuestion=confirm");
        assertTrue(confirm.isAccepted());
        assertEquals("New World", confirm.getArgs().get(0), "the folder is the words before it");
        assertEquals(MenuRequest.CONFIRM, confirm.getOnQuestion());
        assertEquals(MenuRequest.CANCEL, parse("open x onQuestion=cancel").getOnQuestion());
        assertEquals(MenuRequest.REPORT, parse("open x onQuestion=report").getOnQuestion());
        assertBad("open x onQuestion=yes");
        assertBad("open x onQuestion=");
        assertBad("open onQuestion=confirm");
        assertBad("open x onQuestion=confirm y");
    }

    @Test
    @DisplayName("screenshot takes a label and a hide set")
    void screenshot() {
        MenuRequest plain = parse("screenshot title1");
        assertTrue(plain.isAccepted());
        assertEquals("title1", plain.getArgs().get(0));
        assertEquals("", plain.getHide());
        assertEquals("chat,toasts", parse("screenshot t hide=toasts,chat").getHide(), "written in a fixed order");
        assertEquals("hud", parse("screenshot t hide=hud").getHide());
        assertBad("screenshot");
        assertBad("screenshot a/b");
        assertBad("screenshot a.png");
        assertBad("screenshot t hide=all");
        assertBad("screenshot t hide=");
        assertBad("screenshot t hide=chat,chat");
        assertBad("screenshot t hide=chat hide=hud");
        assertBad("screenshot t screenshot=u");
        assertBad("screenshot t extra");
    }

    @Test
    @DisplayName("mods takes a mod id, then screenshot= and hide=, hide only with a picture")
    void mods() {
        MenuRequest plain = parse("mods agenttesttoolkit");
        assertTrue(plain.isAccepted());
        assertEquals("agenttesttoolkit", plain.getArgs().get(0));
        assertEquals("", plain.getScreenshot());
        MenuRequest picture = parse("mods agenttesttoolkit hide=toasts screenshot=modlist");
        assertTrue(picture.isAccepted(), "the options in any order");
        assertEquals("modlist", picture.getScreenshot());
        assertEquals("toasts", picture.getHide());
        assertTrue(parse("mods FML").isAccepted(), "an id as the mod declares it");
        assertBad("mods");
        assertBad("mods agenttesttoolkit hide=toasts");
        assertBad("mods agenttesttoolkit screenshot=a/b");
        assertBad("mods agenttesttoolkit screenshot=a screenshot=b");
        assertBad("mods agent test");
        assertBad("mods a:b");
    }

    @Test
    @DisplayName("the menu steps keep their forms, and anything else is still not a step")
    void others() {
        assertEquals(MenuRequest.REASON_VERB_NOT_ALLOWED, parse("click button 0").getRefusal());
        assertEquals(MenuRequest.REASON_VERB_NOT_ALLOWED, parse("tooltip container 0").getRefusal());
        assertTrue(parse("quit").isAccepted());
        assertTrue(parse("open New World").isAccepted());
    }
}
