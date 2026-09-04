package com.mahghuuuls.agenttesttoolkit.command;

import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.scenario.ScenarioCheckpoint;
import com.mahghuuuls.agenttesttoolkit.session.SessionManager;
import com.mahghuuuls.agenttesttoolkit.state.DiagnosticSession;
import com.mahghuuuls.agenttesttoolkit.state.OperatorBinding;
import com.mahghuuuls.agenttesttoolkit.state.ToolkitState;

import java.util.UUID;

/**
 * Puts a checkpoint's session, operator and fixtures back before the resume bundle runs.
 *
 * <p>Lives in the command layer because it needs both the scenario store and the session
 * lifecycle, and those two packages are not allowed to know each other. The command layer is
 * the one place that may depend on everything, which is what makes it the right home for a
 * step that stitches subsystems together.
 *
 * <p>Order matters and is deliberate: the session first, because starting one clears the
 * fixture handles of the previous session; the operator second, inside that session; the
 * fixtures last, so nothing clears them again.
 */
public final class ScenarioResume {

    private ScenarioResume() {
    }

    /** @return the number of fixtures restored */
    public static int restore(ScenarioCheckpoint checkpoint, RecordContext.Snapshot context) {
        DiagnosticSession active = ToolkitState.getActiveSession();
        if (checkpoint.getSession() != null
                && (active == null || !active.getName().equals(checkpoint.getSession()))) {
            SessionManager.start(checkpoint.getSession(), context);
        }
        if (checkpoint.getOperatorUuid() != null && ToolkitState.hasActiveSession()) {
            UUID uuid = UUID.fromString(checkpoint.getOperatorUuid());
            OperatorBinding current = ToolkitState.getOperator();
            // A second allowed run of the same resume bundle must not write a fresh binding
            // record for the same person; the binding is already what the checkpoint says.
            if (current == null || !current.isPlayer(uuid)) {
                SessionManager.bindOperator(uuid, checkpoint.getOperatorName(), context);
            }
        }
        int restored = 0;
        for (ScenarioCheckpoint.Fixture fixture : checkpoint.getFixtures()) {
            ToolkitState.putFixture(fixture.handle, fixture.uuid);
            ToolkitState.putFixturePlace(fixture.uuid, fixture.dimension, fixture.x, fixture.y, fixture.z);
            restored++;
        }
        return restored;
    }
}
