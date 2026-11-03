package com.mahghuuuls.agenttesttoolkit.state;

import com.mahghuuuls.agenttesttoolkit.logging.LoggingCategory;
import com.mahghuuuls.agenttesttoolkit.logging.filter.Filter;

import java.util.EnumMap;
import java.util.Map;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Process-scoped mutable toolkit state: the active diagnostic session, and later the enabled
 * logging categories and their filters.
 *
 * <h2>Why this is a static that is never cleared on server stop</h2>
 *
 * <p>The active session must survive leaving a single player world and returning to the title
 * screen, so that a disconnect and reconnect cycle can be tested inside
 * one session. In single player, leaving a world stops the integrated server and fires
 * {@code FMLServerStoppedEvent}, which Forge documents as the place to reset static state.
 *
 * <p>Following that convention here would break that. <b>Do not add a handler that clears
 * this class on server stop.</b> The state is meant to die with the JVM and nothing sooner,
 * which is also the boundary {@code latest.log} itself uses, so a session and a log file cover
 * the same span.
 *
 * <p>Bundle executions are the opposite case and genuinely must be discarded on server stop,
 * because they hold a sender and dispatch through a server that no longer exists.
 * The two look alike and behave oppositely; that is why both are written down.
 *
 * <h2>Why the field is volatile</h2>
 *
 * <p>All access happens on the logical server thread, so there is no contention. But the
 * server thread is not one thread for the life of the process: stopping an integrated server
 * terminates its thread, and loading the next world starts a <i>new</i> {@code Thread}. A
 * value written by the old server thread must therefore become visible to a different thread
 * that starts later.
 *
 * <p>A plain static field gives no such guarantee. Whether an ordering edge happens to exist
 * depends on vanilla's own shutdown and restart sequence, which is not this mod's code and is
 * not something to rely on silently. {@code volatile} makes the handoff correct by
 * construction, and with no contention it costs nothing worth measuring. Given that session
 * survival rests entirely on this field surviving that transition, the guarantee should come
 * from the declaration rather than from an assumption about another codebase.
 */
public final class ToolkitState {

    private static volatile DiagnosticSession activeSession;

    /**
     * The session's operator, or null.
     *
     * <p>Lives beside the session because it has the session's lifetime: set only while one is
     * active, cleared when it stops, and cleared early when the player leaves. Volatile for the
     * same thread-handoff reason as the session itself.
     */
    private static volatile OperatorBinding operator;

