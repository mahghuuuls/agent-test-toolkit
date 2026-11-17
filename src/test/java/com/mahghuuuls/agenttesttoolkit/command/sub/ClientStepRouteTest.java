package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.command.sub.ClientRequestGate.Facts;
import com.mahghuuuls.agenttesttoolkit.command.sub.ClientRequestGate.Refusal;
import com.mahghuuuls.agenttesttoolkit.command.sub.ClientStepRoute.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Which message each command's words travel in, and what a 1.8.0 client then gets. */
class ClientStepRouteTest {

    private static final Facts EIGHT = new Facts(false, false, true, Boolean.TRUE, "1.8.0");
    private static final Facts NINE = new Facts(false, false, true, Boolean.TRUE, "1.9.0");

    private static ClientStepRoute route(String line) {
        return ClientStepRoute.of(line.split(" "));
    }

    private static Refusal refusalFor(String line, Facts facts) {
        return ClientRequestGate.refusal(route(line).gateStep(false), facts);
    }

    @Test
    @DisplayName("a plain use or attack stays a player action and still reaches a 1.8.0 client")
    void plainClicks() {
        for (String line : new String[] {"use", "attack", "use route=binding", "attack route=binding keepHold=false",
                "use delay=5 screenshot=a", "Use"}) {
            assertEquals(Message.PLAYER_ACTION, route(line).message, line);
            assertNull(refusalFor(line, EIGHT), line);
        }
        assertEquals("use", route("use route=binding delay=5").action.getStep());
        assertEquals(5, route("use route=binding delay=5").action.getDelay());
    }

    @Test
    @DisplayName("a click with another route or keepHold needs the input message, refused to 1.8.0 and sent to 1.9.0")
    void routedClicks() {
        for (String line : new String[] {"use route=event", "attack route=queue", "attack keepHold=true",
                "use route=binding keepHold=true"}) {
            assertEquals(Message.INPUT, route(line).message, line);
            assertEquals(Refusal.CLIENT_TOO_OLD, refusalFor(line, EIGHT), line);
            assertNull(refusalFor(line, NINE), line);
        }
    }

    @Test
    @DisplayName("the screen steps, the other input steps, the player actions and the lifecycle steps each find their message")
    void otherSteps() {
        assertEquals(Message.SCREEN, route("click slot 3").message);
        assertEquals(Message.SCREEN, route("tooltip container 3").message);
        assertEquals(Message.PLAYER_ACTION, route("tooltip 3").message, "the inventory tooltip is unchanged");
        assertEquals(Message.INPUT, route("key key.jump press").message);
        assertEquals(Message.PLAYER_ACTION, route("hold use 20").message);
        assertEquals(Message.LIFECYCLE, route("reconnect 3 logout").message);
        assertEquals(Refusal.CLIENT_TOO_OLD, refusalFor("click slot 3", EIGHT));
        assertNull(refusalFor("tooltip 3 screenshot=a screenshotHide=hud", EIGHT));
        assertNull(ClientRequestGate.refusal(route("reconnect").gateStep(true), EIGHT));
    }

    @Test
    @DisplayName("words that are no step at all, or a step written wrongly, ask for the usage")
    void usage() {
        for (String line : new String[] {"move", "use route=mouse", "key key.jump", "click slot", "tooltip container",
                "select 9", ""}) {
            assertNull(route(line), line);
        }
        assertNull(ClientStepRoute.of(new String[0]));
    }

    @Test
    @DisplayName("a hold with keepHold needs the input message; the 1.8.0 hold still reaches an older client")
    void holdsBesideHolds() {
        for (String line : new String[] {"hold use 20", "hold attack 40 keepHold=false", "hold use 20 delay=5"}) {
            assertEquals(Message.PLAYER_ACTION, route(line).message, line);
            assertNull(refusalFor(line, EIGHT), line);
        }
        assertEquals(20, route("hold use 20 delay=5").action.getTicks());
        assertEquals(5, route("hold use 20 delay=5").action.getDelay());
        for (String line : new String[] {"hold use 20 keepHold=true", "key key.sneak hold 30 keepHold=true"}) {
            assertEquals(Message.INPUT, route(line).message, line);
            assertEquals(Refusal.CLIENT_TOO_OLD, refusalFor(line, EIGHT), line);
            assertNull(refusalFor(line, NINE), line);
        }
    }
}
