package com.mahghuuuls.agenttesttoolkit.command.sub;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A waiting step travels in the message, and with the words, the same step without the wait does:
 * the client never sees {@code wait}, {@code waitTicks} or {@code expect}, so a 1.9.0 client reads
 * it, and a step without them is routed exactly as before. And the input a hold is awaited by.
 */
class ClientWaitRouteTest {

    private static ClientStepRoute route(String line) {
        return ClientStepRoute.of(line.split(" "));
    }

    @Test
    @DisplayName("a key's hold with wait goes as the same input step, its words without the wait")
    void keyHoldWords() {
        ClientStepRoute waiting = route("key key.sneak hold 215 keepHold=true wait=started waitTicks=300");
        ClientStepRoute plain = route("key key.sneak hold 215 keepHold=true");
        assertEquals(ClientStepRoute.Message.INPUT, waiting.message);
        assertEquals(plain.input.toWords(), waiting.input.toWords());
        assertEquals(Arrays.asList("key", "key.sneak", "hold", "215", "keepHold=true"), waiting.input.toWords());
        assertEquals("started", waiting.wait.getWait());
        assertEquals(300, waiting.wait.getWaitTicks());
        assertFalse(plain.wait.waits());
        assertEquals("key.sneak", waiting.heldInput());
    }

    @Test
    @DisplayName("a click or hold that a 1.7.0 client takes still goes as a player action when it waits or expects")
    void playerActionsStayPlayerActions() {
        ClientStepRoute hold = route("hold use 40 wait=done");
        assertEquals(ClientStepRoute.Message.PLAYER_ACTION, hold.message);
        assertEquals("use", hold.heldInput());
        ClientStepRoute attack = route("attack expect=11111111-2222-3333-4444-555555555555");
        assertEquals(ClientStepRoute.Message.PLAYER_ACTION, attack.message);
        assertEquals("11111111-2222-3333-4444-555555555555", attack.wait.getExpect());
        assertNull(attack.heldInput());
        ClientStepRoute queued = route("attack expect=@e[c=1] route=queue");
        assertEquals(ClientStepRoute.Message.INPUT, queued.message);
        assertEquals(Arrays.asList("attack", "route=queue"), queued.input.toWords());
        assertEquals("attack", route("hold attack 20 keepHold=true").heldInput());
    }

    @Test
    @DisplayName("screen steps keep their own expect and take wait; a step that takes no wait is refused with it")
    void screenAndRefusals() {
        ClientStepRoute click = route("click slot 3 expect=net.minecraft.client.gui.inventory.GuiChest wait=done");
        assertEquals(ClientStepRoute.Message.SCREEN, click.message);
        assertTrue(click.screen.toWords().contains("expect=net.minecraft.client.gui.inventory.GuiChest"));
        assertFalse(click.screen.toWords().contains("wait=done"));
        assertTrue(click.wait.waits());
        assertNull(route("focus lost wait=done"));
        assertNull(route("key key.sneak release wait=done"));
        assertNull(route("face block 1 64 1 expect=@e[c=1]"));
        assertNull(route("use waitTicks=20"));
    }

    @Test
    @DisplayName("no hold, no awaited input")
    void heldInputOnlyForHolds() {
        assertNull(route("key key.jump press").heldInput());
        assertNull(route("look 10 0 over=20").heldInput());
        assertNull(route("use").heldInput());
    }

    @Test
    @DisplayName("in a run, a step is kept when it waits, and every hold is kept for a later await; nothing else is")
    void keptInRun() {
        assertTrue(route("hold use 40").keptInRun(), "a hold without a wait, for client await");
        assertTrue(route("key key.sneak hold 100 keepHold=true").keptInRun());
        assertTrue(route("use wait=done").keptInRun());
        assertTrue(route("attack expect=@e[c=1]").keptInRun());
        assertFalse(route("use").keptInRun());
        assertFalse(route("key key.jump press").keptInRun());
        assertFalse(route("hold use 40").waits(), "kept, but the bundle does not pause for it");
        assertTrue(route("look 0 0 wait=started").waits());
    }

    @Test
    @DisplayName("face with expect is refused for a client below 1.10.0, which never reports what it faces")
    void faceExpectNeedsNewClient() {
        ClientStepRoute face = route("face @e[c=1] expect=@e[type=minecraft:pig,c=1]");
        assertEquals("devtool.client.expectNeedsNewClient", ClientSubCommand.expectRefusal(face, null));
        assertEquals("devtool.client.expectNeedsNewClient", ClientSubCommand.expectRefusal(face, "1.9.0"));
        assertNull(ClientSubCommand.expectRefusal(face, "1.10.0"));
        assertNull(ClientSubCommand.expectRefusal(face, "1.11.0"));
        assertNull(ClientSubCommand.expectRefusal(route("face @e[c=1] wait=done"), "1.9.0"), "no expect, no refusal");
        assertNull(ClientSubCommand.expectRefusal(route("attack expect=@e[c=1]"), "1.9.0"),
                "a click reports its target from 1.7.0");
        assertNull(ClientSubCommand.expectRefusal(route("hold use 20 expect=@e[c=1]"), "1.9.0"));
    }
}
