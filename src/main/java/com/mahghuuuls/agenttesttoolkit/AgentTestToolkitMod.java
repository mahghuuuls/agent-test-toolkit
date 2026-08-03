package com.mahghuuuls.agenttesttoolkit;

import com.mahghuuuls.agenttesttoolkit.command.DevToolCommand;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
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
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new DevToolCommand());
    }
}
