package com.mahghuuuls.agenttesttoolkit.session;

import com.mahghuuuls.agenttesttoolkit.inbox.LocalJoin;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.SessionStamp;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/**
 * Warns at a single-player join when the joining name and the world owner's name differ only in
 * case.
 *
 * <p>The game reads the host's player data from the world's own file only when the two names
 * are equal with case, and from the per-player file otherwise. The first join of a launch can
 * carry the account's own spelling and later joins the launch name, so one launch can read two
 * different copies of the same player's data. The toolkit cannot change which copy the game
 * reads; it says when it happens.
 */
public final class HostNameListener {

    /** The joining name and the owner's name are the same name in another case. */
    public static final String REASON_HOST_NAME_CASE = "hostNameCase";

    /** Single player only, and only for the same name in another case. */
    static boolean shouldWarn(boolean dedicated, String playerName, String ownerName) {
        return !dedicated && LocalJoin.nameCaseDiffers(playerName, ownerName);
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        String owner = server.getServerOwner();
        if (!shouldWarn(server.isDedicatedServer(), player.getName(), owner)) {
            return;
        }
        LogRecord record = RecordContext.stamp(LogRecord.of(EventType.WARNING), player);
        SessionStamp.apply(record);
        record.add("reason", REASON_HOST_NAME_CASE);
        record.add("player", player.getName());
        record.add("owner", owner);
        record.add("detail", "the game reads this player's data from playerdata, not from level.dat");
        ToolkitLog.write(record);
    }
}
