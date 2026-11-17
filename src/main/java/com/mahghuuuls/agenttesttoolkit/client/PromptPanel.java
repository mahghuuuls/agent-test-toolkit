package com.mahghuuuls.agenttesttoolkit.client;

import com.mahghuuuls.agenttesttoolkit.net.PromptPanelMessage;
import com.mahghuuuls.agenttesttoolkit.net.ToolkitChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.potion.PotionEffect;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The toolkit's prompt panel: a checkpoint's label, and how to answer it, on the right of the
 * screen, where the chat a bundle prints cannot cover it.
 *
 * <p>Drawn after every other part of the HUD, so nothing of the HUD hides it; toasts and open
 * screens are drawn later and go over it. It is part of the HUD, so F1 and a screenshot that
 * leaves the HUD out leave it out too. One panel at a time: a newer one replaces it. It goes away
 * when the server hides it, and a hide meant for an older panel leaves a newer one up; when its
 * ticks pass, for a panel sent with any (an armed checkpoint's, counted only while the game runs,
 * as the server's wait is; a cue's has none and stays until it is hidden); and when the client
 * leaves the server.
 */
public final class PromptPanel implements ToolkitChannel.PanelReceiver {

    /** Space inside the panel around the text. */
    private static final int PAD = 4;
    /** One text row. */
    private static final int ROW = 10;
    /** Subtitle rows kept clear at the bottom right while subtitles are on. */
    private static final int SUBTITLE_ROWS = 3;
    private static final int BACKGROUND = 0xC0101010;
    private static final int ACCENT = 0xFFFFAA00;

    /** The panel shown now; client thread only. */
    private Shown shown;

    private static final class Shown {
        final String token;
        final String label;
        final List<String> lines;
        final int timeoutTicks;
        int ticks;

        Shown(PromptPanelMessage message) {
            this.token = message.getToken();
            this.label = message.getLabel();
            this.lines = new ArrayList<String>(message.getLines());
            this.timeoutTicks = message.getTimeoutTicks();
        }
    }

