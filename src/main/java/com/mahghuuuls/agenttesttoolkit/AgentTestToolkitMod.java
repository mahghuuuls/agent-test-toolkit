package com.mahghuuuls.agenttesttoolkit;

import com.mahghuuuls.agenttesttoolkit.bundle.BundleExecution;
import com.mahghuuuls.agenttesttoolkit.bundle.BundleRecorder;
import com.mahghuuuls.agenttesttoolkit.bundle.BundleRegistry;
import com.mahghuuuls.agenttesttoolkit.bundle.BundleTicker;
import com.mahghuuuls.agenttesttoolkit.bundle.Bundles;
import com.mahghuuuls.agenttesttoolkit.command.DevToolCommand;
import com.mahghuuuls.agenttesttoolkit.command.JoinAutomation;
import com.mahghuuuls.agenttesttoolkit.config.ToolkitConfigLoader;
import com.mahghuuuls.agenttesttoolkit.observe.BlockPlaceObserver;
import com.mahghuuuls.agenttesttoolkit.observe.DiscreteActionObserver;
import com.mahghuuuls.agenttesttoolkit.observe.EntitySpawnObserver;
import com.mahghuuuls.agenttesttoolkit.observe.damage.DamageObserver;
import com.mahghuuuls.agenttesttoolkit.proxy.CommonProxy;
import com.mahghuuuls.agenttesttoolkit.session.SessionTicker;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;

import java.util.List;

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
     *
     * <p>{@link CommonProxy} is imported because it is common. {@code ClientProxy} deliberately
     * is not, and must never be: importing it here would put a client only class on the
     * server's load path and stop a dedicated server booting.
     */
    @SidedProxy(
            clientSide = "com.mahghuuuls.agenttesttoolkit.proxy.ClientProxy",
            serverSide = "com.mahghuuuls.agenttesttoolkit.proxy.CommonProxy")
    public static CommonProxy proxy;

    /** Held so its pending correlations can be discarded when the server stops. */
    private DamageObserver damageObserver;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // Both loaders return their problems rather than logging as they go, so that the one
        // startup record can be written first and carry the bundle count. ToolkitStartup owns
        // that ordering rule and is tested directly; see REQ-111.
        List<String> configProblems =
                ToolkitConfigLoader.load(event.getModConfigurationDirectory());

        // Bundles load after configuration, since the bundles directory sits inside the
        // toolkit configuration directory that the loader establishes.
        BundleRegistry.LoadReport bundles = Bundles.reload();

        ToolkitStartup.announce(Tags.VERSION, bundles, configProblems);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Subscribed once and never unsubscribed. See ARC-006: the resting cost is a null
        // check, and always-registered handlers remove a whole class of lifecycle bug.
        damageObserver = new DamageObserver();

        MinecraftForge.EVENT_BUS.register(new SessionTicker());
        MinecraftForge.EVENT_BUS.register(new BundleTicker());
        MinecraftForge.EVENT_BUS.register(new BlockPlaceObserver());
        MinecraftForge.EVENT_BUS.register(new DiscreteActionObserver());
        MinecraftForge.EVENT_BUS.register(new EntitySpawnObserver());
        MinecraftForge.EVENT_BUS.register(damageObserver);
        MinecraftForge.EVENT_BUS.register(new JoinAutomation());

        // No-op on a server. The client implementation registers its own handler.
        proxy.applyClientDefaults();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new DevToolCommand());
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
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
        for (BundleExecution abandoned : Bundles.scheduler().discardAll()) {
            BundleRecorder.recordDiscarded(abandoned);
        }
    }
}
