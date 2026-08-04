package com.mahghuuuls.agenttesttoolkit;

import com.mahghuuuls.agenttesttoolkit.command.DevToolCommand;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
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

    /** Held so its pending correlations can be discarded when the server stops. */
    private com.mahghuuuls.agenttesttoolkit.observe.damage.DamageObserver damageObserver;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {

        // REQ-111: one concise initialization record. Configuration contents are deliberately
        // not dumped here; only the summary, plus any load errors as separate records later.
        //
        // REQ-111 also calls for the number of bundles loaded. Bundles do not exist until
        // IMP-012, so that field is absent rather than reported as a misleading zero, per the
        // omit-rather-than-placeholder rule. IMP-012 adds it. The loggingCategoriesEnabled
        // field follows the owner specification's startup example and is genuinely zero here,
        // since REQ-035 requires every category to start disabled.
        ToolkitLog.write(LogRecord.of(EventType.STARTUP)
                .add("version", Tags.VERSION)
                .add("loggingCategoriesEnabled", 0));

        // Configuration loads AFTER the startup record on purpose. REQ-111 requires
        // configuration and parsing errors to follow the initialization summary as separate
        // records, so a reader always meets the version and feature summary first and then
        // any problems, rather than errors from an unidentified build.
        com.mahghuuuls.agenttesttoolkit.config.ToolkitConfigLoader.load(
                event.getModConfigurationDirectory());
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
    }
}
