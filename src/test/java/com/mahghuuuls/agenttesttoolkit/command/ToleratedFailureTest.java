package com.mahghuuuls.agenttesttoolkit.command;

import java.util.Arrays;
import java.util.Collections;

import com.mahghuuuls.agenttesttoolkit.bundle.CommandOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Failures a bundle command declares it expects.
 *
 * <p>The rule this protects is narrow and easy to lose: a declaration tolerates the key it
 * names and nothing else. The tests that matter most here are the negative ones. A change that
 * made a declaration tolerate everything would pass any test asking only "was the expected
 * failure tolerated", and would reintroduce, per command, the defect this project spent a
 * release correcting: something reporting success while doing nothing.
 */
class ToleratedFailureTest {

    private static final String EFFECT_NOT_ACTIVE = "commands.effect.failure.notActive.all";
    private static final String SYNTAX = "commands.generic.syntax";

    @Test
    @DisplayName("a declared failure is tolerated and the bundle continues")
    void declaredFailureIsTolerated() {
        CommandOutcome outcome = CommandOutcomes.classify(
                0, EFFECT_NOT_ACTIVE, Collections.singleton(EFFECT_NOT_ACTIVE));

        assertTrue(outcome.succeeded(), "the author declared this exact failure");
    }

    @Test
    @DisplayName("a tolerated failure names the key rather than reading as a plain success")
    void toleratedFailureIsDistinguishable() {
        CommandOutcome outcome = CommandOutcomes.classify(
                0, EFFECT_NOT_ACTIVE, Collections.singleton(EFFECT_NOT_ACTIVE));

        String detail = outcome.getDetail();
        assertTrue(detail != null && detail.contains(EFFECT_NOT_ACTIVE),
                "a reader must be able to tell this from a command that simply worked: " + detail);
    }

    @Test
    @DisplayName("an undeclared failure on a declaring command still fails")
    void undeclaredFailureStillFails() {
        // The whole point of naming keys instead of a blanket flag. This command expects one
        // failure; a typo in it produces a different one and must not be swallowed.
        CommandOutcome outcome = CommandOutcomes.classify(
                0, SYNTAX, Collections.singleton(EFFECT_NOT_ACTIVE));

        assertFalse(outcome.succeeded(),
                "declaring one failure must not tolerate every other failure");
    }

    @Test
    @DisplayName("declaring nothing behaves exactly as before")
    void emptyDeclarationIsUnchanged() {
        CommandOutcome declared = CommandOutcomes.classify(
                0, EFFECT_NOT_ACTIVE, Collections.<String>emptySet());
        CommandOutcome legacy = CommandOutcomes.classify(0, EFFECT_NOT_ACTIVE);

        assertFalse(declared.succeeded());
        assertEquals(legacy.succeeded(), declared.succeeded(),
                "every bundle written before this feature must be unaffected");
    }

    @Test
    @DisplayName("the built-in tolerated keys still work without being declared")
    void builtInToleranceSurvives() {
        CommandOutcome outcome = CommandOutcomes.classify(
                0, CommandOutcomes.KEY_SELECTOR_NOT_FOUND, Collections.<String>emptySet());

        assertTrue(outcome.succeeded(),
                "a selector matching nothing is still success, declaration or not");
    }

    @Test
    @DisplayName("a successful command is unaffected by any declaration")
    void successIsUnaffected() {
        CommandOutcome outcome = CommandOutcomes.classify(
                1, null, Collections.singleton(EFFECT_NOT_ACTIVE));

        assertTrue(outcome.succeeded());
        assertFalse(outcome.getDetail() != null && outcome.getDetail().contains("tolerated"),
                "a command that worked must not be described as a tolerated failure");
    }

    @Test
    @DisplayName("several declared keys are all honoured")
    void multipleDeclaredKeys() {
        java.util.Collection<String> declared = Arrays.asList(EFFECT_NOT_ACTIVE, SYNTAX);

        assertTrue(CommandOutcomes.classify(0, EFFECT_NOT_ACTIVE, declared).succeeded());
        assertTrue(CommandOutcomes.classify(0, SYNTAX, declared).succeeded());
        assertFalse(CommandOutcomes.classify(0, "commands.generic.notFound", declared).succeeded(),
                "a key outside the declared list is still a failure");
    }

    @Test
    @DisplayName("a null key is not tolerated by any declaration")
    void nullKeyIsNotTolerated() {
        // Vanilla reported nothing usable. Tolerating that would silence failures whose cause
        // is unknown, which is the opposite of what a declaration is for.
        CommandOutcome outcome = CommandOutcomes.classify(
                0, null, Collections.singleton(EFFECT_NOT_ACTIVE));

        assertFalse(outcome.succeeded());
    }
}
