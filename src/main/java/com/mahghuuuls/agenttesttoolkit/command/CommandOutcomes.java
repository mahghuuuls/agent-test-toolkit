package com.mahghuuuls.agenttesttoolkit.command;

import com.mahghuuuls.agenttesttoolkit.bundle.BundleExecution;
import com.mahghuuuls.agenttesttoolkit.bundle.CommandOutcome;
import com.mahghuuuls.agenttesttoolkit.bundle.PlaceholderException;

/**
 * Decides whether a dispatched command succeeded, from what vanilla reports.
 *
 * <p>A pure function of the command manager's return value and the translation key it sent.
 * Deliberately takes an {@code int} and a {@code String} rather than Minecraft types, because
 * this rule is the single most consequential decision in bundle execution and it should not
 * take a running game to check.
 *
 * <h2>The rule</h2>
 *
 * <p>{@code CommandHandler.executeCommand} returns a success count and never throws: it catches
 * {@link net.minecraft.command.CommandException} internally and messages the sender. A return
 * of zero therefore covers four different situations, and only the translation key separates
 * them.
 *
 * <p>A key the step declared is tolerated, whatever the command. Without a declaration, a key
 * that means only that nothing matched is tolerated for the removal commands of
 * {@link BuiltInTolerance}, so a teardown that ends in {@code kill @e[name=...]} does not halt on
 * its second run under the default {@code stopOnFailure}. For any other command, a check such as
 * {@code testfor} or {@code execute} above all, matching nothing is the answer, and it fails.
 *
 * <p>Everything else returning zero is a failure: unknown command, missing permission, and bad
 * syntax all count. Note that {@code commands.generic.notFound} means "no such command" and is
 * <b>not</b> a "nothing matched" key despite the similar name, so the keys are compared exactly
 * rather than by prefix.
 */
public final class CommandOutcomes {

    /** Thrown by {@code CommandHandler} itself when it expands a selector to nothing. */
    public static final String KEY_SELECTOR_NOT_FOUND = "commands.generic.selector.notFound";
    /** Thrown by {@code CommandBase.getEntity} and friends. */
    public static final String KEY_ENTITY_NOT_FOUND = "commands.generic.entity.notFound";
    /** Thrown by {@code CommandBase.getPlayer} and friends. */
    public static final String KEY_PLAYER_NOT_FOUND = "commands.generic.player.notFound";

    /**
     * Vanilla's "nothing was cleared".
     *
     * <p>Not a selector problem, but the same kind of outcome: the command ran and the
     * inventory was already empty. Added after a teardown bundle containing {@code clear @p}
     * was seen halting on its second run, which is the exact friction the tolerated set exists
     * to remove.
     */
    public static final String KEY_CLEAR_NOTHING = "commands.clear.failure";
    /** Vanilla's "that effect is not active", from removing one named effect. */
    public static final String KEY_EFFECT_NOT_ACTIVE = "commands.effect.failure.notActive";
    /** Vanilla's "no effect is active", from {@code effect <target> clear}. */
    public static final String KEY_EFFECT_NOT_ACTIVE_ALL = "commands.effect.failure.notActive.all";

    /** Vanilla's "no such command". Similar name, opposite meaning; must stay a failure. */
    public static final String KEY_COMMAND_NOT_FOUND = "commands.generic.notFound";
    public static final String KEY_NO_PERMISSION = "commands.generic.permission";

    /**
     * The toolkit's own failure keys, in the {@code devtool.} namespace so they can never
     * collide with a vanilla translation key. A bundle author tolerates them by name exactly as
     * they would a vanilla key; nothing here treats them specially.
     */
    public static final String KEY_WAIT_TIMEOUT = BundleExecution.KEY_WAIT_TIMEOUT;
    public static final String KEY_WAIT_CANCELLED = BundleExecution.KEY_WAIT_CANCELLED;
    public static final String KEY_PLACEHOLDER_UNBOUND = PlaceholderException.KEY_UNBOUND;
    public static final String KEY_PLACEHOLDER_UNSAFE = PlaceholderException.KEY_UNSAFE;
    public static final String KEY_ASSERT_FAILED = com.mahghuuuls.agenttesttoolkit.inspect.Assertions.KEY_FAILED;
    public static final String KEY_ASSERT_UNLOADED = com.mahghuuuls.agenttesttoolkit.inspect.Assertions.KEY_UNLOADED;
    public static final String KEY_ASSERT_AMBIGUOUS = com.mahghuuuls.agenttesttoolkit.inspect.Assertions.KEY_AMBIGUOUS;
    public static final String KEY_SCENARIO_MISMATCH = com.mahghuuuls.agenttesttoolkit.scenario.Scenarios.KEY_MISMATCH;
    public static final String KEY_INSPECT_NOT_LOADED = com.mahghuuuls.agenttesttoolkit.inspect.LoadedPositions.KEY_NOT_LOADED;
    public static final String KEY_SAVE_FAILED = com.mahghuuuls.agenttesttoolkit.command.sub.SaveSubCommand.KEY_FAILED;
    public static final String KEY_SCREENSHOT_NO_CLIENT = com.mahghuuuls.agenttesttoolkit.command.sub.ScreenshotSubCommand.KEY_NO_CLIENT;
    public static final String KEY_BAUBLES_ABSENT = com.mahghuuuls.agenttesttoolkit.command.sub.BaublesSubCommand.KEY_ABSENT;
    public static final String KEY_BAUBLES_REJECTED = com.mahghuuuls.agenttesttoolkit.command.sub.BaublesSubCommand.KEY_REJECTED;
    public static final String KEY_BAUBLES_UNSUPPORTED = com.mahghuuuls.agenttesttoolkit.command.sub.BaublesSubCommand.KEY_UNSUPPORTED;

