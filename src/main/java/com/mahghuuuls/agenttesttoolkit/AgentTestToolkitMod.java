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
    }

    @Mod.EventHandler
    public void init(net.minecraftforge.fml.common.event.FMLInitializationEvent event) {
        // Subscribed once and never unsubscribed. See ARC-006: the resting cost is a null
        // check, and always-registered handlers remove a whole class of lifecycle bug.
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new SessionTicker());
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new DevToolCommand());
    }

    // Deliberately absent: any FMLServerStoppedEvent handler that clears ToolkitState.
    //
    // ARC-001 and REQ-052. Leaving a single player world stops the integrated server, and
    // Forge documents that event as the place to reset static state. Doing so here would
    // destroy the active session and break the disconnect-and-reconnect testing REQ-052
    // exists to enable. The state is meant to die with the JVM and nothing sooner.
    //
    // In-flight bundle executions are the opposite case and must be discarded on server stop,
    // because they hold a sender and a server reference. That belongs to IMP-014, see ARC-002.
}
