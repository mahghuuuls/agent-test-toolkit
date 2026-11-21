package com.mahghuuuls.agenttesttoolkit.command;

import com.mahghuuuls.agenttesttoolkit.bundle.CommandOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The success and failure rule for a dispatched command.
 *
 * <p>This is the most consequential decision in bundle execution and the least visible one. It
 * cannot be read off {@code executeCommand}'s return value, because vanilla returns zero for
 * four unrelated situations, and getting it wrong is silent: bundles would either halt on
 * harmless commands or march past real errors.
 *
 * <p>Established by inspection of the 1.12.2 sources. {@code CommandHandler.executeCommand}
 * catches {@link net.minecraft.command.CommandException} internally and messages the sender, so
 * the translation key is the only thing separating the cases.
 */
class CommandOutcomesTest {

    @Test
    @DisplayName("any successful execution is a success")
    void positiveCountSucceeds() {
        assertTrue(CommandOutcomes.classify(1, null).succeeded());
        // A selector matching several entities executes once per entity.
        assertTrue(CommandOutcomes.classify(7, null).succeeded());
    }

    private static final String[] KILL_ARGS = {"@e[name=probe]"};

    @Test
    @DisplayName("a kill whose selector matched nothing is a success, not a failure")
    void selectorMatchedNothingSucceeds() {
        // Vanilla raises PlayerNotFoundException from CommandHandler itself when it expands a
        // selector to an empty list, so a teardown bundle ending in kill @e[name=...] would
        // otherwise halt on its second run under the default stopOnFailure.
        CommandOutcome outcome = CommandOutcomes.classify(0, CommandOutcomes.KEY_SELECTOR_NOT_FOUND,
                java.util.Collections.<String>emptySet(), "kill", KILL_ARGS);

        assertTrue(outcome.succeeded());
        assertEquals(CommandOutcome.SOURCE_BUILT_IN, outcome.getToleratedSource());
        assertNotNull(outcome.getDetail(), "the empty match is still worth recording");
    }

    @Test
    @DisplayName("entity and player not-found are tolerated the same way for a kill")
    void entityAndPlayerNotFoundSucceed() {
        assertTrue(CommandOutcomes.classify(0, CommandOutcomes.KEY_ENTITY_NOT_FOUND,
                java.util.Collections.<String>emptySet(), "kill", KILL_ARGS).succeeded());
        assertTrue(CommandOutcomes.classify(0, CommandOutcomes.KEY_PLAYER_NOT_FOUND,
                java.util.Collections.<String>emptySet(), "kill", KILL_ARGS).succeeded());
    }

    @Test
    @DisplayName("a check command that matched nothing fails, and so does a command the classifier is not told")
    void checkMatchingNothingFails() {
        CommandOutcome testfor = CommandOutcomes.classify(0, CommandOutcomes.KEY_SELECTOR_NOT_FOUND,
                java.util.Collections.<String>emptySet(), "testfor", new String[] {"@e[tag=nothing_here]"});
        assertFalse(testfor.succeeded());
        assertEquals(CommandOutcomes.KEY_SELECTOR_NOT_FOUND, testfor.getDetail(), "the key is the failure's detail");
        assertFalse(CommandOutcomes.classify(0, CommandOutcomes.KEY_SELECTOR_NOT_FOUND).succeeded());
    }

    @Test
    @DisplayName("an unknown command is a failure despite the similar key name")
    void unknownCommandFails() {
        // commands.generic.notFound means "no such command"; the tolerated keys are
        // commands.generic.*.notFound. They differ by one path segment, so the keys are
        // compared exactly. A prefix or suffix test here would silently swallow typos in
        // every bundle.
        CommandOutcome outcome =
                CommandOutcomes.classify(0, CommandOutcomes.KEY_COMMAND_NOT_FOUND);

        assertFalse(outcome.succeeded());
        assertEquals("no such command", outcome.getDetail());
    }

    @Test
    @DisplayName("a command the caller lacks permission for is a failure")
    void noPermissionFails() {
        CommandOutcome outcome = CommandOutcomes.classify(0, CommandOutcomes.KEY_NO_PERMISSION);
        assertFalse(outcome.succeeded());
        assertEquals("caller lacks permission", outcome.getDetail());
    }

    @Test
    @DisplayName("invalid syntax is a failure and keeps vanilla's key as the detail")
    void badSyntaxFails() {
        CommandOutcome outcome = CommandOutcomes.classify(0, "commands.generic.usage");
        assertFalse(outcome.succeeded());
        assertEquals("commands.generic.usage", outcome.getDetail());
    }

    @Test
    @DisplayName("zero with no key at all is a failure that says so plainly")
    void noKeyFails() {
        // Report the failure rather than guess at a cause that was never given.
        CommandOutcome outcome = CommandOutcomes.classify(0, null);
        assertFalse(outcome.succeeded());
        assertTrue(outcome.getDetail().contains("no reason"), outcome.getDetail());
    }

    @Test
    @DisplayName("a tolerated key only applies when nothing executed")
    void toleratedKeyDoesNotMaskRealExecution() {
        // A multi-target command can message about one missing target and still act on others.
        // The count wins in that case.
        assertTrue(CommandOutcomes.classify(3, CommandOutcomes.KEY_ENTITY_NOT_FOUND).succeeded());
    }
}
