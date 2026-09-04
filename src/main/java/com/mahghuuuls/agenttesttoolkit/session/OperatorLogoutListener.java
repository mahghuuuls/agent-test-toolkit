package com.mahghuuuls.agenttesttoolkit.session;

import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.state.OperatorBinding;
import com.mahghuuuls.agenttesttoolkit.state.ToolkitState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/**
 * Ends the operator binding when that player leaves.
 *
 * <p>A binding that outlived its player would make every placeholder resolve to someone who
 * is not there, and a checkpoint would wait on nobody until its timeout. Clearing it here, with
 * a record, turns "the operator left" into a fact in the log rather than a mystery a later
 * failure has to be traced back to.
 *
 * <p>Always registered and cheap: one comparison per logout.
 */
public final class OperatorLogoutListener {

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        EntityPlayer player = event.player;
        if (player == null || player.world == null || player.world.isRemote) {
            return;
        }
        OperatorBinding binding = ToolkitState.getOperator();
        if (binding == null || !binding.isPlayer(player.getUniqueID())) {
            return;
        }
        SessionManager.unbindOperator("disconnect", RecordContext.snapshot(player.world));
    }
}
