package com.mahghuuuls.agenttesttoolkit.command;

import com.mahghuuuls.agenttesttoolkit.bundle.PlaceholderSource;
import com.mahghuuuls.agenttesttoolkit.state.OperatorBinding;
import com.mahghuuuls.agenttesttoolkit.state.ToolkitState;

/**
 * Placeholder values read from the toolkit's live state.
 *
 * <p>Read at the moment of each lookup rather than captured when the bundle started, so a
 * bundle that binds the operator in its first command can use the binding in its second.
 * Fixture values arrive once fixtures exist; until then every fixture lookup is unbound, which
 * is the truthful answer.
 */
public final class StatePlaceholderSource implements PlaceholderSource {

    @Override
    public String operatorName() {
        OperatorBinding binding = ToolkitState.getOperator();
        return binding == null ? null : binding.getName();
    }

    @Override
    public String operatorUuid() {
        OperatorBinding binding = ToolkitState.getOperator();
        return binding == null ? null : binding.getUuid().toString();
    }

    @Override
    public String operatorSelector() {
        OperatorBinding binding = ToolkitState.getOperator();
        return binding == null ? null : binding.getSelector();
    }

    @Override
    public String fixtureUuid(String handle) {
        return ToolkitState.getFixtureUuid(handle);
    }
}
