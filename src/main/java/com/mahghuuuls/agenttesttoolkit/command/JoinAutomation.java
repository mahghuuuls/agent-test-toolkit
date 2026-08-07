package com.mahghuuuls.agenttesttoolkit.command;

import com.mahghuuuls.agenttesttoolkit.bundle.BundleDefinition;
import com.mahghuuuls.agenttesttoolkit.bundle.BundleExecution;
import com.mahghuuuls.agenttesttoolkit.bundle.BundleRecorder;
import com.mahghuuuls.agenttesttoolkit.bundle.Bundles;
import com.mahghuuuls.agenttesttoolkit.config.ToolkitConfig;
import com.mahghuuuls.agenttesttoolkit.config.ToolkitConfigLoader;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/**
 * Runs a configured bundle when an operator joins.
 *
 * <h2>Two gates, not one</h2>
 *
 * <p>Both {@code join.enabled} and a non-empty {@code join.bundle} are required, and both
 * default to off. That is more conservative than it looks necessary, and it is deliberate:
 * the toolkit also seeds example bundles on first run, and a join hook defaulting to one of
 * them would combine into "installing this mod changes your world the first time you log in".
 * Neither feature intends that alone, so neither is permitted to imply it.
 *
 * <p>Only operators trigger this. A bundle runs as its caller, so a non-operator joining would
 * run it with their permissions and every command would fail, filling the log with noise that
 * looks like a toolkit fault.
 */
public final class JoinAutomation {

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        EntityPlayer player = event.player;
        if (!(player instanceof EntityPlayerMP) || player.world == null || player.world.isRemote) {
            return;
        }

        ToolkitConfig config = ToolkitConfigLoader.get();
        if (!config.isJoinExecutionEnabled()) {
            return;
        }

        String name = config.getJoinBundleName();
        if (name.isEmpty()) {
            // Enabled with nothing configured. Reported rather than ignored: someone who turned
            // this on and saw nothing happen deserves to know why, and a silent no-op where
            // the operator asked for an action is never acceptable.
            ToolkitLog.error("Join execution is enabled but no bundle is configured",
                    "set join.bundle in devtool.cfg");
            return;
        }

        EntityPlayerMP operator = (EntityPlayerMP) player;
        if (!operator.canUseCommand(2, "devtool")) {
            return;
        }

        BundleDefinition bundle = Bundles.registry().get(name);
        if (bundle == null) {
            ToolkitLog.error("Join bundle not found", name);
            return;
        }

        try {
            BundleExecution execution = new BundleExecution(bundle,
                    new ServerCommandDispatcher(operator.server, operator),
                    new SenderContextSource(operator));
            BundleRecorder.recordStart(execution);
            Bundles.scheduler().submit(execution);
        } catch (RuntimeException e) {
            // A failure here must never prevent the join from completing. A player
            // kept out of their own world by a diagnostic tool is a far worse outcome than a
            // setup routine that did not run.
            ToolkitLog.error("Join bundle could not be started",
                    name + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
