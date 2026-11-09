package com.mahghuuuls.agenttesttoolkit.net;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetworkManager;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import java.util.Map;

/**
 * The toolkit's one network channel (ARC-017), shared code.
 *
 * <p>Nothing here references a client class: the client side installs a {@link ClientReceiver}
 * through {@code ClientProxy}, and on a dedicated server none exists. The server sends only to
 * a client whose mod list shows the toolkit, so a client without it is never sent a message
 * it cannot read, and still joins ({@code acceptableRemoteVersions = "*"}, REQ-188).
 */
public final class ToolkitChannel {

    public static final String NAME = "agenttesttoolkit";
    static final String MOD_ID = com.mahghuuuls.agenttesttoolkit.Tags.MOD_ID;

    /** What the client does with a request. Installed only on a client. */
    public interface ClientReceiver {
        void receive(ScreenshotRequest request);
    }

    /** What the client does with a lifecycle step (ARC-022). Installed only on a client. */
    public interface ActionReceiver {
        void receive(ClientActionRequest request);

        /** The server said it understands client actions; answer with a hello. Network thread. */
        void serverHello(String serverVersion);
    }

    /** What the client does with a player action. Installed only on a client. */
    public interface PlayerActionReceiver {
        void receive(PlayerActionRequest request);
    }

    private static volatile SimpleNetworkWrapper channel;
    private static volatile ClientReceiver receiver;
    private static volatile ActionReceiver actionReceiver;
    private static volatile PlayerActionReceiver playerActionReceiver;

    private ToolkitChannel() {
    }

    /** Called once at pre-initialisation, on both sides. */
    public static void register() {
        SimpleNetworkWrapper wrapper = NetworkRegistry.INSTANCE.newSimpleChannel(NAME);
        wrapper.registerMessage(new ScreenshotRequest.Handler(), ScreenshotRequest.class, 0, Side.CLIENT);
        wrapper.registerMessage(new ClientActionRequest.Handler(), ClientActionRequest.class, 1, Side.CLIENT);
        wrapper.registerMessage(new ClientHello.Handler(), ClientHello.class, 2, Side.SERVER);
        wrapper.registerMessage(new ClientActionReport.Handler(), ClientActionReport.class, 3, Side.SERVER);
        wrapper.registerMessage(new ServerHello.Handler(), ServerHello.class, 4, Side.CLIENT);
        wrapper.registerMessage(new LocalJoinProof.Handler(), LocalJoinProof.class, 5, Side.SERVER);
        wrapper.registerMessage(new PlayerActionRequest.Handler(), PlayerActionRequest.class, 6, Side.CLIENT);
        channel = wrapper;
    }

    public static void setPlayerActionReceiver(PlayerActionReceiver receiver) {
        playerActionReceiver = receiver;
    }

    static PlayerActionReceiver playerActionReceiver() {
        return playerActionReceiver;
    }

    public static void setClientReceiver(ClientReceiver clientReceiver) {
        receiver = clientReceiver;
    }

    static ClientReceiver clientReceiver() {
        return receiver;
    }

    public static void setActionReceiver(ActionReceiver receiver) {
        actionReceiver = receiver;
    }

    static ActionReceiver actionReceiver() {
        return actionReceiver;
    }

    /**
     * Whether this player's client can read the toolkit's messages: the integrated server's
     * own host shares the process, and any other client must list the toolkit in its handshake.
     */
    public static boolean canReceive(EntityPlayerMP player) {
        if (!hasRealConnection(player)) {
            return false;
        }
        NetworkManager manager = player.connection.getNetworkManager();
        NetworkDispatcher dispatcher = NetworkDispatcher.get(manager);
        return hasToolkit(dispatcher == null ? null : dispatcher.getModList(), manager.isLocalChannel());
    }

    /** The first toolkit version that reads {@link ServerHello} and the client-action messages. */
    static final int[] FIRST_ACTIONS_VERSION = {1, 4, 0};

    /**
     * Whether this player's client understands the client-action messages. An older toolkit has
     * no handler for them and would drop the connection with a decoding error, so the server
     * says {@link ServerHello} only when this is true. The host of single player is this very
     * build. Server side only: there the handshake's mod list is the client's real one, while a
     * client's copy of the server's list stays empty in 1.12.2.
     */
    public static boolean clientSpeaksActions(EntityPlayerMP player) {
        if (!hasRealConnection(player)) {
            return false;
        }
        NetworkManager manager = player.connection.getNetworkManager();
        NetworkDispatcher dispatcher = NetworkDispatcher.get(manager);
        return speaksActions(dispatcher == null ? null : dispatcher.getModList(), manager.isLocalChannel());
    }

    /** The decision of {@link #clientSpeaksActions}, apart from the connection, for tests. */
    static boolean speaksActions(Map<String, String> mods, boolean localChannel) {
        if (localChannel) {
            return true;
        }
        String version = mods == null ? null : mods.get(MOD_ID);
        return version != null && atLeast(version, FIRST_ACTIONS_VERSION);
    }

    /** 1.5.0: a client that takes `quit`, and a server that reads a local-join proof. */
    public static final int[] VERSION_1_5_0 = {1, 5, 0};

    /**
     * 1.7.0: a client that reads player actions, and a server that reads the extra fields of a
     * report. An older client has no handler for the player-action message and would drop the
     * connection on it.
     */
    public static final int[] VERSION_1_7_0 = {1, 7, 0};

    /** Numeric parts compared in order; anything unreadable counts as older. */
    public static boolean atLeast(String version, int[] minimum) {
        String[] parts = version.split("[.-]");
        for (int i = 0; i < minimum.length; i++) {
            int value;
            try {
                value = i < parts.length ? Integer.parseInt(parts[i]) : 0;
            } catch (NumberFormatException notNumeric) {
                return false;
            }
            if (value != minimum[i]) {
                return value > minimum[i];
            }
        }
        return true;
    }

    /** A player with a network channel behind it; another mod's fake player has none. */
    private static boolean hasRealConnection(EntityPlayerMP player) {
        return player != null && !(player instanceof net.minecraftforge.common.util.FakePlayer)
                && player.connection != null && player.connection.getNetworkManager() != null
                && player.connection.getNetworkManager().channel() != null;
    }

    /** The decision of {@link #canReceive}, apart from the connection so it is tested without one. */
    static boolean hasToolkit(Map<String, String> modList, boolean localChannel) {
        return localChannel || (modList != null && modList.containsKey(MOD_ID));
    }

    /** Sends a request. The caller has checked {@link #canReceive} first. */
    public static void send(ScreenshotRequest request, EntityPlayerMP player) {
        channel.sendTo(request, player);
    }

    /** Sends a lifecycle step. The caller has checked {@link #canReceive} first. */
    public static void send(ClientActionRequest request, EntityPlayerMP player) {
        channel.sendTo(request, player);
    }

    /** Sends a player action. The caller has checked the client said hello at 1.7.0 or later. */
    public static void send(PlayerActionRequest request, EntityPlayerMP player) {
        channel.sendTo(request, player);
    }

    /** Server to client: the server's hello. The caller has checked {@link #clientSpeaksActions}. */
    public static void send(ServerHello hello, EntityPlayerMP player) {
        channel.sendTo(hello, player);
    }

    /** Client to server: a hello or a report. Called only on a client. */
    public static void sendToServer(net.minecraftforge.fml.common.network.simpleimpl.IMessage message) {
        SimpleNetworkWrapper current = channel;
        if (current != null) {
            current.sendToServer(message);
        }
    }
}
