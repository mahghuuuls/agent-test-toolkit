package com.mahghuuuls.agenttesttoolkit.command;

import com.mahghuuuls.agenttesttoolkit.bundle.CommandOutcome;

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
 * <p>Three keys mean a selector matched nothing. Those count as <b>success</b>: a command that
 * runs and affects nothing has still run. Without this a teardown bundle ending in
 * {@code kill @e[name=...]} would halt on its second run, since {@code stopOnFailure} defaults
 * to true; the toolkit exists to remove exactly that kind of repeated manual fiddling.
 *
 * <p>Everything else returning zero is a failure: unknown command, missing permission, and bad
 * syntax all count. Note that {@code commands.generic.notFound} means "no such command" and is
 * <b>not</b> in the tolerated set despite the similar name, so the keys are compared exactly
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

    /** Vanilla's "no such command". Similar name, opposite meaning; must stay a failure. */
    public static final String KEY_COMMAND_NOT_FOUND = "commands.generic.notFound";
    public static final String KEY_NO_PERMISSION = "commands.generic.permission";

    private CommandOutcomes() {
    }

    /**
     * @param executionCount what {@code executeCommand} returned; above zero means at least one
     *                       successful execution
     * @param translationKey the key of the last message sent to the sender, or null
     */
    public static CommandOutcome classify(int executionCount, String translationKey) {
        if (executionCount > 0) {
            return CommandOutcome.success();
        }
        if (isMatchedNothing(translationKey)) {
            // Recorded as a note rather than discarded. An agent looking at a bundle that
            // "worked" but changed nothing needs to see that the selector was empty.
            return CommandOutcome.success("selector matched nothing");
        }
        if (KEY_CLEAR_NOTHING.equals(translationKey)) {
            // Kept separate from the selector cases so the note stays accurate: nothing was
            // cleared, which is not the same as a selector matching nobody.
            return CommandOutcome.success("nothing to clear");
        }
        return CommandOutcome.failure(describe(translationKey));
    }

    /** True when vanilla's error was only that a selector or name matched no one. */
    public static boolean isMatchedNothing(String translationKey) {
        return KEY_SELECTOR_NOT_FOUND.equals(translationKey)
                || KEY_ENTITY_NOT_FOUND.equals(translationKey)
                || KEY_PLAYER_NOT_FOUND.equals(translationKey);
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
