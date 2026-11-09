package com.mahghuuuls.agenttesttoolkit.inbox;

import com.mahghuuuls.agenttesttoolkit.config.ToolkitConfigLoader;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/**
 * Closes the inbox when the player who opened it leaves.
 *
 * <p>Requests run with that player as caller. Once they are gone there is no caller, and
 * keeping the inbox open would leave a command channel waiting for whoever logs in next. The
 * one exception is a leave the toolkit itself started with {@code devtool client reconnect}:
 * the opening is then suspended until that same client is back (ARC-023).
 * Kept in this package rather than added to the session's logout listener, so the session
 * package does not depend on the inbox.
 */
public final class InboxLogoutListener {

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        EntityPlayer player = event.player;
        if (player == null || player.world == null || player.world.isRemote) {
            return;
        }
        if (!InboxGate.isOperator(player.getUniqueID())) {
            return;
        }
        // The toolkit sent this client away and expects it back. In single player
        // the host's leave fires this only for a reconnect asked to log out; the default leave
        // stops the server without it, and the server's stop handles that one.
        if (InboxGate.suspendIfLeaving(player.getUniqueID(), System.currentTimeMillis(),
                ToolkitConfigLoader.get().getInboxRejoinWindowSeconds() * 1000L,
                RecordContext.snapshot(player.world))) {
            return;
        }
        if (!InboxGate.current().isSuspended()) {
            InboxGate.close("operatorLeft", RecordContext.snapshot(player.world));
        }
    }
}
