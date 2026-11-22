package com.mahghuuuls.agenttesttoolkit.client;

import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.net.ToolkitChannel;
import com.mahghuuuls.agenttesttoolkit.net.ToolkitNotice;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;

/**
 * Connects {@link ChatHistory} to the game: the configuration at start, the toolkit's key binding,
 * the swap of the plain chat screen, and the failure notices from the server.
 *
 * <p>The chat overlay itself is cancelled by the screenshot taker's chat handler, which already
 * leaves the chat out of a picture's frame; one handler for one event keeps the two reasons from
 * working against each other.
 */
public final class ChatHistoryListener implements ToolkitChannel.NoticeReceiver {

    /** The key binding's name, as the Controls screen shows it through the language file. */
    static final String KEY_NAME = "key.agenttesttoolkit.toggleChatHistory";
    /** The toolkit's own category in the Controls screen. */
    static final String KEY_CATEGORY = "key.categories.agenttesttoolkit";
    /** The text the opener put in the chat box, in the obfuscated game; the helper maps it in a development game. */
    private static final String DEFAULT_TEXT = "field_146409_v";

    private final ChatHistory history;
    private final PromptPanel panel;
    /**
     * Unbound until the person sets it, so it takes no key another mod or a pack already uses.
     * Only in play: with a screen open, typing must go to the screen.
     */
    private final KeyBinding toggle = new KeyBinding(KEY_NAME, KeyConflictContext.IN_GAME, Keyboard.KEY_NONE,
            KEY_CATEGORY);
    /** Whether the field holding the opener's text was looked up; client thread only. */
    private boolean defaultTextResolved;
    /** That field, or null when this game's chat screen does not have it. */
    private Field defaultTextField;
    /** Set once the opener's text could not be read, so the failure is recorded once. */
    private boolean defaultTextUnreadable;

    public ChatHistoryListener(ChatHistory history, PromptPanel panel) {
        this.history = history;
        this.panel = panel;
    }

    /** Registers the key binding and sets the state from the configuration. Once, at mod start. */
    public void start(boolean hideFromConfig) {
        ClientRegistry.registerKeyBinding(toggle);
        write(history.startFromConfig(hideFromConfig));
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        while (toggle.isPressed()) {
            write(history.toggleByKey());
            if (!history.showsNotice()) {
                // The failure lines are back in sight in the chat itself.
                panel.clearNotice();
            }
        }
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        GuiScreen opening = event.getGui();
        if (!ChatHistory.replacesScreen(opening == null ? null : opening.getClass().getName(), history.isHidden())) {
            return;
        }
        String defaultText = defaultText((GuiChat) opening);
        if (defaultText != null) {
            event.setGui(new HistoryHiddenChat(defaultText));
        }
    }

    /**
     * The text the opener put in the box, such as the command key's {@code /}; null when it cannot
     * be read. The game's own screen is then kept: losing the {@code /} would turn a command into
     * a chat message, while the kept screen still draws no lines, as the overlay is cancelled.
     */
    private String defaultText(GuiChat screen) {
        try {
            if (!defaultTextResolved) {
                defaultTextResolved = true;
                defaultTextField = ObfuscationReflectionHelper.findField(GuiChat.class, DEFAULT_TEXT);
            }
            Object value = defaultTextField == null ? null : defaultTextField.get(screen);
            if (value == null || value instanceof String) {
                return value == null ? "" : (String) value;
            }
            unreadable("the field holds " + value.getClass().getName());
        } catch (IllegalAccessException | RuntimeException e) {
            unreadable(e.toString());
        }
        return null;
    }

    private void unreadable(String detail) {
        if (!defaultTextUnreadable) {
            defaultTextUnreadable = true;
            ToolkitLog.error("The chat screen's opening text cannot be read; hidden chat lines keep their hover and clicks",
                    detail);
        }
    }

    /** Network thread: shown on the client thread, and only while the history is hidden. */
    @Override
    public void receive(ToolkitNotice notice) {
        Minecraft.getMinecraft().addScheduledTask(deliveryOf(history, panel, notice));
    }

    /** The work a received notice hands to the client thread. */
    static Runnable deliveryOf(final ChatHistory history, final PromptPanel panel, final ToolkitNotice notice) {
        return new Runnable() {
            @Override
            public void run() {
                deliver(history, panel, notice.getText(), notice.getTicks());
            }
        };
    }

    /** Shows a notice in the panel only while the history is hidden. Client thread. */
    static void deliver(ChatHistory history, PromptPanel panel, String text, int ticks) {
        if (history.showsNotice()) {
            panel.showNotice(text, ticks);
        }
    }

    private static void write(LogRecord record) {
        if (record != null) {
            ToolkitLog.write(record);
        }
    }
}
