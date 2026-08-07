package com.mahghuuuls.agenttesttoolkit.session;

import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Advances the active session's tick counter once per server tick.
 *
 * <p>Subscribed permanently at initialization. When no session is active the handler costs a
 * null check, which is the same always-registered, cheaply-gated approach the logging
 * categories use.
 *
 * <p>Only the END phase is counted. Forge fires both START and END for every tick, so counting
 * both would double the rate and make {@code sessionTick} disagree with the world tick by a
 * factor of two, which would quietly corrupt any timing reasoning built on it.
 */
public final class SessionTicker {

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            SessionManager.onServerTick();
        }
    }
}
