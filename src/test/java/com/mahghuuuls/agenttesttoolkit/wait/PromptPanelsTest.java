package com.mahghuuuls.agenttesttoolkit.wait;

import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.net.PromptPanelMessage;
import com.mahghuuuls.agenttesttoolkit.session.SessionManager;
import com.mahghuuuls.agenttesttoolkit.state.ToolkitState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The prompt panels the server keeps, one per player, and every way one is taken away: a hide
 * with its token, an acknowledgement or a reset taking them all, a player leaving, the server
 * stopping. Pinned: a late hide never removes a newer panel, two players' panels are apart, and
 * a session replaced by a new start leaves no panel up.
 */
class PromptPanelsTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BO = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final RecordContext.Snapshot CONTEXT = new RecordContext.Snapshot(RecordContext.SERVER, 10L);

    /** The hides sent, as "player token". */
    private final List<String> sent = new ArrayList<String>();

    @BeforeEach
    void setUp() {
        PromptPanels.forgetAll();
        PromptPanels.setHider(new PromptPanels.Hider() {
            @Override
            public void hide(UUID player, String token) {
                sent.add(player + " " + token);
            }
        });
        ToolkitState.resetForTesting();
        ToolkitLog.startCaptureForTesting();
    }

    @AfterEach
    void tearDown() {
        SessionManager.setStopHook(null);
        PromptPanels.setHider(null);
        PromptPanels.forgetAll();
        OperatorCheckpoints.reset();
        ToolkitLog.stopCaptureForTesting();
        ToolkitState.resetForTesting();
    }

    @Test
    @DisplayName("a late hide for a replaced panel is ignored; the newer panel's own hide takes it")
    void lateHideIgnored() {
        PromptPanels.shown(ALEX, "old");
        PromptPanels.shown(ALEX, "new");
        assertFalse(PromptPanels.hide(ALEX, "old"), "the old panel was already replaced");
        assertEquals("new", PromptPanels.current(ALEX));
        assertEquals(Collections.<String>emptyList(), sent, "nothing sent that could reach the newer panel");

        assertTrue(PromptPanels.hide(ALEX, "new"));
        assertNull(PromptPanels.current(ALEX));
        assertEquals(Arrays.asList(ALEX + " new"), sent);
        assertFalse(PromptPanels.hide(ALEX, "new"), "hidden once");
        assertEquals(1, sent.size());
    }

    @Test
    @DisplayName("a cue to one player then an arm to another: each panel is hidden on its own")
    void twoPlayersApart() {
        PromptPanels.shown(ALEX, "cue");
        PromptPanels.shown(BO, "arm");
        assertFalse(PromptPanels.hide(ALEX, "arm"), "a token hides only its own player's panel");

        assertTrue(PromptPanels.hide(BO, "arm"), "the arm's wait ended");
        assertEquals("cue", PromptPanels.current(ALEX), "the cue is still up");
        assertTrue(PromptPanels.hide(ALEX, "cue"));
        assertEquals(Arrays.asList(BO + " arm", ALEX + " cue"), sent);
    }

    @Test
    @DisplayName("hiding them all, as an acknowledgement does, reaches every player's panel")
    void hideAllReachesEveryone() {
        PromptPanels.shown(ALEX, "cue");
        PromptPanels.shown(BO, "arm");
        assertEquals(2, PromptPanels.hideAll());
        assertEquals(new HashSet<String>(Arrays.asList(ALEX + " cue", BO + " arm")), new HashSet<String>(sent));
        assertNull(PromptPanels.current(ALEX));
        assertNull(PromptPanels.current(BO));
        assertEquals(0, PromptPanels.hideAll(), "nothing left");
    }

    @Test
    @DisplayName("a replacing session start runs the reset hook, and the reset takes every panel")
    void replacingStartHides() {
        // The stop hook as the mod installs it.
        SessionManager.setStopHook(new Runnable() {
            @Override
            public void run() {
                OperatorCheckpoints.reset();
            }
        });
        SessionManager.start("first", CONTEXT);
        PromptPanels.shown(ALEX, "cue");
        PromptPanels.shown(BO, "arm");

        SessionManager.start("second", CONTEXT);

        assertEquals(new HashSet<String>(Arrays.asList(ALEX + " cue", BO + " arm")), new HashSet<String>(sent));
        assertNull(PromptPanels.current(ALEX));
        assertNull(PromptPanels.current(BO));
    }

    @Test
    @DisplayName("a player who leaves, and a stopping server, forget panels without sending a hide")
    void forgetSendsNothing() {
        PromptPanels.shown(ALEX, "cue");
        PromptPanels.shown(BO, "arm");
        PromptPanels.forget(ALEX);
        assertNull(PromptPanels.current(ALEX));
        assertFalse(PromptPanels.hide(ALEX, "cue"), "a late hide after the leave sends nothing");
        assertEquals("arm", PromptPanels.current(BO), "the other player's panel stays");

        PromptPanels.forgetAll();
        assertNull(PromptPanels.current(BO));
        assertEquals(Collections.<String>emptyList(), sent);
        assertEquals(0, PromptPanels.hideAll(), "nothing is left for the next world");
    }

    @Test
    @DisplayName("the client drops its panel only for a hide carrying that panel's token")
    void clientTokenCheck() {
        assertTrue(PromptPanelMessage.hide("abc").hides("abc"));
        assertFalse(PromptPanelMessage.hide("abc").hides("def"), "a late hide for an older panel");
        assertFalse(PromptPanelMessage.hide("abc").hides(null), "no panel shown");
        assertFalse(PromptPanelMessage.show("abc", "label", null, 0).hides("abc"), "a show is not a hide");
    }
}
