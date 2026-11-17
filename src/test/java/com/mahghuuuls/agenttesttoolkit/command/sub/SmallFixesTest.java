package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.bundle.BundleCommand;
import com.mahghuuuls.agenttesttoolkit.bundle.BundleDefinition;
import com.mahghuuuls.agenttesttoolkit.bundle.BundleExecution;
import com.mahghuuuls.agenttesttoolkit.bundle.CommandOutcome;
import com.mahghuuuls.agenttesttoolkit.bundle.RunOrigin;
import com.mahghuuuls.agenttesttoolkit.inbox.InboxRequest;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.state.ToolkitState;
import net.minecraft.command.CommandException;
import net.minecraft.command.WrongUsageException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The checkpoint prompt's form by client version, the strict clear's key and its failure, the
 * fixture command's message without a session, and the run's quietness: its own {@code quiet}
 * word, on the command and through the inbox, and a nested run's following of its parent.
 */
class SmallFixesTest {

    @Test
    @DisplayName("a 1.9.0 client is shown the panel by default; an older one the earlier default")
    void formByVersion() {
        assertEquals("panel", CheckpointSubCommand.formFor(null, "1.9.0", CheckpointSubCommand.OLDER_CUE_FORM));
        assertEquals("panel", CheckpointSubCommand.formFor(null, "1.10.2", CheckpointSubCommand.OLDER_ARM_FORM));
        assertEquals("actionbar", CheckpointSubCommand.formFor(null, "1.8.0", CheckpointSubCommand.OLDER_CUE_FORM));
        assertEquals("title", CheckpointSubCommand.formFor(null, "1.8.0", CheckpointSubCommand.OLDER_ARM_FORM));
        assertEquals("actionbar", CheckpointSubCommand.formFor(null, null, CheckpointSubCommand.OLDER_CUE_FORM),
                "no hello yet is treated as an older client");
    }

    @Test
    @DisplayName("a form named on the cue is kept, except a panel an older client cannot read")
    void askedForm() {
        assertEquals("chat", CheckpointSubCommand.formFor("chat", "1.9.0", CheckpointSubCommand.OLDER_CUE_FORM));
        assertEquals("title", CheckpointSubCommand.formFor("title", "1.8.0", CheckpointSubCommand.OLDER_CUE_FORM));
        assertEquals("panel", CheckpointSubCommand.formFor("panel", "1.9.0", CheckpointSubCommand.OLDER_CUE_FORM));
        assertEquals("actionbar", CheckpointSubCommand.formFor("panel", "1.8.0", CheckpointSubCommand.OLDER_CUE_FORM));
    }

    @Test
    @DisplayName("a strict clear that left a fixture not loaded fails with devtool.fixture.notLoaded; otherwise none")
    void strictKey() {
        assertEquals("devtool.fixture.notLoaded", FixtureSubCommand.strictFailure(true, 1));
        assertNull(FixtureSubCommand.strictFailure(true, 0));
        assertNull(FixtureSubCommand.strictFailure(false, 3), "without strict, as before");
    }

    @Test
    @DisplayName("the strict clear's failure is thrown as devtool.fixture.notLoaded, and only when strict found one")
    void strictThrows() throws CommandException {
        CommandException thrown = assertThrows(CommandException.class, () -> FixtureSubCommand.failIfStrict(true, 1, 2));
        assertEquals(FixtureSubCommand.KEY_NOT_LOADED, thrown.getMessage());
        FixtureSubCommand.failIfStrict(true, 0, 2);
        FixtureSubCommand.failIfStrict(false, 3, 3);
    }

    @Test
    @DisplayName("a fixture command without a session names the step that starts one")
    void fixtureWithoutSession() {
        ToolkitState.resetForTesting();
        try {
            CommandException thrown = assertThrows(CommandException.class,
                    () -> new FixtureSubCommand().execute(null, null, new String[] {"list"}));
            assertTrue(thrown.getMessage().contains("devtool session start <name>"), thrown.getMessage());
        } finally {
            ToolkitState.resetForTesting();
        }
    }

    private static BundleExecution runOfQuietness(boolean quiet) {
        BundleExecution run = new BundleExecution(new BundleDefinition("parent", null, true,
                Collections.singletonList(new BundleCommand("say hi", 0))),
                command -> CommandOutcome.success(), () -> RecordContext.Snapshot.NONE);
        run.setQuiet(quiet);
        return run;
    }

    @Test
    @DisplayName("a nested run follows its parent's quietness, unless its word or its file says otherwise")
    void nestedFollowsParent() {
        BundleExecution quietParent = runOfQuietness(true);
        BundleExecution loudParent = runOfQuietness(false);
        assertTrue(RunSubCommand.quietFor(null, null, quietParent, RunOrigin.NESTED), "a quiet parent's child");
        assertFalse(RunSubCommand.quietFor(null, null, loudParent, RunOrigin.NESTED), "a loud parent's child");
        assertFalse(RunSubCommand.quietFor(null, Boolean.FALSE, quietParent, RunOrigin.NESTED), "its file");
        assertTrue(RunSubCommand.quietFor(null, Boolean.TRUE, loudParent, RunOrigin.NESTED), "its file");
        assertFalse(RunSubCommand.quietFor(Boolean.FALSE, Boolean.TRUE, quietParent, RunOrigin.NESTED),
                "its word outranks its file");
        assertTrue(RunSubCommand.quietFor(null, null, null, RunOrigin.INBOX), "a top-level inbox run");
        assertFalse(RunSubCommand.quietFor(null, null, null, RunOrigin.CHAT), "a top-level chat run");
    }

    @Test
    @DisplayName("run takes quiet=true or quiet=false after the bundle name, and nothing else")
    void quietWord() throws WrongUsageException {
        assertNull(RunSubCommand.quietWord(new String[] {"b"}));
        assertEquals(Boolean.TRUE, RunSubCommand.quietWord(new String[] {"b", "quiet=true"}));
        assertEquals(Boolean.FALSE, RunSubCommand.quietWord(new String[] {"b", "QUIET=FALSE"}));
        assertThrows(WrongUsageException.class, () -> RunSubCommand.quietWord(new String[] {"b", "quiet=maybe"}));
        assertThrows(WrongUsageException.class, () -> RunSubCommand.quietWord(new String[] {"b", "loud"}));
    }

    @Test
    @DisplayName("an inbox run may carry the quiet word, and becomes devtool run with it")
    void inboxQuietWord() {
        assertEquals("devtool run b quiet=false", InboxRequest.parse("q1", "run b quiet=false").getCommandLine());
        assertEquals("devtool run b quiet=true", InboxRequest.parse("q2", "run b quiet=true").getCommandLine());
        assertEquals(InboxRequest.REASON_BAD_ARGUMENTS, InboxRequest.parse("q3", "run b quiet=no").getRefusal());
        assertEquals(InboxRequest.REASON_BAD_ARGUMENTS, InboxRequest.parse("q4", "run b c").getRefusal());
    }
}
