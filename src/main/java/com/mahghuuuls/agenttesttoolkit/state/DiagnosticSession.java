package com.mahghuuuls.agenttesttoolkit.state;

/**
 * One named grouping of toolkit records belonging to a single manual test attempt.
 *
 * <p>A session asserts nothing. It has no expected result, no verdict, and no pass or fail
 * concept: the toolkit records facts and leaves conclusions to the reader. Its only job is to
 * let a human or an agent find the region of {@code latest.log} that belongs to one attempt.
 *
 * <p>The tick counter advances once per server tick while a server is running. It therefore
 * pauses while the player sits at the title screen between single player worlds, which is
 * correct: no game time passes there, and the counter exists to reason about in-game timing
 * such as projectile travel and delayed effects.
 *
 * <p>Lives in {@code state} rather than {@code session} to keep the dependency graph acyclic.
 * {@code ToolkitState} holds one of these, and {@code session} depends on {@code state}; if
 * this class lived in {@code session} the two packages would depend on each other. The split
 * is deliberate: {@code state} holds "active session" as data, while {@code session} keeps the
 * lifecycle rules that operate on it.
 *
 * <p>Deliberately free of Minecraft types so its behavior is unit testable without a game.
 */
public final class DiagnosticSession {

    private final String name;
    private final String worldKey;
    private long tick;

    public DiagnosticSession(String name) {
        this(name, null);
    }

    /**
     * @param worldKey the world the session started in, as {@code WorldIdentity.key()} text, or
     *                 null when no world had loaded yet; a session without one is never stopped
     *                 for a world change
     */
    public DiagnosticSession(String name, String worldKey) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("session name must not be null or empty");
        }
        this.name = name;
        this.worldKey = worldKey;
        this.tick = 0L;
    }

    /** The world this session belongs to, or null. */
    public String getWorldKey() {
        return worldKey;
    }

    public String getName() {
        return name;
    }

    /** Ticks elapsed since this session started, counting only ticks with a running server. */
    public long getTick() {
        return tick;
    }

    public void advanceTick() {
        tick++;
    }
}