    private CommandOutcomes() {
    }

    /**
     * A command the caller does not name: only a positive count succeeds.
     *
     * @param executionCount what {@code executeCommand} returned; above zero means at least one
     *                       successful execution
     * @param translationKey the key of the last message sent to the sender, or null
     */
    public static CommandOutcome classify(int executionCount, String translationKey) {
        return classify(executionCount, translationKey, java.util.Collections.<String>emptySet());
    }

    /**
     * As {@link #classify(int, String)}, plus failures the bundle author declared they expect.
     * Without the command's name nothing is tolerated by the built-in table; see
     * {@link #classify(int, String, java.util.Collection, String, String[])}.
     */
    public static CommandOutcome classify(int executionCount, String translationKey,
                                          java.util.Collection<String> declared) {
        return classify(executionCount, translationKey, declared, null, null);
    }

    /**
     * Classifies a dispatched command from its count, its failure key, the keys its step
     * declared, and the command itself.
     *
     * <p>Declared keys come first, so a step that declares a key the table would also tolerate
     * is counted as declared: the author's statement is the more specific one. They are matched
     * against the raw translation key <b>before</b> it is described, because {@link #describe}
     * rewrites two of them into prose. Matching after would make those two untolerable by
     * accident rather than by decision.
     *
     * @param declared    translation keys this command is permitted to fail with. Anything else is
     *                    still a failure, including on this same command, so a typo is not silenced
     * @param commandName the command's registered name, such as {@code kill}; null when unknown
     * @param args        the arguments it ran with; null when unknown
     */
    public static CommandOutcome classify(int executionCount, String translationKey,
                                          java.util.Collection<String> declared,
                                          String commandName, String[] args) {
        if (executionCount > 0) {
            return CommandOutcome.success();
        }
        if (translationKey != null && declared != null && declared.contains(translationKey)) {
            // Named rather than summarised. A reader must be able to tell a tolerated failure
            // from a command that simply worked, and to see which failure was expected.
            return CommandOutcome.tolerated(translationKey, "tolerated failure: " + translationKey,
                    CommandOutcome.SOURCE_DECLARED);
        }
        if (BuiltInTolerance.tolerates(commandName, args, translationKey)) {
            // Recorded as a note rather than discarded. An agent looking at a bundle that
            // "worked" but changed nothing needs to see that there was nothing to remove.
            return CommandOutcome.tolerated(translationKey, builtInNote(translationKey),
                    CommandOutcome.SOURCE_BUILT_IN);
        }
        return CommandOutcome.failure(describe(translationKey));
    }

    /** The note says what was absent: no one matched, nothing was in the inventory, no effect was on. */
    private static String builtInNote(String translationKey) {
        if (KEY_CLEAR_NOTHING.equals(translationKey)) {
            return "nothing to clear";
        }
        if (KEY_EFFECT_NOT_ACTIVE.equals(translationKey) || KEY_EFFECT_NOT_ACTIVE_ALL.equals(translationKey)) {
            return "no effect to clear";
        }
        return "selector matched nothing";
    }

    private static String describe(String translationKey) {
        if (KEY_COMMAND_NOT_FOUND.equals(translationKey)) {
            return "no such command";
        }
        if (KEY_NO_PERMISSION.equals(translationKey)) {
            return "caller lacks permission";
        }
        if (translationKey == null) {
            // Vanilla reported nothing usable. Said plainly rather than guessed at.
            return "command reported no success and gave no reason";
        }
        return translationKey;
    }
}
