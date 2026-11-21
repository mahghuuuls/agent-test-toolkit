package com.mahghuuuls.agenttesttoolkit.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The table of commands that may match nothing without a declaration. The negative cases matter
 * most: a check command that passes on an empty match reports success for a test that found
 * nothing, which is the defect this table replaced.
 */
class BuiltInToleranceTest {

    private static final String SELECTOR = CommandOutcomes.KEY_SELECTOR_NOT_FOUND;
    private static final String[] KEYS = {SELECTOR, CommandOutcomes.KEY_ENTITY_NOT_FOUND,
            CommandOutcomes.KEY_PLAYER_NOT_FOUND, CommandOutcomes.KEY_CLEAR_NOTHING,
            CommandOutcomes.KEY_EFFECT_NOT_ACTIVE, CommandOutcomes.KEY_EFFECT_NOT_ACTIVE_ALL};

    private static boolean tolerates(String command, String key) {
        String[] parts = command.split(" ");
        return BuiltInTolerance.tolerates(parts[0], java.util.Arrays.copyOfRange(parts, 1, parts.length), key);
    }

    @Test
    @DisplayName("each removal command in the table is tolerated for each nothing-matched key")
    void removalsAreTolerated() {
        String[] removals = {"kill @e[tag=nothing_here]", "clear @p", "effect @s clear",
                "scoreboard players reset @e[tag=nothing_here]", "scoreboard players reset @a obj",
                "scoreboard players tag @e[tag=nothing_here] remove probe", "scoreboard PLAYERS Tag @e remove x"};
        for (String command : removals) {
            for (String key : KEYS) {
                assertTrue(tolerates(command, key), command + " / " + key);
            }
        }
    }

    @Test
    @DisplayName("check and other commands are not tolerated: testfor, execute, tp, teleport, give and the rest")
    void othersFail() {
        String[] others = {"testfor @e[tag=nothing_here]", "execute @e[tag=nothing_here] ~ ~ ~ say hi",
                "tp @e[tag=nothing_here] 0 64 0", "teleport @e[tag=nothing_here] 0 64 0",
                "give @p[tag=nothing_here] stone", "enchant @p sharpness", "title @a[tag=x] title hi",
                "scoreboard players test @p obj 1", "scoreboard players set @e[tag=x] obj 1",
                "somemod:custom @e[tag=x]", "devtool assert entities count 1"};
        for (String command : others) {
            for (String key : KEYS) {
                assertFalse(tolerates(command, key), command + " / " + key);
            }
        }
    }

    @Test
    @DisplayName("effect without clear, such as removing one named effect, is not tolerated")
    void effectWithoutClear() {
        assertFalse(tolerates("effect @s minecraft:speed 0", CommandOutcomes.KEY_EFFECT_NOT_ACTIVE));
        assertFalse(tolerates("effect @s minecraft:speed 0", SELECTOR));
        assertFalse(tolerates("effect @s", SELECTOR), "too short to be a clear");
        assertFalse(tolerates("effect clear @s", SELECTOR), "clear in the target's place");
        assertFalse(tolerates("effect @s CLEAR", SELECTOR), "vanilla reads only clear, exactly");
    }

    @Test
    @DisplayName("scoreboard players tag ... add, or list, is not a removal")
    void tagAddIsNotRemoval() {
        assertFalse(tolerates("scoreboard players tag @e[tag=x] add probe", SELECTOR));
        assertFalse(tolerates("scoreboard players tag @e[tag=x] list", SELECTOR));
        assertFalse(tolerates("scoreboard players tag @e[tag=x]", SELECTOR));
        assertFalse(tolerates("scoreboard objectives remove obj", SELECTOR), "not the players form");
        assertFalse(tolerates("scoreboard players tag @e REMOVE x", SELECTOR), "vanilla reads only remove, exactly");
    }

    @Test
    @DisplayName("a removal is tolerated only for the nothing-matched keys, never for another failure")
    void onlyTheListedKeys() {
        assertFalse(tolerates("kill @e[tag=x]", "commands.generic.usage"));
        assertFalse(tolerates("kill @e[tag=x]", CommandOutcomes.KEY_COMMAND_NOT_FOUND));
        assertFalse(tolerates("clear @p stone", "commands.generic.syntax"));
        assertFalse(tolerates("kill @e[tag=x]", null));
        assertFalse(tolerates("kill @e[tag=x]", "commands.generic.selector.notFound.extra"));
        assertFalse(tolerates("kill @e[tag=x]", CommandOutcomes.KEY_NO_PERMISSION));
        assertFalse(tolerates("kill @e[tag=x]", "notFound"), "keys are compared whole");
    }

    @Test
    @DisplayName("an unknown command, a null name or null arguments are handled without guessing")
    void unknownAndNull() {
        assertFalse(BuiltInTolerance.tolerates(null, new String[0], SELECTOR));
        assertTrue(BuiltInTolerance.tolerates("kill", null, SELECTOR));
        assertFalse(BuiltInTolerance.tolerates("effect", null, SELECTOR));
        assertFalse(BuiltInTolerance.tolerates("Kill", new String[0], SELECTOR), "registered names are lower case");
    }
}
