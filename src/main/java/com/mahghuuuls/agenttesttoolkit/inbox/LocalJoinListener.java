package com.mahghuuuls.agenttesttoolkit.inbox;

import com.mahghuuuls.agenttesttoolkit.config.ToolkitConfigLoader;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.security.SecureRandom;

/**
 * Opens the command inbox at a local join (D24, REQ-222, ARC-029).
 *
 * <p>The single-player host is recognised at login, by the in-process channel and the integrated
 * server's owner. A dedicated server writes a fresh secret into its own folder at every start;
 * a client that shares that folder sends it back ({@code net.LocalJoinProof}), and
 * {@link #onProof} opens the inbox for it. The secret is removed at the stop.
 */
public final class LocalJoinListener {

    static final String SECRET_FILE = ToolkitConfigLoader.LOCAL_JOIN_SECRET_FILE;
    /** A refusal of operator rights at a proven local join on a server that checks accounts. */
    static final String REASON_ONLINE_MODE = "localJoinOnlineMode";
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static volatile String secret;

    /** The secret's file, beside the bundles; null before configuration loads. */
    public static File secretFile() {
        File config = ToolkitConfigLoader.getConfigDirectory();
        return config == null ? null : new File(config, SECRET_FILE);
    }

    /**
     * At a dedicated server's start, when both keys allow a local join: a new secret, so one
     * from an earlier run is worth nothing. Readable by this user only where the file system
     * says so; on Windows the profile's folders already are.
     */
    public static void writeSecret() {
        File file = secretFile();
        if (file == null || !ToolkitConfigLoader.get().isInboxEnabled()
                || !ToolkitConfigLoader.get().isInboxOpenOnLocalJoin()) {
            return;
        }
        String fresh = LocalJoin.newSecret(RANDOM);
        try {
            file.getParentFile().mkdirs();
            Files.write(file.toPath(), fresh.getBytes(UTF8));
            try {
                Files.setPosixFilePermissions(file.toPath(),
                        java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException notPosix) {
                // Windows: no POSIX bits.
            }
            secret = fresh;
        } catch (IOException | RuntimeException e) {
            secret = null;
            ToolkitLog.error("Local-join secret could not be written", e.toString());
        }
    }

    /**
     * At a stop: the secret is gone, from memory and from disk. Only a process that wrote one
     * removes the file, so an integrated server stopping beside a development server sharing the
     * folder leaves that server's secret alone.
     */
    public static void removeSecret() {
        MISMATCHED.clear();
        String written = secret;
        if (written == null) {
            return;
        }
        secret = null;
        File file = secretFile();
        try {
            // Only this process's own secret: another server sharing the folder may have written its own.
            if (file != null && file.isFile()
                    && written.equals(new String(Files.readAllBytes(file.toPath()), UTF8).trim())) {
                file.delete();
            }
        } catch (IOException | RuntimeException unreadable) {
            // Left in place; the next start overwrites it.
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP) || event.player.world == null || event.player.world.isRemote) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null || server.isDedicatedServer() || player.connection == null) {
            return;
        }
        boolean localChannel = player.connection.getNetworkManager() != null
                && player.connection.getNetworkManager().isLocalChannel();
        LocalJoin.Decision decision = LocalJoin.decideSinglePlayer(
                ToolkitConfigLoader.get().isInboxOpenOnLocalJoin(), ToolkitConfigLoader.get().isInboxEnabled(),
                InboxGate.isOpen(), localChannel, player.getName(), server.getServerOwner(),
                player.canUseCommand(2, "devtool"));
        act(server, player, decision);
    }

    /** Players whose wrong proof was recorded once this run, so a client cannot fill the log. */
    private static final java.util.Set<java.util.UUID> MISMATCHED =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<java.util.UUID, Boolean>());

    /** A client sent the secret back; on the server thread. */
    public static void onProof(EntityPlayerMP player, String proof) {
        MinecraftServer server = player.getServer();
        if (server == null || !server.isDedicatedServer()) {
            return;
        }
        // The secret proves a shared folder; a connection from this machine is the other half (FQ42).
        java.net.SocketAddress remote = player.connection == null ? null
                : player.connection.getNetworkManager().getRemoteAddress();
        boolean loopback = remote instanceof java.net.InetSocketAddress
                && ((java.net.InetSocketAddress) remote).getAddress() != null
                && ((java.net.InetSocketAddress) remote).getAddress().isLoopbackAddress();
        boolean matched = loopback && LocalJoin.matches(secret, proof);
        LocalJoin.Decision decision = apply(server, player, matched);
        if (decision == LocalJoin.Decision.NOT_LOCAL && MISMATCHED.add(player.getUniqueID())) {
            InboxGate.recordRefusal(RecordContext.snapshot(player), player.getUniqueID(), player.getName(), "localJoinNotProven");
        }
    }

    private static LocalJoin.Decision apply(MinecraftServer server, EntityPlayerMP player, boolean matched) {
        // Rights first, so the inbox decision below sees an operator. Decided at every proven
        // join, also while the inbox is open or suspended for a reconnect.
        LocalJoin.Grant grant = LocalJoin.decideGrant(ToolkitConfigLoader.get().isInboxLocalJoinOperator(),
                ToolkitConfigLoader.get().isInboxOpenOnLocalJoin(), ToolkitConfigLoader.get().isInboxEnabled(),
                matched, LocalJoinOperators.inOpsList(server, player),
                LocalJoinOperators.givenThisRun(player.getUniqueID()), server.isServerInOnlineMode());
        if (grant == LocalJoin.Grant.GRANT) {
            LocalJoinOperators.grant(server, player);
        } else if (grant == LocalJoin.Grant.KEEP) {
            LocalJoinOperators.kept(player);
        } else if (grant == LocalJoin.Grant.ONLINE_MODE) {
            // One refusal for the join: this one says why, so the inbox's own is not written too.
            InboxGate.recordRefusal(RecordContext.snapshot(player), player.getUniqueID(), player.getName(), REASON_ONLINE_MODE);
            return LocalJoin.Decision.NOT_OPERATOR;
        }
        LocalJoin.Decision decision = LocalJoin.decideDedicated(ToolkitConfigLoader.get().isInboxOpenOnLocalJoin(),
                ToolkitConfigLoader.get().isInboxEnabled(), InboxGate.isOpen(), matched,
                player.canUseCommand(2, "devtool"));
        act(server, player, decision);
        return decision;
    }

    /** Carries out a decision: an opening or a refusal record; the other decisions write nothing. */
    private static void act(MinecraftServer server, EntityPlayerMP player, LocalJoin.Decision decision) {
        RecordContext.Snapshot context = RecordContext.snapshot(player);
        if (decision == LocalJoin.Decision.OPEN) {
            InboxGate.openAtLocalJoin(player.getUniqueID(), player.getName(), server.getFolderName(), context);
        } else if (decision == LocalJoin.Decision.NOT_OPERATOR) {
            InboxGate.recordRefusal(context, player.getUniqueID(), player.getName(), "localJoinNotOperator");
        } else if (decision == LocalJoin.Decision.NOT_HOST) {
            InboxGate.recordRefusal(context, player.getUniqueID(), player.getName(), "localJoinNotHost", "owner", server.getServerOwner());
        }
    }
}
