package com.mahghuuuls.agenttesttoolkit.client;

import net.minecraft.client.gui.GuiChat;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.client.ClientCommandHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The vanilla chat screen for a client whose chat history is hidden: the input box, the line being
 * typed, sending, the sent-message history and tab completion all work as in the game's own
 * screen, which this extends and leaves in charge of them.
 *
 * <p>Three things differ. The chat lines are not drawn here (the chat overlay is cancelled while
 * the history is hidden), but the game still knows where each line would be, so a line under the
 * mouse would still give its hover text and take a click on a link it cannot be seen to have;
 * this screen gives hidden lines neither. And a list of several completions, which the game prints
 * as a chat line and so would be hidden too, is drawn just above the input box instead, from the
 * same completions, until a key other than Tab is pressed.
 */
public final class HistoryHiddenChat extends GuiChat {

    /** The key code of Tab, the completion key, as the game's own chat screen tests it. */
    private static final int TAB = 15;
    /** One text row above the input box. */
    private static final int ROW = 10;
    /** The background the game's chat screen draws behind the input box. */
    private static final int BACKGROUND = Integer.MIN_VALUE;

    /** Whether the last key was Tab, which is when the game asks the server for completions. */
    private boolean tabbed;
    /** The completions shown above the input box; empty when none are. */
    private final List<String> suggestions = new ArrayList<String>();

    /** @param defaultText the text the opener put in the box, such as the command key's {@code /} */
    public HistoryHiddenChat(String defaultText) {
        super(defaultText == null ? "" : defaultText);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        tabbed = keyCode == TAB;
        if (!tabbed) {
            // The game forgets a completion as soon as another key is typed; the list goes with it.
            suggestions.clear();
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void setCompletions(String... newCompletions) {
        super.setCompletions(newCompletions);
        if (!tabbed) {
            // The game takes completions only when it asked for them, at a press of Tab.
            return;
        }
        suggestions.clear();
        // The game's completer puts the client's own commands' completions first, then the server's.
        String[] local = ClientCommandHandler.instance.latestAutoComplete;
        if (local != null) {
            addNonEmpty(local);
        }
        addNonEmpty(newCompletions);
        if (suggestions.size() < 2) {
            // One completion is written into the box itself, so there is no list to show.
            suggestions.clear();
        }
    }

    private void addNonEmpty(String[] values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                suggestions.add(value);
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (suggestions.isEmpty()) {
            return;
        }
        StringBuilder joined = new StringBuilder();
        for (String suggestion : suggestions) {
            joined.append(joined.length() == 0 ? "" : ", ").append(suggestion);
        }
        List<String> rows = fontRenderer.listFormattedStringToWidth(joined.toString(), Math.max(1, width - 8));
        int bottom = height - 14;
        int top = bottom - rows.size() * ROW - 2;
        drawRect(2, top, width - 2, bottom, BACKGROUND);
        int y = top + 2;
        for (String row : rows) {
            fontRenderer.drawStringWithShadow(row, 4, y, 0xFFFFFF);
            y += ROW;
        }
    }

    /** A hidden line shows no hover text. */
    @Override
    protected void handleComponentHover(ITextComponent component, int x, int y) {
    }

    /**
     * A click on a hidden line does nothing; the game's screen then treats it as a click on the
     * input box, as for a click where no line is.
     */
    @Override
    public boolean handleComponentClick(ITextComponent component) {
        return false;
    }
}
