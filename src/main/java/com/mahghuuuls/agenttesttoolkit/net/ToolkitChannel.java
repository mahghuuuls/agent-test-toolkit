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

    private static volatile SimpleNetworkWrapper channel;
    private static volatile ClientReceiver receiver;

    private ToolkitChannel() {
    }

    /** Called once at pre-initialisation, on both sides. */
    public static void register() {
        SimpleNetworkWrapper wrapper = NetworkRegistry.INSTANCE.newSimpleChannel(NAME);
        wrapper.registerMessage(new ScreenshotRequest.Handler(), ScreenshotRequest.class, 0, Side.CLIENT);
        channel = wrapper;
    }

    public static void setClientReceiver(ClientReceiver clientReceiver) {
        receiver = clientReceiver;
    }

    static ClientReceiver clientReceiver() {
        return receiver;
    }

    /**
     * Whether this player's client can read the toolkit's messages: the integrated server's
     * own host shares the process, and any other client must list the toolkit in its handshake.
     */
    public static boolean canReceive(EntityPlayerMP player) {
        if (player == null || player.connection == null) {
            return false;
        }
        NetworkManager manager = player.connection.getNetworkManager();
        NetworkDispatcher dispatcher = NetworkDispatcher.get(manager);
        return hasToolkit(dispatcher == null ? null : dispatcher.getModList(), manager.isLocalChannel());
    }

    /** The decision of {@link #canReceive}, apart from the connection so it is tested without one. */
    static boolean hasToolkit(Map<String, String> modList, boolean localChannel) {
        return localChannel || (modList != null && modList.containsKey(MOD_ID));
    }

    /** Sends a request. The caller has checked {@link #canReceive} first. */
    public static void send(ScreenshotRequest request, EntityPlayerMP player) {
        channel.sendTo(request, player);
    }
}
