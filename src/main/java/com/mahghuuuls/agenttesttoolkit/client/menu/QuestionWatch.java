package com.mahghuuuls.agenttesttoolkit.client.menu;

import com.mahghuuuls.agenttesttoolkit.client.screen.ScreenReader;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.client.GuiNotification;
import net.minecraftforge.fml.client.GuiOldSaveLoadConfirm;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.StartupQuery;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Watches for the questions a world asks as it opens, for one {@code open} step: the old save's
 * question, and Forge's startup questions and notices (missing registries, missing blocks or
 * items, a backup {@code level.dat}, a save that cannot be loaded). Armed for that step only, so
 * a question a person meets later is theirs.
 *
 * <p>Forge's startup questions show while the client is inside the world's start, where no
 * client tick runs, so everything here happens as the question's screen opens: the question is
 * recorded, and by the step's {@code onQuestion} either reported at once (the screen stays for a
 * person) or answered as the screen's own button does; {@link QuestionAnswers} decides which. A
 * startup question is answered by a stand-in screen put in its place, which gives the answer when
 * the loading loop first draws it, as a click would come; answering inside the opening itself
 * would let Forge's next question be lost while it hands the first one over. The old save's question is a menu screen where the
 * client ticks, so its opening is cancelled and its own button's action is taken on the next
 * tick. Escape is never sent to these screens: on a startup question it would leave the world's
 * start waiting for ever.
 *
 * <p>Client thread only.
 */
final class QuestionWatch {

    /** Where the step's outcome goes. */
    interface Owner {
        /** A question under {@code report}: the step ends now, the screen staying for a person. */
        void reported(String kind, String text);

        /** A question answered no: the world does not open. */
        void cancelled(String kind);
    }

    /** The step's reason when a question ended it under {@code report}. */
    static final String REASON_QUESTION = "question";
    /** The step's reason when its question was answered no. */
    static final String REASON_CANCELLED = "cancelled";

    /** {@code GuiScreen.actionPerformed(GuiButton)}. */
    private static final String ACTION_PERFORMED = "func_146284_a";

    private final QuestionAnswers answers = new QuestionAnswers();
    private MenuRequest request;
    private Owner owner;
    /** The old save's question, to be answered at the next client tick, and how. */
    private GuiOldSaveLoadConfirm oldSave;
    private QuestionAnswers.Decision oldSaveAnswer;

    /** Watches for this step's questions until {@link #disarm}. */
    void arm(MenuRequest request, Owner owner) {
        this.request = request;
        this.owner = owner;
        this.oldSave = null;
        this.oldSaveAnswer = null;
        answers.arm(request.getOnQuestion());
    }

    void disarm() {
        answers.disarm();
        request = null;
        owner = null;
        oldSave = null;
        oldSaveAnswer = null;
    }

    /** At the lowest priority, so it sees the screen that is really about to open. */
    void onGuiOpen(GuiOpenEvent event) {
        GuiScreen gui = event.getGui();
        if (!answers.isArmed() || gui == null) {
            return;
        }
        List<String> hierarchy = ScreenReader.hierarchy(gui.getClass());
        if (!ConfirmScreens.isQuestion(hierarchy)) {
            return;
        }
        StartupQuery query = ConfirmScreens.isStartupQuestion(hierarchy) ? queryOf((GuiNotification) gui) : null;
        String text = query != null ? query.getText() : oldSaveText(gui);
        QuestionAnswers.Decision decision = answers.opened(hierarchy, text, query != null);
        if (decision == null) {
            return;
        }
        record("question", decision.kind, null, text);
        if (decision.act == QuestionAnswers.Act.REPORT) {
            // Left for a person; also a startup question whose query cannot be read here.
            Owner o = owner;
            disarm();
            o.reported(decision.kind, ConfirmScreens.oneLine(text));
        } else if (decision.act == QuestionAnswers.Act.ANSWER_NEXT_TICK) {
            event.setCanceled(true);
            oldSave = (GuiOldSaveLoadConfirm) gui;
            oldSaveAnswer = decision;
        } else {
            event.setGui(new Answer(query, decision));
        }
    }

