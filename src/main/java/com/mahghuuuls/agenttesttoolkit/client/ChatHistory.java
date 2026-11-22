package com.mahghuuuls.agenttesttoolkit.client;

import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;

/**
 * Whether the chat history is hidden on this client, and the rules that follow from it.
 *
 * <p>A person watching an agent drive the game may want the chat lines off the screen while
 * still typing commands. Hiding never deletes anything: every line still reaches the chat and
 * the log, so showing the history again shows what arrived meanwhile. The configuration sets the
 * state once at game start; the key binding flips it until the game closes. Nothing is saved,
 * so a game started without the option always starts with the history shown.
 *
 * <p>While hidden: the chat overlay is not drawn (in play and behind the open chat screen), the
 * plain chat screen is swapped for one that gives hidden lines no hover text and no clicks, and
 * a failure line the server also sends as a notice is shown in the prompt panel, since the chat
 * line itself cannot be seen.
 *
 * <p>Free of game types, so the transitions, their records and the screen rule are tested
 * without a game. Client thread only.
 */
public final class ChatHistory {

    /** The option's name in the records. */
    public static final String OPTION = "hideChatHistory";
    /** A change made by the configuration at game start. */
    public static final String BY_CONFIG = "config";
    /** A change made by the key binding. */
    public static final String BY_KEY = "key";

    /**
     * The vanilla chat screen, compared by name so this stays free of game types. Only exactly
     * this class is swapped: a subclass, such as the bed screen's chat, and another mod's chat
     * screen have behaviour of their own that a swap would lose.
     */
    static final String VANILLA_CHAT = "net.minecraft.client.gui.GuiChat";

    private boolean hidden;

    /**
     * Sets the state from the configuration at game start.
     *
     * @return the record to write, or null when the option leaves the history shown, which is
     *         the state every game starts in and so no change
     */
    public LogRecord startFromConfig(boolean hide) {
        if (hide == hidden) {
            return null;
        }
        hidden = hide;
        return record(hidden, BY_CONFIG);
    }

    /** Flips the state for a press of the key, and returns the record to write. */
    public LogRecord toggleByKey() {
        hidden = !hidden;
        return record(hidden, BY_KEY);
    }

    public boolean isHidden() {
        return hidden;
    }

    /**
     * Whether a failure notice from the server is shown in the prompt panel: only while the
     * history is hidden. With the history shown, the same line is already readable in the chat.
     */
    public boolean showsNotice() {
        return hidden;
    }

    /**
     * Whether the screen being opened is swapped for the one that keeps hidden lines inert.
     *
     * @param screenClassName the class name of the screen being opened; null when a screen is closing
     */
    public static boolean replacesScreen(String screenClassName, boolean hidden) {
        return hidden && VANILLA_CHAT.equals(screenClassName);
    }

    /** The client's record of a change. */
    static LogRecord record(boolean value, String by) {
        return LogRecord.of(EventType.CLIENT_OPTION)
                .add("side", "CLIENT")
                .add("option", OPTION)
                .add("value", value)
                .add("by", by);
    }
}
