package com.mahghuuuls.agenttesttoolkit.client.menu;

import java.util.List;

/**
 * What one {@code open} step does with each question its world asks as it opens: whether to
 * report it, how to answer it, the answer as recorded, and when the watch ends.
 *
 * <p>Pure: no game type, so the order of questions in one opening (an old save's question, then
 * one of Forge's, a notice and then a question) is unit-tested without a game; {@link QuestionWatch}
 * reads the screens and carries the decisions out. Armed with the step's {@code onQuestion} and
 * disarmed when the step ends: a report or a no ends it; a yes and a notice's one button leave it
 * armed, so the next question of the same opening is answered too.
 */
final class QuestionAnswers {

    /** The answer recorded for a notice, which has only one button. */
    static final String ACKNOWLEDGED = "acknowledged";

    /** How a question is met. */
    enum Act {
        /** The step ends now, the screen staying for a person. */
        REPORT,
        /** The old save's question: its opening is cancelled and its button's action taken at the next tick. */
        ANSWER_NEXT_TICK,
        /** One of Forge's startup questions: a stand-in screen gives the answer when it is first drawn. */
        STAND_IN
    }

    /** The decision for one question. */
    static final class Decision {
        final Act act;
        final String kind;
        /** Whether the answer is yes; for a notice, which has one button, it does not matter. */
        final boolean yes;
        /** Whether the screen has a yes and a no; false for a notice. */
        final boolean twoButtons;
        /** The answer as the record names it; null for a report. */
        final String answer;

        Decision(Act act, String kind, boolean yes, boolean twoButtons, String answer) {
            this.act = act;
            this.kind = kind;
            this.yes = yes;
            this.twoButtons = twoButtons;
            this.answer = answer;
        }

        /** Whether giving this answer ends the step: a no to a question with a yes and a no. */
        boolean ends() {
            return act != Act.REPORT && twoButtons && !yes;
        }
    }

    /** The step's {@code onQuestion}; null while disarmed. */
    private String onQuestion;

    void arm(String onQuestion) {
        this.onQuestion = onQuestion;
    }

    void disarm() {
        onQuestion = null;
    }

    boolean isArmed() {
        return onQuestion != null;
    }

    /**
     * A screen opening. Null when the watch is disarmed or the screen is no question a world asks
     * as it opens. Under {@code report}, and for a startup question whose query cannot be read
     * (so it cannot be answered), the step reports it and the watch disarms.
     *
     * @param hierarchy the screen's class and superclasses
     * @param text      the question's text; empty or null when the screen has none
     * @param readable  whether a startup question's query could be read; ignored for the old save's
     */
    Decision opened(List<String> hierarchy, String text, boolean readable) {
        if (onQuestion == null || !ConfirmScreens.isQuestion(hierarchy)) {
            return null;
        }
        String kind = ConfirmScreens.questionKind(hierarchy, text);
        boolean startup = ConfirmScreens.isStartupQuestion(hierarchy);
        boolean twoButtons = !ConfirmScreens.KIND_NOTICE.equals(kind);
        if (MenuRequest.REPORT.equals(onQuestion) || (startup && !readable)) {
            onQuestion = null;
            return new Decision(Act.REPORT, kind, false, twoButtons, null);
        }
        boolean yes = MenuRequest.CONFIRM.equals(onQuestion);
        String answer = !twoButtons ? ACKNOWLEDGED : yes ? MenuRequest.CONFIRM : MenuRequest.CANCEL;
        return new Decision(startup ? Act.STAND_IN : Act.ANSWER_NEXT_TICK, kind, yes, twoButtons, answer);
    }

    /**
     * The answer is being given: a no ends the step, so the watch disarms first, before the
     * answer lets the world's start go on. Returns whether the step ends with it.
     */
    boolean answering(Decision decision) {
        if (decision.ends()) {
            onQuestion = null;
        }
        return decision.ends();
    }
}
