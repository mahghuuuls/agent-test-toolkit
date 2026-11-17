package com.mahghuuuls.agenttesttoolkit.client;

import com.mahghuuuls.agenttesttoolkit.inbox.InputOptions;
import com.mahghuuuls.agenttesttoolkit.inbox.PlayerActionOptions;
import com.mahghuuuls.agenttesttoolkit.net.InputRequest;
import com.mahghuuuls.agenttesttoolkit.net.PlayerActionRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a received step does to the running holds, read from the very messages the client takes:
 * the decision the client's step handling acts on before anything else.
 */
class AskedTest {

    private static Asked input(String line) {
        return new Asked(new InputRequest(InputOptions.parse(line.split(" ")), "tok", 5L));
    }

    private static Asked action(String line) {
        return new Asked(new PlayerActionRequest(PlayerActionOptions.parse(line.split(" ")), "tok", 5L));
    }

    @Test
    @DisplayName("keepHold on a click, a press or a hold keeps the running holds; without it they end as the next action")
    void keepHoldIsRead() {
        assertEquals(HoldRelease.Ending.NONE, input("attack route=event keepHold=true").ending());
        assertEquals(HoldRelease.Ending.NONE, input("key key.attack press keepHold=true").ending());
        assertEquals(HoldRelease.Ending.NONE, input("key key.sneak hold 30 keepHold=true").ending());
        assertEquals(HoldRelease.Ending.NONE, input("hold use 40 keepHold=true").ending());
        assertEquals(HoldRelease.Ending.ALL_NEXT_ACTION, input("attack route=event").ending());
        assertEquals(HoldRelease.Ending.ALL_NEXT_ACTION, input("key key.forward hold 60").ending());
        assertEquals(HoldRelease.Ending.ALL_NEXT_ACTION, action("hold use 40").ending(), "a player action keeps nothing");
        assertEquals(HoldRelease.Ending.ALL_NEXT_ACTION, action("attack").ending());
    }

    @Test
    @DisplayName("focus lost ends the holds as a loss of focus, a release only its own, listing and the view nothing")
    void otherEndings() {
        assertEquals(HoldRelease.Ending.ALL_FOCUS_LOST, input("focus lost").ending());
        assertEquals(HoldRelease.Ending.NONE, input("focus gained").ending());
        assertEquals(HoldRelease.Ending.NAMED_RELEASED, input("key key.forward release").ending());
        assertEquals(HoldRelease.Ending.NONE, input("keys key.").ending());
        assertEquals(HoldRelease.Ending.NONE, input("view back").ending());
        assertEquals(HoldRelease.Ending.ALL_NEXT_ACTION, input("look 90 0 over=20").ending());
    }
}
