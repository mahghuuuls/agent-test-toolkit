package com.mahghuuuls.agenttesttoolkit;

import com.mahghuuuls.agenttesttoolkit.command.DevToolCommand;
import com.mahghuuuls.agenttesttoolkit.session.SessionTicker;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

/**
 * Entry point.
 *
 * <p>{@code acceptableRemoteVersions = "*"} implements concept decision D4: the toolkit is
 * required on the server and optional on the client, and a client without it must still be
 * able to connect. Without this attribute Forge would refuse such a client, which would make
 * the toolkit unusable for testing against a dedicated server.
 */
@Mod(
        modid = Tags.MOD_ID,
        name = Tags.MOD_NAME,
        version = Tags.VERSION,
        acceptableRemoteVersions = "*"
)
public class AgentTestToolkitMod {

    /**
     * REQ-147. The client class is named as a string, never referenced as a type from common
     * code, so a dedicated server never attempts to load it.
     */
    @net.minecraftforge.fml.common.SidedProxy(
            clientSide = "com.mahghuuuls.agenttesttoolkit.proxy.ClientProxy",
            serverSide = "com.mahghuuuls.agenttesttoolkit.proxy.CommonProxy")
    public static com.mahghuuuls.agenttesttoolkit.proxy.CommonProxy proxy;

    /** Held so its pending correlations can be discarded when the server stops. */
    private com.mahghuuuls.agenttesttoolkit.observe.damage.DamageObserver damageObserver;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {

        // Both loaders return their problems rather than logging as they go, so that the one
        // startup record can be written first and carry the bundle count. ToolkitStartup owns
        // that ordering rule and is tested directly; see REQ-111.
        java.util.List<String> configProblems =
                com.mahghuuuls.agenttesttoolkit.config.ToolkitConfigLoader.load(
                        event.getModConfigurationDirectory());

        // Bundles load after configuration, since the bundles directory sits inside the
        // toolkit configuration directory that the loader establishes.
        com.mahghuuuls.agenttesttoolkit.bundle.BundleRegistry.LoadReport bundles =
                com.mahghuuuls.agenttesttoolkit.bundle.Bundles.reload();

        ToolkitStartup.announce(Tags.VERSION, bundles, configProblems);
    }

    @Mod.EventHandler
    public void init(net.minecraftforge.fml.common.event.FMLInitializationEvent event) {
        // Subscribed once and never unsubscribed. See ARC-006: the resting cost is a null
        // check, and always-registered handlers remove a whole class of lifecycle bug.
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new SessionTicker());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                new com.mahghuuuls.agenttesttoolkit.observe.BlockPlaceObserver());
        damageObserver = new com.mahghuuuls.agenttesttoolkit.observe.damage.DamageObserver();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(damageObserver);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                new com.mahghuuuls.agenttesttoolkit.bundle.BundleTicker());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                new com.mahghuuuls.agenttesttoolkit.observe.DiscreteActionObserver());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                new com.mahghuuuls.agenttesttoolkit.observe.EntitySpawnObserver());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                new com.mahghuuuls.agenttesttoolkit.command.JoinAutomation());
        // No-op on a server. The client implementation registers its own handler.
        proxy.applyClientDefaults();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new DevToolCommand());
    }

    @Mod.EventHandler
    public void serverStopped(net.minecraftforge.fml.common.event.FMLServerStoppedEvent event) {
        // ARC-002: discard in-flight, server-bound transient state. Pending damage
        // correlations hold entity references from a world that is unloading, and the observer
        // is registered permanently, so leaving them would leak stale entries into the next
        // world's first tick.
        //
        // ARC-001: this handler must NEVER clear ToolkitState. Leaving a single player world
        // stops the integrated server, and Forge documents this event as the place to reset
        // static state. Doing that here would destroy the active session and break the
        // disconnect-and-reconnect testing REQ-052 exists to enable. Session and logging
        // category state are meant to die with the JVM and nothing sooner.
        if (damageObserver != null) {
            damageObserver.discardPending();
        }
        // Same rule, same reason: an in-flight bundle holds a sender from the world that is
        // unloading, and the scheduler is registered permanently, so an execution left here
        // would resume against the next world.
        for (com.mahghuuuls.agenttesttoolkit.bundle.BundleExecution abandoned
                : com.mahghuuuls.agenttesttoolkit.bundle.Bundles.scheduler().discardAll()) {
            com.mahghuuuls.agenttesttoolkit.bundle.BundleRecorder.recordDiscarded(abandoned);
        }
    }
}
