package com.mahghuuuls.agenttesttoolkit.bundle;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Drives the bundle scheduler once per server tick.
 *
 * <p>Subscribed permanently at initialization, following ARC-006: with nothing running the
 * handler costs one emptiness check. The same always-registered, cheaply-gated approach the
 * session ticker and the observers use.
 *
 * <p>Only the END phase is counted, for the same reason as {@code SessionTicker}: Forge fires
 * both START and END every tick, so advancing on both would double the rate and make
 * {@code durationTicks} disagree with the world clock by a factor of two.
 */
public final class BundleTicker {

    private final BundleRecorder recorder = new BundleRecorder();

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Bundles.scheduler().tick(recorder);
        }
    }
}
