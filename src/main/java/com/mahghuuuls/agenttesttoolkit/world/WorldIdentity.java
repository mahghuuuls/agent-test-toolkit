package com.mahghuuuls.agenttesttoolkit.world;

/**
 * Which save a server is serving: its folder name and its overworld seed.
 *
 * <p>The one place that decides what "the same world" means (ARC-019). The folder alone is not
 * enough: a save deleted and recreated under the same name is a different world, and its seed
 * almost always differs. The display name is never used, because two saves can share one.
 *
 * <p>Free of Minecraft types so the comparison is tested without a world.
 */
public final class WorldIdentity {

    private final String folder;
    private final long seed;

    public WorldIdentity(String folder, long seed) {
        if (folder == null) {
            throw new IllegalArgumentException("folder must not be null");
        }
        this.folder = folder;
        this.seed = seed;
    }

    public String getFolder() {
        return folder;
    }

    public long getSeed() {
        return seed;
    }

    /**
     * The identity as text, for storing where this type cannot be used ({@code state} does not
     * depend on {@code world}). Two identities have the same key exactly when they are equal.
     */
    public String key() {
        return seed + ":" + folder;
    }

    /**
     * Whether a session that started in the world named by {@code sessionKey} must stop now that
     * this world has loaded. A session with no recorded world is never stopped for this.
     */
    public boolean endsSessionFrom(String sessionKey) {
        return sessionKey != null && !sessionKey.equals(key());
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof WorldIdentity)) {
            return false;
        }
        WorldIdentity that = (WorldIdentity) other;
        return seed == that.seed && folder.equals(that.folder);
    }

    @Override
    public int hashCode() {
        return folder.hashCode() * 31 + (int) (seed ^ (seed >>> 32));
    }

    @Override
    public String toString() {
        return folder + " (seed " + seed + ")";
    }
}
