package com.mahghuuuls.agenttesttoolkit.proxy;

import com.mahghuuuls.agenttesttoolkit.config.ToolkitConfig;
import com.mahghuuuls.agenttesttoolkit.config.ToolkitConfigLoader;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import net.minecraft.client.Minecraft;
// net.minecraft.util, not net.minecraft.client.audio. SoundCategory is a common type; only
// GameSettings, which reads and writes the levels, is client only.
import net.minecraft.util.SoundCategory;
import net.minecraft.client.settings.GameSettings;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Applies display and audio defaults on a physical client.
 *
 * <p>Loaded only on a client. Nothing in common code references this class as a type; Forge
 * resolves it from the class name string in {@code @SidedProxy}. See {@link CommonProxy} for
 * why that matters.
 *
 * <p><b>Off by default.</b> These are the operator's own application settings, not game state,
 * and a diagnostic tool rewriting them unasked would be a surprising thing for a mod to do.
 * Master, weather, hostile, friendly and ambient volume change only when a level is configured:
 * brightness and music serve a test environment, but these are preferences, so their defaults
 * leave them alone.
 *
 * <p>Applied once per world join rather than once per launch, because a test session usually
 * means entering a fresh world and the settings should hold for it.
 */
public class ClientProxy extends CommonProxy {

    private boolean applied;

    @Override
    public void applyClientDefaults() {
        MinecraftForge.EVENT_BUS.register(this);
        // The one client-only handler besides this class (rule 12): screenshots on request.
        com.mahghuuuls.agenttesttoolkit.client.ScreenshotTaker taker =
                new com.mahghuuuls.agenttesttoolkit.client.ScreenshotTaker();
        com.mahghuuuls.agenttesttoolkit.net.ToolkitChannel.setClientReceiver(taker);
        MinecraftForge.EVENT_BUS.register(taker);
    }

    @SubscribeEvent
    public void onJoinWorld(EntityJoinWorldEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null || event.getEntity() != mc.player) {
            return;
        }
        if (applied) {
            return;
        }
        applied = true;

        ToolkitConfig config = ToolkitConfigLoader.get();
        if (!config.isClientDefaultsEnabled()) {
            return;
        }

        GameSettings settings = mc.gameSettings;
        float previousGamma = settings.gammaSetting;
        float previousMusic = settings.getSoundLevel(SoundCategory.MUSIC);
        // Only the categories with a configured level; the rest are left as they were.
        java.util.List<ToolkitConfig.Volume> set = new java.util.ArrayList<ToolkitConfig.Volume>();
        float[] previous = new float[ToolkitConfig.Volume.values().length];
        for (ToolkitConfig.Volume volume : ToolkitConfig.Volume.values()) {
            if (config.isClientVolumeSet(volume)) {
                set.add(volume);
                previous[volume.ordinal()] = settings.getSoundLevel(category(volume));
            }
        }

        settings.gammaSetting = config.getClientBrightness();
        settings.setSoundLevel(SoundCategory.MUSIC, config.getClientMusicVolume());
        for (ToolkitConfig.Volume volume : set) {
            settings.setSoundLevel(category(volume), config.getClientVolume(volume));
        }
        settings.saveOptions();

        // An operator whose brightness changed must be able to find out why from the log rather
        // than by guessing. Both the old and new values are recorded so the change can be
        // undone by hand.
        LogRecord record = LogRecord.of(EventType.ENVIRONMENT)
                .add("side", "CLIENT")
                .add("clientDefaultsApplied", true)
                .addDecimal("brightnessWas", previousGamma)
                .addDecimal("brightnessNow", settings.gammaSetting)
                .addDecimal("musicWas", previousMusic)
                .addDecimal("musicNow", config.getClientMusicVolume());
        for (ToolkitConfig.Volume volume : set) {
            // Only when changed, so an absent field always means "left as it was".
            record.addDecimal(volume.getRecordPrefix() + "Was", previous[volume.ordinal()])
                    .addDecimal(volume.getRecordPrefix() + "Now", settings.getSoundLevel(category(volume)));
        }
        ToolkitLog.write(record);
    }

    private static SoundCategory category(ToolkitConfig.Volume volume) {
        return SoundCategory.getByName(volume.getCategoryName());
    }
}
