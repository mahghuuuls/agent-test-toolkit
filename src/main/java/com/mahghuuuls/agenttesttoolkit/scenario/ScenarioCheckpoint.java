package com.mahghuuuls.agenttesttoolkit.scenario;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What a scenario expects to happen next, across a restart.
 *
 * <p>Bounded metadata and nothing else: no commands, no entity snapshots. Enough to refuse
 * the wrong bundle, the wrong text of the right bundle, or the wrong world, and to put the
 * session's operator and fixtures back so the resume bundle can name them.
 *
 * <p>Immutable and free of Minecraft types, so the rule that decides whether a run is allowed
 * can be tested without a save.
 */
public final class ScenarioCheckpoint {

    /** Bumped when a stored field changes meaning. A newer store is read as corrupt, not guessed at. */
    public static final int SCHEMA_VERSION = 1;

    /** One fixture as it was when the checkpoint was written. */
    public static final class Fixture {
        public final String handle;
        public final String uuid;
        public final int dimension;
        public final int x;
        public final int y;
        public final int z;

        public Fixture(String handle, String uuid, int dimension, int x, int y, int z) {
            this.handle = handle;
            this.uuid = uuid;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private final String scenarioId;
    private final String session;
    private final String nextBundle;
    private final String nextHash;
    private final String operatorUuid;
    private final String operatorName;
    private final boolean expectFatalStop;
    private final long createdTick;
    private final List<Fixture> fixtures;

    public ScenarioCheckpoint(String scenarioId, String session, String nextBundle, String nextHash,
                              String operatorUuid, String operatorName, boolean expectFatalStop,
                              long createdTick, List<Fixture> fixtures) {
        if (scenarioId == null || scenarioId.isEmpty() || nextBundle == null || nextBundle.isEmpty()
                || nextHash == null || nextHash.isEmpty()) {
            throw new IllegalArgumentException("scenarioId, nextBundle and nextHash are required");
        }
        this.scenarioId = scenarioId;
        this.session = session;
        this.nextBundle = nextBundle;
        this.nextHash = nextHash;
        this.operatorUuid = operatorUuid;
        this.operatorName = operatorName;
        this.expectFatalStop = expectFatalStop;
        this.createdTick = createdTick;
        this.fixtures = Collections.unmodifiableList(
                new ArrayList<Fixture>(fixtures == null ? Collections.<Fixture>emptyList() : fixtures));
    }

    public String getScenarioId() {
        return scenarioId;
    }

    /** The session the checkpoint was written in, restored on resume. May be null. */
    public String getSession() {
        return session;
    }

    public String getNextBundle() {
        return nextBundle;
    }

    /** The normalized hash the next bundle had when the checkpoint was written. */
    public String getNextHash() {
        return nextHash;
    }

    public String getOperatorUuid() {
        return operatorUuid;
    }

    public String getOperatorName() {
        return operatorName;
    }

    /** Whether the stop that follows is meant to be a crash rather than a clean shutdown. */
    public boolean isExpectFatalStop() {
        return expectFatalStop;
    }

    public long getCreatedTick() {
        return createdTick;
    }

    public List<Fixture> getFixtures() {
        return fixtures;
    }

    /**
     * Whether a run may proceed while this checkpoint stands.
     *
     * @return null when allowed, otherwise a short machine-readable reason:
     *         {@code wrongBundle} or {@code changedHash}
     */
    public String refusalFor(String offeredBundle, String offeredHash) {
        if (!nextBundle.equals(offeredBundle)) {
            return "wrongBundle";
        }
        if (!nextHash.equals(offeredHash)) {
            return "changedHash";
        }
        return null;
    }
}
