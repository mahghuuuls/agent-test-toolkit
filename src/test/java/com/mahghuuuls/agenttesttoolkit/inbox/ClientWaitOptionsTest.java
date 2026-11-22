package com.mahghuuuls.agenttesttoolkit.inbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bundle wait's words: their bounds, which steps take them, and that they are taken out of the
 * words the client is sent, so a step without them is exactly the step a 1.9.0 client reads.
 */
class ClientWaitOptionsTest {

    private static ClientWaitOptions split(String line) {
        return ClientWaitOptions.split(line.split(" "));
    }

    @Test
    @DisplayName("waitTicks is 1 to 1200 without leading zeros, 200 when not given")
    void waitTicksBounds() {
        assertEquals(200, split("use wait=done").getWaitTicks());
        assertEquals(1, split("use wait=done waitTicks=1").getWaitTicks());
        assertEquals(1200, split("use wait=done waitTicks=1200").getWaitTicks());
        assertNull(split("use wait=done waitTicks=0"));
        assertNull(split("use wait=done waitTicks=1201"));
        assertNull(split("use wait=done waitTicks=0200"));
        assertNull(split("use wait=done waitTicks=-5"));
        assertNull(split("use wait=done waitTicks="));
    }

    @Test
    @DisplayName("wait is started or done; a key given twice, or waitTicks with nothing to wait for, is refused")
    void waitValues() {
        assertEquals("started", split("key key.sneak hold 215 wait=started").getWait());
        assertEquals("done", split("key key.sneak hold 215 wait=done").getWait());
        assertNull(split("use wait=finished"));
        assertNull(split("use wait=Done"));
        assertNull(split("use wait=done wait=started"));
        assertNull(split("use waitTicks=40"));
        ClientWaitOptions none = split("use");
        assertFalse(none.waits());
        assertEquals("", none.getWait());
    }

    @Test
    @DisplayName("the options are taken out of the step's words, wherever they stand, and the rest keep their order")
    void wordsWithoutTheOptions() {
        assertArrayEquals(new String[] {"key", "key.sneak", "hold", "215", "keepHold=true"},
                split("key key.sneak hold 215 wait=done keepHold=true waitTicks=300").getWords());
        assertArrayEquals(new String[] {"hold", "use", "40"}, split("hold use 40 expect=@e[type=cow,c=1]").getWords());
        String[] plain = "click slot 3 shift expect=net.minecraft.client.gui.inventory.GuiChest".split(" ");
        assertArrayEquals(plain, ClientWaitOptions.split(plain).getWords(), "a step without them is unchanged");
    }

    @Test
    @DisplayName("every step the bundle wait lists takes wait; a key's release, focus, keys and the lifecycle steps do not")
    void stepsThatTakeWait() {
        for (String step : new String[] {"select 1", "use", "attack", "hold attack 20", "drop", "inventory",
                "tooltip 3", "key key.jump press", "key key.jump hold 5", "look 0 0", "face @e[c=1]",
                "face block 1 2 3", "view back", "screen", "click slot 1", "scroll 2", "tooltip container 3"}) {
            ClientWaitOptions options = split(step + " wait=done");
            assertNotNull(options, step);
            assertTrue(options.waits(), step);
            assertEquals(step.split(" ").length, options.getWords().length, step);
        }
        for (String step : new String[] {"key key.jump release", "focus lost", "keys", "close", "respawn"}) {
            ClientWaitOptions options = split(step + " wait=done");
            assertFalse(options.waits(), step);
            assertTrue(Arrays.asList(options.getWords()).contains("wait=done"), "left for the step to refuse: " + step);
        }
    }

    @Test
    @DisplayName("expect is a selector or UUID on use, attack, hold and face at an entity, and waits as done does")
    void expectOption() {
        ClientWaitOptions attack = split("attack expect=@e[type=minecraft:zombie,c=1]");
        assertEquals("@e[type=minecraft:zombie,c=1]", attack.getExpect());
        assertTrue(attack.waits());
        assertEquals("done", attack.getWait());
        assertEquals("done", split("use expect=11111111-2222-3333-4444-555555555555 wait=done").getWait());
        assertEquals(300, split("face @p expect=@e[c=1] waitTicks=300").getWaitTicks());
        assertNull(split("use expect=@e[c=1] wait=started"), "a target is only known once the action is over");
        assertNull(split("use expect=Steve"), "not a selector or UUID");
        assertNull(split("use expect=@e[c=1] expect=@p"));
        assertFalse(split("face block 1 2 3 expect=@e[c=1]").waits(), "a block face keeps expect, and refuses it");
        assertFalse(split("drop expect=@e[c=1]").waits());
        assertEquals("", split("click button 1 expect=net.minecraft.client.gui.GuiMainMenu").getExpect(),
                "a screen step's expect names a screen class and stays the step's own");
    }

    @Test
    @DisplayName("client await takes an input and optional ticks, 1 to 1200, default 200")
    void awaitWords() {
        ClientWaitOptions.Await sneak = ClientWaitOptions.parseAwait("await key.sneak".split(" "));
        assertEquals("key.sneak", sneak.getInput());
        assertEquals(200, sneak.getWaitTicks());
        assertEquals(1200, ClientWaitOptions.parseAwait("await use 1200".split(" ")).getWaitTicks());
        assertNull(ClientWaitOptions.parseAwait("await use 1201".split(" ")));
        assertNull(ClientWaitOptions.parseAwait("await use 0".split(" ")));
        assertNull(ClientWaitOptions.parseAwait("await".split(" ")));
        assertNull(ClientWaitOptions.parseAwait("await use 20 more".split(" ")));
        assertNull(ClientWaitOptions.parseAwait("await wait=done".split(" ")), "not a binding name");
    }

    @Test
    @DisplayName("the inbox passes a waiting step and await on, for the command to refuse outside a bundle")
    void inboxPassesThem() {
        assertTrue(InboxRequest.parse("r1", "client key key.sneak hold 40 wait=done").isAccepted());
        assertEquals("devtool client key key.sneak hold 40 wait=done",
                InboxRequest.parse("r1", "client key key.sneak hold 40 wait=done").getCommandLine());
        assertTrue(InboxRequest.parse("r2", "client await key.sneak").isAccepted());
        assertEquals("badArguments", InboxRequest.parse("r3", "client use wait=soon").getRefusal());
        assertEquals("badArguments", InboxRequest.parse("r4", "client focus lost wait=done").getRefusal());
    }
}