    /** Network thread: handed to the client thread, which owns the panel. */
    @Override
    public void receive(final PromptPanelMessage message) {
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                apply(message);
            }
        });
    }

    private void apply(PromptPanelMessage message) {
        if (message.isShow()) {
            shown = new Shown(message);
        } else if (shown != null && message.hides(shown.token)) {
            shown = null;
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || shown == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) {
            shown = null;
            return;
        }
        if (mc.isGamePaused()) {
            return;
        }
        shown.ticks++;
        if (shown.timeoutTicks > 0 && shown.ticks >= shown.timeoutTicks) {
            shown = null;
        }
    }

    /** Posted on the network thread as the connection closes. */
    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                shown = null;
            }
        });
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        Shown current = shown;
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL || current == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) {
            return;
        }
        ScaledResolution resolution = event.getResolution();
        int screenWidth = resolution.getScaledWidth();
        int screenHeight = resolution.getScaledHeight();
        FontRenderer font = mc.fontRenderer;

        int textWidth = Math.max(0, PanelLayout.maxWidth(screenWidth) - 2 * PAD - 2);
        List<String> rows = new ArrayList<String>();
        for (String part : font.listFormattedStringToWidth(TextFormatting.BOLD + current.label, textWidth)) {
            rows.add(TextFormatting.GOLD + "" + TextFormatting.BOLD + part);
        }
        for (String line : current.lines) {
            rows.addAll(font.listFormattedStringToWidth(line, textWidth));
        }
        int widest = 0;
        for (String row : rows) {
            widest = Math.max(widest, font.getStringWidth(row));
        }

        int bottom = mc.gameSettings.showSubtitles
                ? screenHeight - 30 - ROW / 2 - (SUBTITLE_ROWS - 1) * ROW : screenHeight;
        // The toasts shown in this frame, read again each frame since they come and go; when the
        // overlay cannot be read, every row counts as used.
        PanelLayout.Rect rect = PanelLayout.place(screenWidth, screenHeight, widest + 2 * PAD + 2,
                rows.size() * ROW + 2 * PAD - 1, potionRowsBottom(mc),
                PanelLayout.toastRows(ToastHide.visibleSlots(mc)), bottom,
                sidebar(mc, font, screenWidth, screenHeight));
        if (rect.width <= 0 || rect.height <= 0) {
            return;
        }

        // Drawing a rectangle turns blending off at its end; whatever is drawn after the HUD,
        // vanilla or another mod's overlay, is left the blending it had before the panel.
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        GlStateManager.enableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        Gui.drawRect(rect.x, rect.y, rect.right(), rect.bottom(), BACKGROUND);
        Gui.drawRect(rect.x, rect.y, rect.x + 2, rect.bottom(), ACCENT);
        int fits = Math.max(1, (rect.height - 2 * PAD + 1) / ROW);
        int y = rect.y + PAD;
        for (int i = 0; i < rows.size() && i < fits; i++) {
            font.drawStringWithShadow(rows.get(i), rect.x + PAD + 2, y, 0xFFFFFF);
            y += ROW;
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        if (blend) {
            GlStateManager.enableBlend();
        } else {
            GlStateManager.disableBlend();
        }
    }

    /** The bottom of the potion icon rows at the top right, as the HUD draws them; 0 for none. */
    private static int potionRowsBottom(Minecraft mc) {
        boolean beneficial = false;
        boolean harmful = false;
        for (PotionEffect effect : mc.player.getActivePotionEffects()) {
            if (!effect.getPotion().shouldRenderHUD(effect) || !effect.doesShowParticles()) {
                continue;
            }
            if (effect.getPotion().isBeneficial()) {
                beneficial = true;
            } else {
                harmful = true;
            }
        }
        int demo = mc.isDemo() ? 15 : 0;
        return harmful ? 27 + 24 + demo : beneficial ? 1 + 24 + demo : 0;
    }

    /** The scoreboard sidebar's rectangle as the HUD draws it, or null when none is shown. */
    private static PanelLayout.Rect sidebar(Minecraft mc, FontRenderer font, int screenWidth, int screenHeight) {
        if (mc.world == null) {
            return null;
        }
        Scoreboard scoreboard = mc.world.getScoreboard();
        ScoreObjective objective = null;
        ScorePlayerTeam team = scoreboard.getPlayersTeam(mc.player.getName());
        if (team != null && team.getColor().getColorIndex() >= 0) {
            objective = scoreboard.getObjectiveInDisplaySlot(3 + team.getColor().getColorIndex());
        }
        if (objective == null) {
            objective = scoreboard.getObjectiveInDisplaySlot(1);
        }
        if (objective == null) {
            return null;
        }
        List<Score> scores = new ArrayList<Score>();
        Collection<Score> sorted = scoreboard.getSortedScores(objective);
        for (Score score : sorted) {
            if (score.getPlayerName() != null && !score.getPlayerName().startsWith("#")) {
                scores.add(score);
            }
        }
        if (scores.size() > 15) {
            scores = scores.subList(scores.size() - 15, scores.size());
        }
        int width = font.getStringWidth(objective.getDisplayName());
        for (Score score : scores) {
            String row = ScorePlayerTeam.formatPlayerName(scoreboard.getPlayersTeam(score.getPlayerName()),
                    score.getPlayerName()) + ": " + TextFormatting.RED + score.getScorePoints();
            width = Math.max(width, font.getStringWidth(row));
        }
        int rowsHeight = scores.size() * font.FONT_HEIGHT;
        int bottom = screenHeight / 2 + rowsHeight / 3;
        int top = bottom - rowsHeight - font.FONT_HEIGHT - 1;
        int left = screenWidth - width - 3 - 2;
        return new PanelLayout.Rect(left, top, screenWidth - 1 - left, bottom - top);
    }
}
