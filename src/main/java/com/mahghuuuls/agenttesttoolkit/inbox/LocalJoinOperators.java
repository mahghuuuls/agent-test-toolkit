package com.mahghuuuls.agenttesttoolkit.inbox;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mahghuuuls.agenttesttoolkit.config.ToolkitConfigLoader;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.SessionStamp;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Operator rights the toolkit gives at a proven local join on a development dedicated server,
 * and takes back: at that player's leave, at the server's orderly stop, and, after a crash or a
 * killed process, at the next start.
 *
 * <p>Only a dedicated server gives or takes back anything. The rights given in this run are kept
 * in memory; every grant is also written into a file of the toolkit's own beside the bundles
 * before the rights are given, so rights a crash left in {@code ops.json} are found and taken
 * back at the next start. An operator the toolkit did not add is never removed: the list says
 * what the toolkit added, and a player already in {@code ops.json}, at any level, is left as is.
 * When the list cannot be written or read, nothing is given. Server thread only.
 */
public final class LocalJoinOperators {

    /** The toolkit's list of the rights it gave, beside the bundles. */
    static final String FILE = "local-join-ops.json";
    static final String REASON_LOCAL_JOIN = "localJoin";
    static final String REASON_LOGOUT = "logout";
    static final String REASON_SERVER_STOP = "serverStop";
    static final String REASON_LEFTOVER = "leftover";

    private static final Charset UTF8 = Charset.forName("UTF-8");
    /** The rights given in this server run, by UUID. */
    private static final Map<UUID, String> GIVEN = new LinkedHashMap<UUID, String>();

    private LocalJoinOperators() {
    }

    /** Whether this player is in the server's operator list at any level. */
    static boolean inOpsList(MinecraftServer server, EntityPlayerMP player) {
        return server.getPlayerList().getOppedPlayers().getEntry(player.getGameProfile()) != null;
    }

    /** Whether the toolkit gave this player its rights in this run. */
    static synchronized boolean givenThisRun(UUID uuid) {
        return GIVEN.containsKey(uuid);
    }

    /**
     * Gives the joining player operator rights from the join's own profile, at the level the
     * server's {@code op} command gives. The list is written first; if it cannot be, nothing is
     * given, so no right can exist that the toolkit would not take back.
     */
    static synchronized void grant(MinecraftServer server, EntityPlayerMP player) {
        GameProfile profile = player.getGameProfile();
        Map<UUID, String> listed = read();
        if (listed == null) {
            ToolkitLog.error("Local-join operator rights not given", "the toolkit's list " + FILE + " cannot be read");
            return;
        }
        listed.put(profile.getId(), profile.getName());
        if (!write(listed)) {
            ToolkitLog.error("Local-join operator rights not given", "the toolkit's list " + FILE + " cannot be written");
            return;
        }
        server.getPlayerList().addOp(profile);
        GIVEN.put(profile.getId(), profile.getName());
        record(RecordContext.snapshot(player), "granted", profile.getName(), profile.getId(), REASON_LOCAL_JOIN);
    }

    /** Says that a proven local join was in the operator list already, and is left as is. */
    static void kept(EntityPlayerMP player) {
        record(RecordContext.snapshot(player), "kept", player.getName(), player.getUniqueID(), REASON_LOCAL_JOIN);
    }

    /**
     * Takes back the rights the toolkit gave this player, and only those: the entry is removed
     * only when the toolkit's list names the player and the operator list still has it.
     */
    static synchronized void revoke(MinecraftServer server, UUID uuid, String reason, RecordContext.Snapshot context) {
        if (server == null || !server.isDedicatedServer()) {
            return;
        }
        // An unreadable list still leaves this run's grants in memory, and those are taken back;
        // the file is then left as it is for the owner to look at.
        Map<UUID, String> listed = read();
        if (listed == null) {
            ToolkitLog.error("Local-join operator list cannot be read", FILE + "; taking back this run's rights only");
        }
        String name = listed == null ? null : listed.get(uuid);
        if (name == null) {
            name = GIVEN.get(uuid);
        }
        if (name == null) {
            return;
        }
        GameProfile profile = new GameProfile(uuid, name);
        boolean present = server.getPlayerList().getOppedPlayers().getEntry(profile) != null;
        if (present) {
            server.getPlayerList().removeOp(profile);
        }
        GIVEN.remove(uuid);
        if (listed != null) {
            listed.remove(uuid);
            write(listed);
        }
        if (present) {
            record(context, "revoked", name, uuid, reason);
        }
    }

