package com.mahghuuuls.agenttesttoolkit.inbox;

import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.state.InboxOpening;
import com.mahghuuuls.agenttesttoolkit.state.ToolkitState;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * The moments a suspended inbox opening is checked besides the poll (ARC-023): the operator
 * logging in again, a world loading, and, in single player while no server runs at all, the
 * client's own tick, so the deadline closes it on time even when the world is never reopened.
 *
 * <p>Registered after the session's world listener, so the {@code WORLD} record and the current
 * world key come first. An opening never carries over into another world.
 */
public final class InboxRejoinListener {

    private int clientTicks;

    /** Fires only on a physical client; the server's own poll covers a running server. */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++clientTicks < 20) {
            return;
        }
        clientTicks = 0;
        InboxOpening opening = InboxGate.current();
        if (opening != null && opening.isSuspended()
                && FMLCommonHandler.instance().getMinecraftServerInstance() == null) {
            InboxGate.checkSuspension(System.currentTimeMillis(), RecordContext.Snapshot.NONE);
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player == null || event.player.world == null || event.player.world.isRemote) {
            return;
        }
        InboxGate.checkSuspension(System.currentTimeMillis(), RecordContext.snapshot(event.player.world));
        InboxGate.markReturned(event.player.getUniqueID(), System.currentTimeMillis());
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        World world = event.getWorld();
        if (world == null || world.isRemote || world.provider.getDimension() != 0) {
            return;
        }
        InboxOpening opening = InboxGate.current();
        if (opening == null) {
            return;
        }
        RecordContext.Snapshot context = RecordContext.snapshot(world);
        String key = ToolkitState.getCurrentWorldKey();
        if (opening.getWorldKey() != null && key != null && !opening.getWorldKey().equals(key)) {
            ToolkitState.setPendingRejoin(null);
            InboxGate.close("worldChanged", context);
            return;
        }
        InboxGate.checkSuspension(System.currentTimeMillis(), context);
    }
}