    /**
     * UUIDs of fixtures claimed in the active session, by handle.
     *
     * <p>The session's lifetime, like the operator. Copy-on-write for the same reason the
     * category set is: reads happen at every placeholder substitution, writes only on claim.
     */
    private static volatile java.util.Map<String, String> fixtureUuids =
            java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<String, String>());

    /**
     * Enabled logging categories.
     *
     * <p>Replaced wholesale on every change rather than mutated in place. Reads happen on
     * every observed game event and must be cheap and safe; writes happen rarely, from a
     * command. Copy-on-write gives both without a lock, and the volatile reference carries
     * the same cross-thread visibility guarantee as {@link #activeSession}.
     *
     * <p>The check-then-act in {@link #enable} and {@link #disable} is not atomic, which would
     * matter if two threads mutated concurrently. They do not: console commands are queued and
     * drained on the server thread, player commands execute on the server thread, and the tick
     * handler runs there too. All writes therefore happen on the single logical server thread,
     * the same invariant {@code activeSession} relies on. Reads happen on that thread as well.
     */
    private static volatile Set<LoggingCategory> enabledCategories =
            Collections.unmodifiableSet(EnumSet.noneOf(LoggingCategory.class));

    private ToolkitState() {
    }

    /**
     * Hot path: consulted at the top of every event handler. Must stay cheap
     * enough that an always-registered handler costs nothing worth measuring when its
     * category is off.
     */
    /**
     * One filter per category, or absent for none. Filters deliberately do not compose, so this
     * is a plain replacement rather than a list.
     *
     * <p>Held separately from {@code enabledCategories} rather than as a field on the category,
     * because a category is an enum constant shared across the JVM. Filters are also
     * deliberately <b>not</b> cleared by {@code disable}: re-enabling a category re-applies the
     * filter the operator set, which is what someone toggling a category mid-test expects.
     */
    private static volatile Map<LoggingCategory, Filter> filters =
            Collections.unmodifiableMap(new EnumMap<LoggingCategory, Filter>(LoggingCategory.class));

    /** @return the filter for this category, or null when it records everything. */
    public static Filter getFilter(LoggingCategory category) {
        return filters.get(category);
    }

    /** Replaces any existing filter. Passing null removes it. */
    public static void setFilter(LoggingCategory category, Filter filter) {
        Map<LoggingCategory, Filter> next =
                new EnumMap<LoggingCategory, Filter>(LoggingCategory.class);
        next.putAll(filters);
        if (filter == null) {
            next.remove(category);
        } else {
            next.put(category, filter);
        }
        filters = Collections.unmodifiableMap(next);
    }

    /**
     * Whether an event at this position should be recorded for this category.
     *
     * <p>Combines the enabled check and the filter so no caller can accidentally consult one
     * without the other. An observer that checked only {@code isEnabled} would record filtered
     * events, and the failure would be invisible except as a log that is larger than asked for.
     */
    public static boolean shouldRecord(LoggingCategory category,
                                       int dimension, double x, double y, double z) {
        if (!enabledCategories.contains(category)) {
            return false;
        }
        Filter filter = filters.get(category);
        return filter == null || filter.admits(dimension, x, y, z);
    }

    public static boolean isEnabled(LoggingCategory category) {
        return enabledCategories.contains(category);
    }

    /** @return true when this call changed the state. */
    public static boolean enable(LoggingCategory category) {
        if (enabledCategories.contains(category)) {
            return false;
        }
        Set<LoggingCategory> next = mutableCopy();
        next.add(category);
        enabledCategories = Collections.unmodifiableSet(next);
        return true;
    }

    /** @return true when this call changed the state. */
    public static boolean disable(LoggingCategory category) {
        if (!enabledCategories.contains(category)) {
            return false;
        }
        Set<LoggingCategory> next = mutableCopy();
        next.remove(category);
        enabledCategories = Collections.unmodifiableSet(next);
        return true;
    }

    /**
     * Copies the current set into a fresh mutable {@link EnumSet}.
     *
     * <p>Built by {@code noneOf} plus {@code addAll} rather than {@code EnumSet.copyOf}.
     * {@code copyOf} throws {@link IllegalArgumentException} when handed an empty collection
     * that is not itself an {@code EnumSet}, because it has no element type to infer from.
     * The field holds an unmodifiable wrapper rather than a bare {@code EnumSet}, so the empty
     * case is exactly the resting state, and {@code copyOf} would have failed on the very
     * first enable. Caught by unit tests before it reached a game.
     */
    private static Set<LoggingCategory> mutableCopy() {
        Set<LoggingCategory> copy = EnumSet.noneOf(LoggingCategory.class);
        copy.addAll(enabledCategories);
        return copy;
    }

    /** @return how many categories were disabled. */
    public static int disableAll() {
        int count = enabledCategories.size();
        enabledCategories = Collections.unmodifiableSet(EnumSet.noneOf(LoggingCategory.class));
        // Filters go too. "all off" means the operator wants a clean slate, and leaving a
        // filter behind to surprise them on the next enable would defeat that.
        filters = Collections.unmodifiableMap(
                new EnumMap<LoggingCategory, Filter>(LoggingCategory.class));
        return count;
    }

    /** @return an immutable view of the enabled categories, in declaration order. */
    public static Set<LoggingCategory> getEnabledCategories() {
        return enabledCategories;
    }

    /** @return the active session, or null when none is active. */
    public static DiagnosticSession getActiveSession() {
        return activeSession;
    }

    public static boolean hasActiveSession() {
        return activeSession != null;
    }

    /**
     * Replaces the active session, or clears it when given null.
     *
     * <p>Intended for {@code SessionManager}, which owns the lifecycle rules. Public only
     * because that class lives in a different package; treat it as internal.
     */
    public static void setActiveSession(DiagnosticSession session) {
        activeSession = session;
    }

    /** @return the session's operator, or null when none is bound. */
    public static OperatorBinding getOperator() {
        return operator;
    }

    /**
     * Replaces the operator binding, or clears it when given null.
     *
     * <p>Intended for {@code SessionManager}, which owns the rules about when a binding may
     * exist and records every change. Public only because that class lives elsewhere.
     */
    public static void setOperator(OperatorBinding binding) {
        operator = binding;
    }

    /**
     * The command inbox's open state, or null while closed.
     *
     * <p>The process's lifetime at most, never persisted: a new process starts with the inbox
     * closed. Owned by {@code InboxGate}, which records every change.
     */
    private static volatile InboxOpening inboxOpening;

    public static InboxOpening getInboxOpening() {
        return inboxOpening;
    }

    /** Intended for {@code InboxGate} only, which decides when the inbox may open. */
    public static void setInboxOpening(InboxOpening opening) {
        inboxOpening = opening;
    }

    /**
     * The world the running server serves, as {@code WorldIdentity.key()} text, or null before
     * any world has loaded. Plain text because {@code world} depends on {@code state}, not the
     * other way round. Set when the overworld loads; kept across a server stop like the
     * session, so a session started now can be compared with the next world.
     */
    private static volatile String currentWorldKey;

    /**
     * The one reconnect the toolkit has sent a player's client on, or null (ARC-023). Process
     * lifetime, never persisted, like the inbox opening it may keep.
     */
    private static volatile PendingRejoin pendingRejoin;

    public static PendingRejoin getPendingRejoin() {
        return pendingRejoin;
    }

    /** Intended for the {@code client} command and {@code InboxGate} only. */
    public static void setPendingRejoin(PendingRejoin rejoin) {
        pendingRejoin = rejoin;
    }

    public static String getCurrentWorldKey() {
        return currentWorldKey;
    }

    /** Intended for the world-load listener in {@code session} only. */
    public static void setCurrentWorldKey(String key) {
        currentWorldKey = key;
    }

    /** @return the UUID text of the fixture claimed under this handle, or null. */
    public static String getFixtureUuid(String handle) {
        return handle == null ? null : fixtureUuids.get(handle);
    }

    /** Records a claimed fixture. Refusing a duplicate handle is the caller's decision. */
    public static void putFixture(String handle, String uuid) {
        java.util.Map<String, String> next = new java.util.LinkedHashMap<String, String>(fixtureUuids);
        next.put(handle, uuid);
        fixtureUuids = java.util.Collections.unmodifiableMap(next);
    }

    /** Forgets one handle. The entity's last place is kept, in case it is asked about again. */
    public static void removeFixture(String handle) {
        java.util.Map<String, String> next = new java.util.LinkedHashMap<String, String>(fixtureUuids);
        next.remove(handle);
        fixtureUuids = java.util.Collections.unmodifiableMap(next);
    }

    /** Every claimed handle and its UUID, in claim order. */
    public static java.util.Map<String, String> getFixtures() {
        return fixtureUuids;
    }

    /**
     * Where a fixture was last seen: dimension and block position.
     *
     * <p>Plain ints rather than a Minecraft position type, so this package stays free of
     * game classes and the rule "absent needs a loaded place" stays testable without one.
     */
    public static final class FixturePlace {
        public final int dimension;
        public final int x;
        public final int y;
        public final int z;

        FixturePlace(int dimension, int x, int y, int z) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static volatile java.util.Map<String, FixturePlace> fixturePlaces =
            java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<String, FixturePlace>());

    /** The last known place of a claimed fixture, by UUID text, or null. */
    public static FixturePlace getFixturePlace(String uuid) {
        return uuid == null ? null : fixturePlaces.get(uuid);
    }

    /** Remembers where a fixture was last seen, so absence can be told from unloaded. */
    public static void putFixturePlace(String uuid, int dimension, int x, int y, int z) {
        java.util.Map<String, FixturePlace> next = new java.util.LinkedHashMap<String, FixturePlace>(fixturePlaces);
        next.put(uuid, new FixturePlace(dimension, x, y, z));
        fixturePlaces = java.util.Collections.unmodifiableMap(next);
    }

    /** Forgets every fixture. Called when the session that claimed them ends. */
    public static void clearFixtures() {
        fixtureUuids = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<String, String>());
        fixturePlaces = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<String, FixturePlace>());
    }

    /**
     * Clears every piece of process-scoped state.
     *
     * <p>Exists for tests only. Production code must never call this: doing so on server stop
     * is exactly the mistake this class is written to prevent.
     */
    public static void resetForTesting() {
        activeSession = null;
        operator = null;
        inboxOpening = null;
        currentWorldKey = null;
        pendingRejoin = null;
        clearFixtures();
        enabledCategories = Collections.unmodifiableSet(EnumSet.noneOf(LoggingCategory.class));
        filters = Collections.unmodifiableMap(
                new EnumMap<LoggingCategory, Filter>(LoggingCategory.class));
    }
}