    /** Takes back every right in the list. */
    private static synchronized void revokeAll(MinecraftServer server, String reason) {
        Map<UUID, String> listed = read();
        Map<UUID, String> all = new LinkedHashMap<UUID, String>();
        if (listed != null) {
            all.putAll(listed);
        } else {
            // Said here too: at a start nothing is in memory, and rights a crash left may be stuck.
            ToolkitLog.error("Local-join operator list cannot be read", FILE
                    + "; rights it names stay in ops.json until the file is repaired or removed");
        }
        all.putAll(GIVEN);
        for (UUID uuid : all.keySet()) {
            revoke(server, uuid, reason, RecordContext.Snapshot.NONE);
        }
    }

    /** At a dedicated server's start: rights a crash or a killed process left are taken back before anyone joins. */
    public static void revokeLeftovers(MinecraftServer server) {
        synchronized (LocalJoinOperators.class) {
            GIVEN.clear();
        }
        revokeAll(server, REASON_LEFTOVER);
    }

    /** At a dedicated server's orderly stop. */
    public static void revokeAtStop(MinecraftServer server) {
        revokeAll(server, REASON_SERVER_STOP);
    }

    /** Takes back the rights of a player who leaves a dedicated server. Registered on the event bus at mod start. */
    public static final class LogoutListener {
        @SubscribeEvent
        public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            EntityPlayer player = event.player;
            if (player == null || player.world == null || player.world.isRemote) {
                return;
            }
            MinecraftServer server = player.getServer();
            // Single player never gives rights; its host shares the name and UUID with the
            // development server's player, so it must not touch the shared list either.
            if (server == null || !server.isDedicatedServer()) {
                return;
            }
            revoke(server, player.getUniqueID(), REASON_LOGOUT, RecordContext.snapshot(player.world));
        }
    }

    private static void record(RecordContext.Snapshot context, String action, String name, UUID uuid, String reason) {
        LogRecord record = RecordContext.stamp(LogRecord.of(EventType.OP), context);
        SessionStamp.apply(record);
        record.add("action", action);
        record.add("player", name);
        record.add("uuid", uuid.toString());
        record.add("reason", reason);
        ToolkitLog.write(record);
    }

    private static File file() {
        File config = ToolkitConfigLoader.getConfigDirectory();
        return config == null ? null : new File(config, FILE);
    }

    /** The list on disk; empty when there is none; null when it exists but cannot be read. */
    private static Map<UUID, String> read() {
        File file = file();
        if (file == null) {
            return null;
        }
        if (!file.exists()) {
            return new LinkedHashMap<UUID, String>();
        }
        try {
            return parse(new String(Files.readAllBytes(file.toPath()), UTF8));
        } catch (IOException | RuntimeException unreadable) {
            return null;
        }
    }

    /** Writes the list, or removes the file when it is empty. False when that failed. */
    private static boolean write(Map<UUID, String> listed) {
        File file = file();
        if (file == null) {
            return false;
        }
        try {
            if (listed.isEmpty()) {
                // Nothing given: no file, so a leftover is always a real one.
                Files.deleteIfExists(file.toPath());
            } else {
                // Written beside and moved into place, so an interrupted write never leaves a
                // damaged list where the grants were.
                file.getParentFile().mkdirs();
                File temp = new File(file.getParentFile(), FILE + ".tmp");
                Files.write(temp.toPath(), format(listed).getBytes(UTF8));
                try {
                    Files.move(temp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException notAtomic) {
                    Files.move(temp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return true;
        } catch (IOException | RuntimeException e) {
            ToolkitLog.error("Local-join operator list could not be written", String.valueOf(e));
            return false;
        }
    }

    /** The list as the file holds it: an array of {@code {"uuid": ..., "name": ...}}. */
    static String format(Map<UUID, String> listed) {
        JsonArray array = new JsonArray();
        for (Map.Entry<UUID, String> entry : listed.entrySet()) {
            JsonObject item = new JsonObject();
            item.addProperty("uuid", entry.getKey().toString());
            item.addProperty("name", entry.getValue());
            array.add(item);
        }
        return array.toString();
    }

    /**
     * The list from the file. Anything that is not a list of such entries throws, so a damaged
     * file is never read as an empty list and overwritten.
     */
    static Map<UUID, String> parse(String text) {
        Map<UUID, String> listed = new LinkedHashMap<UUID, String>();
        JsonElement root = new JsonParser().parse(text);
        if (!root.isJsonArray()) {
            throw new IllegalArgumentException("not a list");
        }
        for (JsonElement element : root.getAsJsonArray()) {
            JsonObject item = element.getAsJsonObject();
            listed.put(UUID.fromString(item.get("uuid").getAsString()), item.get("name").getAsString());
        }
        return listed;
    }
}
