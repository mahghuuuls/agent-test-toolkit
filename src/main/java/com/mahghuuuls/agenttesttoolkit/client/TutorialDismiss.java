package com.mahghuuuls.agenttesttoolkit.client;

import com.mahghuuuls.agenttesttoolkit.config.ToolkitConfigLoader;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Sets the tutorial to done at the first title screen of a launch, when {@code client.dismissTutorial}
 * asks it, so the tutorial's hints (move, look, punch a tree) never cover a test's screen or picture.
 *
 * <p>It does what finishing the tutorial does: the tutorial's step becomes none, which the game
 * saves in {@code options.txt} itself. Done once per launch, at the title screen, where no world
 * shows a hint yet; a tutorial already done is left alone, so nothing is written then.
 */
public final class TutorialDismiss {

    private boolean handled;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (handled || event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (!(mc.currentScreen instanceof GuiMainMenu)) {
            return;
        }
        // The client's configuration is loaded by now: the title screen comes after mod loading.
        handled = true;
        if (!ToolkitConfigLoader.get().isClientDismissTutorial()) {
            return;
        }
        TutorialSteps was = mc.gameSettings.tutorialStep;
        if (was == TutorialSteps.NONE) {
            return;
        }
        mc.getTutorial().setStep(TutorialSteps.NONE);
        ToolkitLog.write(LogRecord.of(EventType.ENVIRONMENT)
                .add("side", "CLIENT")
                .add("tutorialDismissed", true)
                .add("tutorialWas", was.getName()));
    }
}
