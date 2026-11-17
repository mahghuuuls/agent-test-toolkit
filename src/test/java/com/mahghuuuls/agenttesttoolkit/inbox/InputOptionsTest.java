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

/** The words of the input steps: what each takes, its bounds, and what it refuses. */
class InputOptionsTest {

    private static InputOptions parse(String line) {
        return InputOptions.parse(line.split(" "));
    }

    private static void assertRoundTrip(String line) {
        InputOptions first = parse(line);
        assertNotNull(first, line);
        InputOptions again = InputOptions.parse(first.toWords().toArray(new String[0]));
        assertNotNull(again, "read back: " + first.toWords());
        assertEquals(first.toWords(), again.toWords(), line);
    }

    @Test
    @DisplayName("key presses, holds and releases a binding by name, with raw and keepHold where they mean something")
    void key() {
        InputOptions press = parse("key key.jump press");
        assertEquals("key", press.getStep());
        assertEquals("key.jump", press.getName());
        assertEquals("press", press.getMode());
        assertFalse(press.isRaw());
        InputOptions hold = parse("key key.forward hold 40 raw=true");
        assertEquals(40, hold.getTicks());
        assertTrue(hold.isRaw());
        assertEquals("release", parse("key waila.keybind.wailaconfig release").getMode());
        assertTrue(parse("key key.attack press keepHold=true").isKeepHold());
        assertNotNull(parse("key key.ashenskills.skill press raw=false"));
        assertNotNull(parse("key key.jump hold 1"));
        assertNotNull(parse("key key.jump hold 1200"));
        for (String bad : new String[] {"key", "key key.jump", "key key.jump tap", "key key.jump hold",
                "key key.jump hold 0", "key key.jump hold 1201", "key key.jump hold 05", "key key.jump press 3",
                "key key.jump release raw=true", "key key.jump hold 20 keepHold=true", "key key.jump press raw=yes",
                "key key=jump press", "key key.jump press route=event", "key key.jump press over=5",
                "key key.jump press raw=true raw=true"}) {
            assertNull(parse(bad), bad);
        }
    }

    @Test
    @DisplayName("keys lists every binding or those under a prefix, and takes nothing else")
    void keys() {
        assertEquals("", parse("keys").getName());
        assertEquals("key.", parse("keys key.").getName());
        assertNull(parse("keys key. extra"));
        assertNull(parse("keys delay=5"));
        assertNull(parse("keys key. screenshot=a"));
    }

    @Test
    @DisplayName("use and attack take a route and keepHold; only those need the newer message")
    void clicks() {
        assertEquals("queue", parse("attack route=queue").getRoute());
        assertTrue(parse("attack route=queue").travelsAsInput());
        assertTrue(parse("use route=event").travelsAsInput());
        assertTrue(parse("attack keepHold=true").travelsAsInput());
        assertFalse(parse("use").travelsAsInput(), "a plain click keeps going as a player action");
        assertFalse(parse("use route=binding").travelsAsInput());
        assertFalse(parse("use route=binding keepHold=false delay=5").travelsAsInput());
        assertArrayEquals(new String[] {"use", "delay=5"},
                parse("use route=binding keepHold=false delay=5").toPlayerActionWords());
        assertNotNull(PlayerActionOptions.parse(parse("attack route=binding screenshot=a").toPlayerActionWords()));
        for (String bad : new String[] {"use route=mouse", "use now", "attack Developer", "use keepHold=1",
                "use raw=true", "use over=5", "use route=event route=queue"}) {
            assertNull(parse(bad), bad);
        }
    }

    @Test
    @DisplayName("look takes an absolute yaw and pitch, pitch -90 to 90, over 0 to 200 ticks")
    void look() {
        InputOptions look = parse("look 90 0");
        assertEquals(90.0, look.getYaw());
        assertEquals(0.0, look.getPitch());
        assertEquals(0, look.getOver());
        assertEquals(20, parse("look 0 0 over=20").getOver());
        assertEquals(-45.5, parse("look -45.5 -90").getYaw());
        assertEquals(12.345, parse("look 12.345 89.999").getYaw());
        assertNotNull(parse("look 360 90 over=200"));
        assertNotNull(parse("look -360 -90 over=0"));
        for (String bad : new String[] {"look", "look 90", "look 90 91", "look 361 0", "look 90 -90.5",
                "look 1.2345 0", "look a b", "look 90 0 over=201", "look 90 0 over=-1", "look 090 0", "look 90 0 5"}) {
            assertNull(parse(bad), bad);
        }
    }

