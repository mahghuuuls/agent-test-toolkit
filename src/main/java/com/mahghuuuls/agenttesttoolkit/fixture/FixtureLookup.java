package com.mahghuuuls.agenttesttoolkit.fixture;

import com.mahghuuuls.agenttesttoolkit.state.ToolkitState;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Finds a session's fixtures in the loaded world, deciding by tag.
 *
 * <p>A handle is looked up through the cache first, by UUID, and the hit is believed only when
 * the entity still carries the handle's tag. On a miss, one scan of every loaded world's
 * entities collects this session's tags and refills the cache. The scan is what vanilla
 * {@code @e[tag=...]} does, and runs once per command, never per tick.
 *
 * <p>A dead or dying entity still carrying the tag is on its way out of the world: it is reported as
 * {@code removing}, never put back in the cache, and never counted as holding the handle, so a
 * fixture cleared a moment ago cannot come back through the scan.
 *
 * <p>Nothing here loads a chunk. A fixture in an unloaded chunk is data on disk and can only
 * be reported as unloaded.
 */
public final class FixtureLookup {

    public enum State {
        LOADED, REMOVING, UNTAGGED, UNLOADED, ABSENT, UNKNOWN, DUPLICATE;

        public String recordName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** One handle's answer: its state, and the entity when one was found. */
    public static final class Found {
        public final State state;
        public final Entity entity;
        public final String uuid;
        /** For {@code duplicate}: every living carrier's UUID, comma separated. */
        public final String carriers;

        Found(State state, Entity entity, String uuid) {
            this(state, entity, uuid, null);
        }

        Found(State state, Entity entity, String uuid, String carriers) {
            this.state = state;
            this.entity = entity;
            this.uuid = uuid;
            this.carriers = carriers;
        }
    }

    private final MinecraftServer server;
    private final String sessionName;
    private Map<String, List<Entity>> scanned;

    public FixtureLookup(MinecraftServer server, String sessionName) {
        this.server = server;
        this.sessionName = sessionName;
    }

    /** Every loaded entity carrying a tag of this session, by handle. Computed once. */
    public Map<String, List<Entity>> scan() {
        if (scanned != null) {
            return scanned;
        }
        Map<String, List<Entity>> found = new LinkedHashMap<String, List<Entity>>();
        for (WorldServer world : server.worlds) {
            if (world == null) {
                continue;
            }
            for (Entity entity : new ArrayList<Entity>(world.loadedEntityList)) {
                for (String tag : entity.getTags()) {
                    String handle = FixtureTags.handleOf(tag, sessionName);
                    if (handle == null) {
                        continue;
                    }
                    List<Entity> list = found.get(handle);
                    if (list == null) {
                        list = new ArrayList<Entity>();
                        found.put(handle, list);
                    }
                    list.add(entity);
                }
            }
        }
        scanned = found;
        return found;
    }

    /** Handles known to the cache or carried by a loaded entity, cache order first. */
    public List<String> handles() {
        List<String> handles = new ArrayList<String>(ToolkitState.getFixtures().keySet());
        for (String handle : scan().keySet()) {
            if (!handles.contains(handle)) {
                handles.add(handle);
            }
        }
        return handles;
    }

    /**
     * Where the fixture under this handle is now, or null when neither the cache nor any loaded
     * entity knows the handle.
     *
     * <p>Two living carriers of one tag are {@code duplicate}, whatever the cache says. A cached
     * fixture in an unloaded place keeps its handle. Otherwise a single living carrier is the
     * fixture and refills the cache; a dead one is {@code removing}; a cached entity that lost
     * its tag, with no other carrier, is {@code untagged}.
     */
    public Found resolve(String handle) {
        String tag = FixtureTags.tag(sessionName, handle);
        String cachedUuid = ToolkitState.getFixtureUuid(handle);
        Entity cached = cachedUuid == null ? null : server.getEntityFromUuid(UUID.fromString(cachedUuid));
        List<Entity> living = livingCarriers(handle);

        if (living.size() > 1) {
            StringBuilder uuids = new StringBuilder();
            for (Entity entity : living) {
                if (uuids.length() > 0) {
                    uuids.append(',');
                }
                uuids.append(entity.getUniqueID());
            }
            return new Found(State.DUPLICATE, living.get(0), living.get(0).getUniqueID().toString(),
                    uuids.toString());
        }
        if (cachedUuid != null && cached == null && !placeLoaded(cachedUuid)) {
            ToolkitState.FixturePlace place = ToolkitState.getFixturePlace(cachedUuid);
            return new Found(place == null ? State.UNKNOWN : State.UNLOADED, null, cachedUuid);
        }
        if (living.size() == 1) {
            Entity entity = living.get(0);
            String uuid = entity.getUniqueID().toString();
            ToolkitState.putFixture(handle, uuid);
            remember(entity);
            return new Found(State.LOADED, entity, uuid);
        }
        if (cached != null && cached.getTags().contains(tag) && !cached.isEntityAlive()) {
            return new Found(State.REMOVING, cached, cachedUuid);
        }
        List<Entity> all = scan().get(handle);
        if (all != null && !all.isEmpty()) {
            Entity dying = all.get(0);
            return new Found(State.REMOVING, dying, dying.getUniqueID().toString());
        }
        if (cached != null) {
            return new Found(State.UNTAGGED, cached, cachedUuid);
        }
        if (cachedUuid != null) {
            return new Found(State.ABSENT, null, cachedUuid);
        }
        return null;
    }

    /**
     * What the cache entry for this handle is worth, read before anything refills the cache,
     * with the cached UUID; the UUID is null when there is no entry.
     */
    public FixtureOwnership.Cached cachedState(String handle) {
        String cachedUuid = ToolkitState.getFixtureUuid(handle);
        if (cachedUuid == null) {
            return FixtureOwnership.Cached.NONE;
        }
        Entity cached = server.getEntityFromUuid(UUID.fromString(cachedUuid));
        return FixtureOwnership.cachedFrom(cached != null,
                cached != null && cached.getTags().contains(FixtureTags.tag(sessionName, handle)),
                cached != null && !cached.isEntityAlive(),
                ToolkitState.getFixturePlace(cachedUuid) != null, placeLoaded(cachedUuid));
    }

    /** Another living loaded entity carrying this handle's tag, or null. */
    public String otherCarrier(String handle, Entity besides) {
        for (Entity entity : livingCarriers(handle)) {
            if (entity != besides) {
                return entity.getUniqueID().toString();
            }
        }
        return null;
    }

    private List<Entity> livingCarriers(String handle) {
        List<Entity> living = new ArrayList<Entity>();
        List<Entity> all = scan().get(handle);
        if (all != null) {
            for (Entity entity : all) {
                // isEntityAlive, not isDead: a mob at zero health plays a death of about 20
                // ticks before it is marked dead, and must not hold the handle meanwhile.
                if (entity.isEntityAlive()) {
                    living.add(entity);
                }
            }
        }
        return living;
    }

    private static boolean placeLoaded(String uuid) {
        ToolkitState.FixturePlace place = ToolkitState.getFixturePlace(uuid);
        if (place == null) {
            return false;
        }
        WorldServer world = DimensionManager.getWorld(place.dimension);
        return world != null && world.isBlockLoaded(new BlockPos(place.x, place.y, place.z));
    }

    /** Keeps the last known place current, so unloaded and absent can be told apart later. */
    public static void remember(Entity entity) {
        ToolkitState.putFixturePlace(entity.getUniqueID().toString(), entity.dimension,
                (int) Math.floor(entity.posX), (int) Math.floor(entity.posY), (int) Math.floor(entity.posZ));
    }
}
