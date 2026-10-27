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
import com.mahghuuuls.agenttesttoolkit.session.OperatorLogoutListener;
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
 * <p>{@code acceptableRemoteVersions = "*"} is deliberate: the toolkit is required on the
 * server and optional on the client, and a client without it must still be able to connect.
 * Without this attribute Forge would refuse such a client, which would make the toolkit
 * unusable for testing against a dedicated server.
 */
@Mod(
        modid = Tags.MOD_ID,
        name = Tags.MOD_NAME,
        version = Tags.VERSION,
        acceptableRemoteVersions = "*",
        // Ordering only, with no version: Baubles is optional (ARC-018).
        dependencies = "after:baubles"
)
public class AgentTestToolkitMod {

    /**
     * The client class is named as a string, never referenced as a type from common
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
        // that ordering and is tested directly.
        List<String> configProblems =
                ToolkitConfigLoader.load(event.getModConfigurationDirectory());

        // Bundles load after configuration, since the bundles directory sits inside the
        // toolkit configuration directory that the loader establishes.
        BundleRegistry.LoadReport bundles = Bundles.reload();

        // The channel is registered on both sides before any connection exists (ARC-017).
        com.mahghuuuls.agenttesttoolkit.net.ToolkitChannel.register();

        ToolkitStartup.announce(Tags.VERSION, bundles, configProblems);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Subscribed once and never unsubscribed. The resting cost is a null check, and
        // always-registered handlers remove a whole class of lifecycle bug.
        damageObserver = new DamageObserver();

        MinecraftForge.EVENT_BUS.register(new SessionTicker());
        MinecraftForge.EVENT_BUS.register(new BundleTicker());
        MinecraftForge.EVENT_BUS.register(new BlockPlaceObserver());
        MinecraftForge.EVENT_BUS.register(new DiscreteActionObserver());
        MinecraftForge.EVENT_BUS.register(new EntitySpawnObserver());
        MinecraftForge.EVENT_BUS.register(damageObserver);
        MinecraftForge.EVENT_BUS.register(new JoinAutomation());
        MinecraftForge.EVENT_BUS.register(new OperatorLogoutListener());
        MinecraftForge.EVENT_BUS.register(new com.mahghuuuls.agenttesttoolkit.inbox.InboxPoller());
        MinecraftForge.EVENT_BUS.register(new com.mahghuuuls.agenttesttoolkit.inbox.InboxLogoutListener());
        com.mahghuuuls.agenttesttoolkit.inbox.InboxPoller.setHandler(
                new com.mahghuuuls.agenttesttoolkit.command.InboxDispatch());
        MinecraftForge.EVENT_BUS.register(new com.mahghuuuls.agenttesttoolkit.scenario.Scenarios());
        MinecraftForge.EVENT_BUS.register(new com.mahghuuuls.agenttesttoolkit.session.WorldChangeListener());
        // Checkpoints belong to the session but live in wait, which session may not depend on.
        // Installed here so every stop forgets them: the command, a replacing start, and a
        // world change alike.
        com.mahghuuuls.agenttesttoolkit.session.SessionManager.setStopHook(new Runnable() {
            @Override
            public void run() {
                com.mahghuuuls.agenttesttoolkit.wait.OperatorCheckpoints.reset();
            }
        });

        // No-op on a server. The client implementation registers its own handler.
        proxy.applyClientDefaults();
    }

    @Mod.EventHandler
    public void postInit(net.minecraftforge.fml.common.event.FMLPostInitializationEvent event) {
        // Decided once every mod has loaded; the bridge class is loaded only when Baubles is here.
        String problem = com.mahghuuuls.agenttesttoolkit.integration.Integrations.init(
                net.minecraftforge.fml.common.Loader.isModLoaded(
                        com.mahghuuuls.agenttesttoolkit.integration.Integrations.BAUBLES_MOD_ID));
        if (problem != null) {
            com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog.error("Baubles integration unavailable", problem);
        }
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        // Files may have changed since the game started, as when an agent edits a bundle while
        // the owner sits at the title screen. Reloading here makes a world run them as they are
        // on disk, without anyone typing devtool reload (REQ-199).
        // Contained: a broken file must never stop a world from opening, and the command below
        // must still be registered.
        try {
            net.minecraft.world.World overworld = event.getServer().getEntityWorld();
            com.mahghuuuls.agenttesttoolkit.command.sub.ReloadSubCommand.reloadFromDisk(
                    overworld == null ? com.mahghuuuls.agenttesttoolkit.logging.RecordContext.Snapshot.NONE
                            : com.mahghuuuls.agenttesttoolkit.logging.RecordContext.snapshot(overworld),
                    com.mahghuuuls.agenttesttoolkit.command.sub.ReloadSubCommand.TRIGGER_SERVER_START);
        } catch (RuntimeException e) {
            com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog.error("Reload at server start failed", String.valueOf(e));
        }

        // A status file left by a crash or a killed process would say "open" for an inbox that
        // is closed. Only the toolkit's own file is touched.
        if (!com.mahghuuuls.agenttesttoolkit.inbox.InboxGate.isOpen()) {
            com.mahghuuuls.agenttesttoolkit.inbox.InboxStatus.delete(
                    com.mahghuuuls.agenttesttoolkit.inbox.InboxPoller.directory());
        }
        event.registerServerCommand(new DevToolCommand());
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        // Discard in-flight, server-bound transient state. Pending damage
        // correlations hold entity references from a world that is unloading, and the observer
        // is registered permanently, so leaving them would leak stale entries into the next
        // world's first tick.
        //
        // This handler must NEVER clear ToolkitState. Leaving a single player world stops the
        // integrated server, and Forge documents this event as the place to reset static
        // state. Doing that here would destroy the active session and break testing a
        // disconnect and reconnect inside one session. Session and logging category state are
        // meant to die with the JVM and nothing sooner.
        if (damageObserver != null) {
            damageObserver.discardPending();
        }

        // Same rule, same reason: an in-flight bundle holds a sender from the world that is
        // unloading, and the scheduler is registered permanently, so an execution left here
        // would resume against the next world.
        try {
            for (BundleExecution abandoned : Bundles.scheduler().discardAll()) {
                BundleRecorder.recordDiscarded(abandoned);
            }
        } finally {
            // The inbox is the exception to keeping state: with no server there is nothing to
            // run requests on, and in single player the opener has already left. Closed with a
            // record, in a finally so a failure above cannot leave it open into the next world.
            com.mahghuuuls.agenttesttoolkit.inbox.InboxGate.close("serverStopped",
                    com.mahghuuuls.agenttesttoolkit.logging.RecordContext.Snapshot.NONE);
        }
    }
}