    /** A client tick at a menu: the old save's question, answered as its own button would. */
    void tick(Minecraft mc) {
        GuiOldSaveLoadConfirm screen = oldSave;
        QuestionAnswers.Decision decision = oldSaveAnswer;
        if (screen == null || !answers.isArmed()) {
            return;
        }
        oldSave = null;
        oldSaveAnswer = null;
        record("answered", decision.kind, decision.answer, null);
        Owner o = owner;
        boolean ends;
        try {
            // The screen never opened, so it is given the game it would have had; then its own
            // action: yes backs the save up into a zip and opens the world, no goes back.
            screen.mc = mc;
            Method action = ObfuscationReflectionHelper.findMethod(GuiScreen.class, ACTION_PERFORMED, void.class,
                    GuiButton.class);
            ends = answers.answering(decision);
            if (ends) {
                disarm();
            }
            action.invoke(screen, new GuiButton(decision.yes ? 0 : 1, 0, 0, ""));
        } catch (InvocationTargetException thrown) {
            // What the screen's own action threw goes on as it would from its button: a world
            // that fails to start is the game's crash report, not the toolkit's.
            Throwable cause = thrown.getCause();
            if (cause instanceof StartupQuery.AbortedException) {
                return;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException(cause);
        } catch (IllegalAccessException | RuntimeException failure) {
            // Not answered: shown to a person instead, and the step says so.
            disarm();
            mc.displayGuiScreen(screen);
            o.reported(decision.kind, "notAnswered:" + failure.getClass().getSimpleName());
            return;
        }
        if (ends) {
            o.cancelled(decision.kind);
        }
    }

    /**
     * Stands in for Forge's question or notice and gives the answer, as the screen's own button
     * does, the first time the loading loop draws it: the screen goes, the answer is set for a
     * question, and the waiting world start is let go.
     */
    private final class Answer extends GuiNotification {
        private final StartupQuery question;
        private final QuestionAnswers.Decision decision;
        private boolean given;

        Answer(StartupQuery question, QuestionAnswers.Decision decision) {
            super(question);
            this.question = question;
            this.decision = decision;
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            if (given) {
                return;
            }
            given = true;
            Owner o = owner;
            MenuRequest r = request;
            record("answered", decision.kind, decision.answer, null);
            boolean ends = answers.answering(decision);
            if (ends) {
                disarm();
            }
            FMLClientHandler.instance().showGuiScreen(null);
            if (decision.twoButtons) {
                question.setResult(decision.yes);
            }
            question.finish();
            if (ends && o != null && r != null) {
                o.cancelled(decision.kind);
            }
        }
    }

    private void record(String action, String kind, String answer, String text) {
        MenuRequest r = request;
        LogRecord record = LogRecord.of(EventType.MENU_STEP)
                .add("side", "CLIENT")
                .add("action", action)
                .add("id", r == null ? null : r.getId())
                .add("step", r == null ? null : r.getStep())
                .add("question", kind)
                .add("answer", answer);
        if (text != null) {
            record.add("text", ConfirmScreens.oneLine(text));
        }
        ToolkitLog.write(record);
    }

    /** Forge's question behind its screen; null when this Forge keeps it elsewhere. */
    private static StartupQuery queryOf(GuiNotification screen) {
        try {
            Field field = GuiNotification.class.getDeclaredField("query");
            field.setAccessible(true);
            Object value = field.get(screen);
            return value instanceof StartupQuery ? (StartupQuery) value : null;
        } catch (ReflectiveOperationException | RuntimeException unreadable) {
            return null;
        }
    }

    /** The old save's question as its screen words it, with the save's name where it can be read. */
    private static String oldSaveText(GuiScreen screen) {
        String name = "";
        try {
            Field field = GuiOldSaveLoadConfirm.class.getDeclaredField("saveName");
            field.setAccessible(true);
            Object value = field.get(screen);
            name = value == null ? "" : value.toString();
        } catch (ReflectiveOperationException | RuntimeException unreadable) {
            name = "";
        }
        return "The world " + name + " contains pre-update modding data";
    }
}