    @Test
    @DisplayName("face takes a selector or a UUID, or a block with an optional face")
    void face() {
        assertEquals("@e[type=zombie,c=1]", parse("face @e[type=zombie,c=1]").getName());
        assertEquals("@p", parse("face @p").getName());
        assertFalse(parse("face @p").isFaceBlock());
        assertNotNull(parse("face 123e4567-e89b-12d3-a456-426614174000"));
        InputOptions block = parse("face block 10 64 -20 up");
        assertTrue(block.isFaceBlock());
        assertEquals(10, block.getBlockX());
        assertEquals(64, block.getBlockY());
        assertEquals(-20, block.getBlockZ());
        assertEquals("up", block.getSide());
        assertEquals("", parse("face block 0 0 0").getSide());
        assertNotNull(parse("face block 30000000 255 -30000000 screenshot=s"));
        for (String bad : new String[] {"face", "face Developer", "face block 1 2", "face block 1 256 3",
                "face block 1 -1 3", "face block 30000001 1 1", "face block 1 2 3 sideways", "face @x",
                "face @e[type=zombie c=1]", "face block 1.5 2 3"}) {
            assertNull(parse(bad), bad);
        }
    }

    @Test
    @DisplayName("view and focus take one of their words")
    void viewAndFocus() {
        for (String view : new String[] {"first", "back", "front"}) {
            assertEquals(view, parse("view " + view).getMode());
        }
        assertEquals("lost", parse("focus lost").getMode());
        assertEquals("gained", parse("focus gained delay=20").getMode());
        assertNull(parse("view"));
        assertNull(parse("view third"));
        assertNull(parse("focus"));
        assertNull(parse("focus off"));
    }

    @Test
    @DisplayName("the wait and the picture follow the player actions' bounds")
    void extras() {
        InputOptions key = parse("key key.jump press delay=40 screenshot=jump screenshotDelay=5 screenshotHide=hud");
        assertEquals(40, key.getDelay());
        assertEquals("jump", key.getScreenshot());
        assertEquals(5, key.getScreenshotDelay());
        assertEquals("hud", key.getScreenshotHide());
        for (String bad : new String[] {"view back delay=1201", "view back screenshotDelay=5", "view back screenshotHide=chat",
                "view back screenshot=a/b", "view back screenshot=a screenshotDelay=201", "view back delay=1 delay=2",
                "view back colour=red", "view back =5"}) {
            assertNull(parse(bad), bad);
        }
    }

    @Test
    @DisplayName("each step reads back from its own words unchanged, and only living players take most of them")
    void roundTripsAndLiving() {
        for (String line : Arrays.asList("key key.jump press raw=true keepHold=true", "key key.forward hold 40 delay=3",
                "keys", "keys key.", "use route=queue keepHold=true screenshot=a screenshotDelay=2",
                "look -45.5 12.25 over=20", "look 0 -0", "face @e[type=zombie,c=1]", "face block -1 2 3 east",
                "view front", "focus lost")) {
            assertRoundTrip(line);
        }
        assertEquals(Arrays.asList("look", "0", "0"), parse("look -0 0.000").toWords());
        assertEquals(Arrays.asList("look", "90", "1.5"), parse("look 90.0 1.50").toWords());
        assertFalse(InputOptions.needsLivingPlayer("keys"));
        assertFalse(InputOptions.needsLivingPlayer("view"));
        assertTrue(InputOptions.needsLivingPlayer("key"));
        assertTrue(InputOptions.needsLivingPlayer("focus"));
        assertNull(InputOptions.parse(new String[0]));
        assertNull(InputOptions.parse(null));
        assertNull(parse("Key key.jump press"), "steps are lower case");
        assertNull(parse("select 3"), "a player action is not an input step");
    }
}
