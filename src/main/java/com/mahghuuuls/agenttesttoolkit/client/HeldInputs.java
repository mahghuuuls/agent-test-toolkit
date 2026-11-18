package com.mahghuuuls.agenttesttoolkit.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every input the toolkit keeps down beyond the moment it is asked, and its release: key bindings
 * held (a player action's hold and a key's hold), one per key code; key presses and clicks, each
 * let go and recorded on the tick after, once the game has handled them; presses, clicks and
 * holds through the mouse or keyboard queue, whose life is {@link PressStages}'; and one turn of
 * the view spread over ticks.
 * It also keeps whether {@code client focus lost} has taken the game's focus, which counts as a
 * loss of focus for every hold and turn until the game takes focus again.
 *
 * <p>Nothing stays pressed: each ends with one record, by {@link HoldRelease}'s reasons, and every
 * one ends when the player leaves the world. Client thread only.
 */
final class HeldInputs {

    /** Where the records of what ends go, and the screenshot a step asked for. */
    interface Records {
        void finish(Minecraft mc, Asked asked, String result, String reason, String screen, String showing,
                    Map<String, String> fields);

        void screenshotAfter(Asked asked, Map<String, String> fields);
    }

    static final String ROUTE_BINDING = "binding";
    static final String ROUTE_EVENT = "event";
    static final String ROUTE_QUEUE = "queue";
    /** The game counted the press on another binding on the same key, or on none. */
    static final String REASON_NOT_COUNTED = "notCounted";

    private final Records records;
    /** Running holds by key code: one key code is one key, whatever the bindings on it. */
    private final Map<Integer, Hold> holds = new LinkedHashMap<Integer, Hold>();
    private final List<Press> presses = new ArrayList<Press>();
    /** Queued holds that have ended, whose key-up is still to go into the queue or be let go. */
    private final List<Hold> releasing = new ArrayList<Hold>();
    private Turn turn;
    /** {@code client focus lost} took the game's focus, and the game has not taken it back yet. */
    private boolean focusLost;

    /** What a press and a hold share: the binding, its key, and where it is in its life. */
    private abstract static class Input {
        final Asked asked;
        final KeyBinding binding;
        final int keyCode;
        final boolean raw;
        /** Through an input queue: the game presses and lets go the key itself, from its events. */
        final boolean queued;
        /** Through the keyboard queue rather than the mouse queue: the binding is on a key. */
        final boolean keyboard;
        final Map<String, String> fields;
        PressStages.Stage stage;

        Input(Asked asked, KeyBinding binding, boolean raw, boolean queued, Map<String, String> fields) {
            this.asked = asked;
            this.binding = binding;
            this.keyCode = binding.getKeyCode();
            this.raw = raw;
            this.queued = queued;
            this.keyboard = queued && MouseQueue.button(keyCode) < 0;
            this.fields = fields;
            this.stage = queued ? PressStages.Stage.QUEUE_DUE : PressStages.Stage.DOWN;
        }

        /** A {@code key} step's: the game's lookup is asked, before its key-down is queued, which binding it counts. */
        abstract boolean keyStep();
    }

    private static final class Hold extends Input {
        final int askedTicks;
        final boolean focusedAtStart;
        /** A key step's record names the tick it was let go. */
        final boolean keyStep;
        int heldTicks;

        Hold(Asked asked, KeyBinding binding, int askedTicks, boolean raw, boolean focusedAtStart, boolean keyStep,
             boolean queued, Map<String, String> fields) {
            super(asked, binding, raw, queued, fields);
            this.askedTicks = askedTicks;
            this.focusedAtStart = focusedAtStart;
            this.keyStep = keyStep;
        }

        @Override
        boolean keyStep() {
            return keyStep;
        }
    }

    private static final class Press extends Input {
        final String route;
        final boolean keyStep;
        /** A key press sets the binding down for a tick; a click only counts the press, as a player action's does. */
        final boolean setsState;
        /** Made while a hand was active, on a binding the game then drains: its record says whether it was taken. */
        final boolean watched;
        /** The binding the game counted the press on instead of this one; null when this one. */
        String countedOn;
        int expected = -1;
        boolean handActive;
        boolean takenByEvent;
        int beforeEvent = -1;

        Press(Asked asked, KeyBinding binding, String route, boolean keyStep, boolean raw, boolean watched,
              Map<String, String> fields) {
            super(asked, binding, raw, ROUTE_QUEUE.equals(route), fields);
            this.route = route;
            this.keyStep = keyStep;
            this.setsState = PressStages.setsState(keyStep, queued);
            this.watched = watched;
        }

        @Override
        boolean keyStep() {
            return keyStep;
        }
    }

    private static final class Turn {
        final Asked asked;
        final double yaw;
        final double pitch;
        final int ticks;
        final boolean focusedAtStart;
        final Map<String, String> fields;
        int done;

        Turn(Asked asked, double yaw, double pitch, int ticks, boolean focusedAtStart, Map<String, String> fields) {
            this.asked = asked;
            this.yaw = yaw;
            this.pitch = pitch;
            this.ticks = ticks;
            this.focusedAtStart = focusedAtStart;
            this.fields = fields;
        }
    }

    HeldInputs(Records records) {
        this.records = records;
    }

    /** The key codes held now, a queued hold whose key-down is still due included. */
    Set<Integer> heldKeyCodes() {
        return Collections.unmodifiableSet(new LinkedHashSet<Integer>(holds.keySet()));
    }

    /** Whether the game has focus, as holds, turns and queued clicks see it. */
    boolean focused() {
        return Display.isActive() && !focusLost;
    }

    /**
     * Holds a binding down: the key goes down and one press is counted, as a key or button going
     * down does. Its release writes its record, with {@code fields} and the ticks and reason.
     *
     * @return the binding the game counted the press on when not this one (see
     *         {@link KeyLookup#countedElsewhere}); null when this one
     */
    String hold(Minecraft mc, Asked asked, KeyBinding binding, int ticks, boolean raw, boolean keyStep,
                Map<String, String> fields) {
        Hold hold = new Hold(asked, binding, ticks, raw, focused(), keyStep, false, fields);
        takeKey(hold.keyCode);
        holds.put(hold.keyCode, hold);
        KeyBinding.setKeyBindState(hold.keyCode, true);
        String countedOn = KeyBindings.countPress(mc.gameSettings.keyBindings, binding);
        if (raw) {
            rawDown(hold.keyCode, true);
        }
        return countedOn;
    }

    /**
     * A hold or key press that keeps the binding down itself now owns the key: the key-up of an
     * ended queued hold or queued press of that key, not yet queued, is dropped, since in the tick
     * the game read it the binding would be let go under the new input.
     */
    private void takeKey(int keyCode) {
        List<Input> ended = new ArrayList<Input>(releasing);
        for (Press press : presses) {
            if (press.queued) {
                ended.add(press);
            }
        }
        for (Input input : ended) {
            if (input.keyCode != keyCode) {
                continue;
            }
            PressStages.Act act = PressStages.onKeyTaken(input.stage);
            input.stage = PressStages.after(input.stage, act, true);
            if (input.stage == PressStages.Stage.OVER) {
                if (input instanceof Press) {
                    presses.remove(input);
                } else {
                    releasing.remove(input);
                }
            }
        }
    }

    /**
     * Holds a binding's key or button through its input queue: the key-down goes into the queue
     * at the next tick start and the hold's {@code started} record is written then, or the hold is
     * refused then with why; the key-up goes into the queue at the tick start after the hold ends,
     * or the binding is let go directly when nothing may be queued. The binding is set down again
     * every tick, as any hold is, since the game sets every key from the real keyboard when it
     * regains focus.
     */
    void queueHold(Asked asked, KeyBinding binding, int ticks, boolean raw, Map<String, String> fields) {
        Hold hold = new Hold(asked, binding, ticks, raw, focused(), true, true, fields);
        holds.put(hold.keyCode, hold);
    }

    /**
     * One press, by a route: {@code binding} counts it on the binding, {@code event} also posts
     * Forge's mouse input event right after, so listeners hear it before the game's next key
     * handling, and {@code queue} puts a real button or key event in the mouse or keyboard queue
     * at the next tick start. Its record is written at the end of the tick the game handles it in.
     *
     * @param keyStep a {@code key} press rather than a click
     */
    void press(Minecraft mc, Asked asked, KeyBinding binding, String route, boolean keyStep, boolean raw,
               Map<String, String> fields) {
        boolean watched = mc.player != null && mc.player.isHandActive() && drainedWhileHandActive(mc, binding);
        Press press = new Press(asked, binding, route, keyStep, raw, watched, fields);
        if (PressStages.takesKey(false, press.queued, press.setsState)) {
            takeKey(press.keyCode);
        }
        presses.add(press);
        if (press.queued) {
            return;
        }
        putTick(fields, "pressedTick", mc);
        if (press.setsState) {
            KeyBinding.setKeyBindState(press.keyCode, true);
        }
        if (raw) {
            rawDown(press.keyCode, true);
        }
        press.countedOn = KeyBindings.countPress(mc.gameSettings.keyBindings, binding);
        press.expected = KeyBindings.pressCount(binding);
        if (ROUTE_EVENT.equals(route)) {
            // Without fields of its own: a listener that reads the mouse sees the last real event.
            MinecraftForge.EVENT_BUS.post(new InputEvent.MouseInputEvent());
        }
    }

    /** The bindings whose presses the game empties unused while a hand is active. */
    private static boolean drainedWhileHandActive(Minecraft mc, KeyBinding binding) {
        GameSettings settings = mc.gameSettings;
        return binding == settings.keyBindAttack || binding == settings.keyBindUseItem
                || binding == settings.keyBindPickBlock;
    }

    /** A turn of the view over ticks; its steps are taken at each tick start. */
    void turn(Asked asked, double yaw, double pitch, int ticks, Map<String, String> fields) {
        turn = new Turn(asked, yaw, pitch, ticks, focused(), fields);
    }

    /**
     * Another action is taken: every hold and the turn end as the next action, unless the
     * player left, died, a screen opened or focus was lost first.
     */
    void endForAction(Minecraft mc) {
        for (Hold hold : new ArrayList<Hold>(holds.values())) {
            advanceHold(mc, hold, true, false);
        }
        if (turn != null) {
            advanceTurn(mc, true, false);
        }
    }

    /** Ends the hold of one key as released; false when none was running. */
    boolean release(Minecraft mc, int keyCode) {
        Hold hold = holds.get(keyCode);
        if (hold == null) {
            return false;
        }
        release(mc, hold, HoldRelease.RELEASED_RELEASED);
        return true;
    }

    /**
     * {@code client focus lost}: the game counts as without focus until it takes focus again;
     * every hold and the turn end as a loss of focus, and every press, queued click and queued
     * key-up is let go or cancelled now.
     */
    void loseFocus(Minecraft mc) {
        focusLost = true;
        for (Hold hold : new ArrayList<Hold>(holds.values())) {
            release(mc, hold, HoldRelease.RELEASED_FOCUS_LOST);
        }
        if (turn != null) {
            endTurn(mc, HoldRelease.RELEASED_FOCUS_LOST);
        }
        List<Input> pending = new ArrayList<Input>(presses);
        pending.addAll(releasing);
        for (Input input : pending) {
            PressStages.Act act;
            while ((act = PressStages.onFocusLost(input.stage)) != PressStages.Act.NONE) {
                perform(mc, input, act, PlayerActionGate.REASON_NO_FOCUS);
            }
        }
    }

    /** {@code client focus gained}: the game has its focus again. */
    void gainFocus() {
        focusLost = false;
    }

    /**
     * The start of a client tick, before the game reads its input: the turn's next step, the
     * queued keys' and buttons' events, every key-up before any key-down, the raw keys written
     * again, and the hand's state for the presses the game handles in this tick.
     */
    void tickStart(Minecraft mc) {
        boolean worldOpen = mc.world != null && mc.player != null;
        if (focusLost && (!worldOpen || mc.inGameHasFocus)) {
            // The game took its focus back: a click into the window, or the pause menu closed.
            focusLost = false;
        }
        if (turn != null) {
            advanceTurn(mc, false, true);
        }
        String blocked = PressStages.blocked(worldOpen, mc.currentScreen != null, focusLost);
        List<Input> due = new ArrayList<Input>(presses);
        due.addAll(releasing);
        for (Hold hold : holds.values()) {
            if (hold.stage == PressStages.Stage.QUEUE_DUE) {
                due.add(hold);
            }
        }
        List<PressStages.Stage> stages = new ArrayList<PressStages.Stage>(due.size());
        for (Input input : due) {
            stages.add(input.stage);
        }
        for (int at : PressStages.startOrder(stages)) {
            Input input = due.get(at);
            PressStages.Act act = PressStages.atStart(input.stage, blocked);
            if (act != PressStages.Act.NONE) {
                perform(mc, input, act, blocked);
            } else if (input instanceof Press && input.stage == PressStages.Stage.DOWN && worldOpen) {
                ((Press) input).handActive = mc.player.isHandActive();
            }
        }
        reassertRaw();
    }

    /** The start of a frame's drawing: the raw keys written again, since the library refills them each frame. */
    void renderStart() {
        reassertRaw();
    }

    /**
     * The end of a client tick: presses let go and recorded, holds pressed again or released.
     * Pressed again every tick, since the game sets every key from the real keyboard and mouse
     * when the window gains focus.
     */
    void tickEnd(Minecraft mc) {
        for (Press press : new ArrayList<Press>(presses)) {
            PressStages.Act act = PressStages.atEnd(press.stage);
            if (act != PressStages.Act.NONE) {
                perform(mc, press, act, null);
            }
        }
        for (Hold hold : new ArrayList<Hold>(holds.values())) {
            advanceHold(mc, hold, false, true);
        }
    }

    /** Before any listener of an input event: the press counts it may take. */
    void beforeInputEvent() {
        for (Press press : presses) {
            if (press.stage == PressStages.Stage.DOWN && press.watched) {
                press.beforeEvent = KeyBindings.pressCount(press.binding);
            }
        }
    }

    /** After every listener: a count that fell was taken by one of them. */
    void afterInputEvent() {
        for (Press press : presses) {
            if (press.stage == PressStages.Stage.DOWN && press.watched && press.beforeEvent >= 0
                    && KeyBindings.pressCount(press.binding) < press.beforeEvent) {
                press.takenByEvent = true;
            }
        }
    }

    /** A mod cancelled the mouse event of a queued click: it took the click as its own input. */
    void mouseEvent(MouseEvent event) {
        if (!event.isCanceled() || !event.isButtonstate()) {
            return;
        }
        for (Press press : presses) {
            if (press.stage == PressStages.Stage.DOWN && press.queued && !press.keyboard
                    && MouseQueue.button(press.keyCode) == event.getButton()) {
                press.takenByEvent = true;
            }
        }
    }

    /**
     * Does one act of a press's or a queued hold's life and moves it on. A key-down the game
     * would count on another binding is not queued for a press (see {@link PressStages#beforeAdd}),
     * and an event that cannot go into its queue turns the act into its fallback: a key-down
     * never queued is refused, a key-up never queued lets the binding go directly.
     */
    private void perform(Minecraft mc, Input input, PressStages.Act act, String reason) {
        PressStages.Act done = act;
        String why = reason;
        String countedOn = null;
        if (act == PressStages.Act.ADD_PRESS) {
            countedOn = input.keyStep() ? KeyBindings.countedOnBeforePress(input.binding) : null;
            done = PressStages.beforeAdd(act, countedOn, input instanceof Hold);
            if (done == PressStages.Act.ADD_PRESS && !addDown(mc, input)) {
                done = PressStages.Act.CANCEL;
                why = RouteState.REASON_ROUTE_UNAVAILABLE;
            }
        } else if (act == PressStages.Act.ADD_RELEASE && !queueInput(input, false)) {
            done = PressStages.Act.UNPRESS;
        }
        if (done == PressStages.Act.CANCEL) {
            cancel(mc, input, why);
        } else if (done == PressStages.Act.NOT_COUNTED) {
            notCounted(mc, (Press) input, countedOn);
        } else if (done == PressStages.Act.ADD_PRESS && input instanceof Hold) {
            started(mc, (Hold) input, countedOn);
        } else if (done == PressStages.Act.LET_GO) {
            letGo(mc, (Press) input);
        } else if (done == PressStages.Act.UNPRESS
                || (done == PressStages.Act.UNPRESS_IF_UP && !realDown(input))) {
            unpress(input.keyCode);
        }
        input.stage = PressStages.after(input.stage, done, input.queued);
        if (input.stage == PressStages.Stage.OVER) {
            if (input instanceof Press) {
                presses.remove(input);
            } else {
                releasing.remove(input);
                if (holds.get(input.keyCode) == input) {
                    holds.remove(input.keyCode);
                }
            }
        }
    }

    /** The key-down into its queue; false when that queue cannot be used. */
    private boolean addDown(Minecraft mc, Input input) {
        if (!(input instanceof Press)) {
            if (!queueInput(input, true)) {
                return false;
            }
            putTick(input.fields, "pressedTick", mc);
            return true;
        }
        Press press = (Press) input;
        int before = KeyBindings.pressCount(press.binding);
        press.handActive = mc.player.isHandActive();
        if (!queueInput(press, true)) {
            return false;
        }
        // The game counts the press as it reads the event, unless a mod cancels its mouse event.
        press.expected = before < 0 ? -1 : before + 1;
        putTick(press.fields, "pressedTick", mc);
        return true;
    }

    /** A queued hold's key went into the queue: its {@code started} record, naming where the game counts the press when elsewhere. */
    private void started(Minecraft mc, Hold hold, String countedOn) {
        if (hold.raw) {
            rawDown(hold.keyCode, true);
        }
        Map<String, String> fields = new LinkedHashMap<String, String>(hold.fields);
        if (countedOn != null) {
            // Held all the same, as a real key is; its press went to another binding on the key.
            fields.put("countedOn", countedOn);
        }
        records.finish(mc, hold.asked, "started", null, PlayerInput.screenName(mc.currentScreen), null, fields);
    }

    /** The button or key of a queued input into its queue; false when that queue cannot be used. */
    private static boolean queueInput(final Input input, final boolean down) {
        if (input.keyboard) {
            return KeyQueue.CHOICE.add(queue -> queue.add(input.keyCode, down));
        }
        return MouseQueue.CHOICE.add(queue -> queue.add(MouseQueue.button(input.keyCode), down));
    }

    /** Whether the real button or key is down, so letting the binding go would undo a person's input. */
    private static boolean realDown(Input input) {
        try {
            return input.keyboard ? Keyboard.isKeyDown(input.keyCode)
                    : Mouse.isButtonDown(MouseQueue.button(input.keyCode));
        } catch (RuntimeException noDevice) {
            return false;
        }
    }

    /** Lets a binding go, unless a hold or a key press that sets the binding itself keeps its key down. */
    private void unpress(int keyCode) {
        if (holds.containsKey(keyCode)) {
            return;
        }
        for (Press press : presses) {
            if (press.keyCode == keyCode && press.setsState && press.stage == PressStages.Stage.DOWN) {
                return;
            }
        }
        KeyBinding.setKeyBindState(keyCode, false);
    }

    /**
     * A queued click, key press or key hold whose key-down never went into the queue: refused
     * with why, and nothing was pressed. A hold's why is the reason it ended, when it ended first.
     */
    private void cancel(Minecraft mc, Input input, String reason) {
        Map<String, String> fields = new LinkedHashMap<String, String>();
        if (input.keyStep()) {
            // A key step's refusal names its binding, as its other records do.
            fields.putAll(input.fields);
        }
        Map<String, String> refused = new LinkedHashMap<String, String>();
        if (RouteState.REASON_ROUTE_UNAVAILABLE.equals(reason)) {
            String failure = (input.keyboard ? KeyQueue.CHOICE.state() : MouseQueue.CHOICE.state())
                    .takeFailureName();
            if (failure != null) {
                refused.put("detail", failure);
            }
        }
        refused.putAll(fields);
        if (!refused.containsKey("route")) {
            refused.put("route", ROUTE_QUEUE);
        }
        records.finish(mc, input.asked, "refused", reason, PlayerInput.screenName(mc.currentScreen), null, refused);
    }

    /** A queued key press the game would have counted on another binding, or on none: never queued, and failed. */
    private void notCounted(Minecraft mc, Press press, String countedOn) {
        Map<String, String> fields = new LinkedHashMap<String, String>(press.fields);
        fields.put("countedOn", countedOn);
        records.finish(mc, press.asked, "failed", REASON_NOT_COUNTED, PlayerInput.screenName(mc.currentScreen),
                null, fields);
    }

    /**
     * A press's tick is over: the key goes up, unless a hold keeps it, and its record is written.
     * A press the game counted on another binding is no press of the one named: it failed.
     */
    private void letGo(Minecraft mc, Press press) {
        Hold hold = holds.get(press.keyCode);
        if (press.setsState && hold == null) {
            KeyBinding.setKeyBindState(press.keyCode, false);
        }
        if (press.raw && (hold == null || !hold.raw)) {
            rawDown(press.keyCode, false);
        }
        Map<String, String> fields = new LinkedHashMap<String, String>(press.fields);
        putTick(fields, "releasedTick", mc);
        if (press.watched) {
            int after = KeyBindings.pressCount(press.binding);
            fields.put("taken", PressTaken.taken(press.expected >= 0 && after >= 0, press.takenByEvent,
                    press.expected, after, press.handActive));
        }
        String screen = PlayerInput.screenName(mc.currentScreen);
        if (press.countedOn != null) {
            fields.put("countedOn", press.countedOn);
            records.finish(mc, press.asked, "failed", REASON_NOT_COUNTED, screen, null, fields);
        } else {
            records.finish(mc, press.asked, "done", null, screen, null, fields);
        }
    }

    /** Keeps a hold down for one more tick, or releases it with its reason. A tick end counts the tick just run. */
    private void advanceHold(Minecraft mc, Hold hold, boolean nextAction, boolean tickEnd) {
        if (tickEnd && !PressStages.holdRuns(hold.stage)) {
            // A queued hold's key goes down at a tick start; nothing of it has run yet.
            return;
        }
        if (tickEnd) {
            hold.heldTicks++;
        }
        boolean worldOpen = mc.world != null && mc.player != null;
        String released = HoldRelease.reason(worldOpen, worldOpen && !died(mc), mc.currentScreen != null,
                hold.focusedAtStart, focused(), nextAction, hold.heldTicks, hold.askedTicks);
        if (released == null) {
            if (PressStages.holdRuns(hold.stage)) {
                KeyBinding.setKeyBindState(hold.keyCode, true);
            }
            return;
        }
        release(mc, hold, released);
    }

    private void release(Minecraft mc, Hold hold, String released) {
        if (hold.queued) {
            PressStages.Act act = PressStages.onHoldEnd(hold.stage);
            if (act == PressStages.Act.CANCEL) {
                // Ended before its key-down went in: refused with the reason it ended, nothing pressed.
                perform(mc, hold, act, released);
                return;
            }
            holds.remove(hold.keyCode);
            // The key-up goes into the queue at the next tick start, so the game lets the key go
            // itself and posts its key event; the binding stays down until then.
            hold.stage = PressStages.after(hold.stage, act, true);
            releasing.add(hold);
        } else {
            holds.remove(hold.keyCode);
            KeyBinding.setKeyBindState(hold.keyCode, false);
        }
        if (hold.raw) {
            rawDown(hold.keyCode, false);
        }
        if (hold.heldTicks == 0 && !hold.queued) {
            // Released before the game ran a tick with it: the press counted at the start would
            // still act once on the next tick, so it is taken back.
            while (hold.binding.isPressed()) {
                // Each call takes back one counted press.
            }
        }
        Map<String, String> fields = new LinkedHashMap<String, String>(hold.fields);
        fields.put("heldTicks", String.valueOf(hold.heldTicks));
        fields.put("released", released);
        if (hold.keyStep) {
            putTick(fields, "releasedTick", mc);
        }
        records.finish(mc, hold.asked, "done", null, PlayerInput.screenName(mc.currentScreen), null, fields);
    }

    /**
     * Takes the turn's next step at a tick start, or ends it with its reason. A step is the
     * mouse's own turn, so the game clamps the pitch, moves a ridden entity, and sends the
     * rotation itself.
     */
    private void advanceTurn(Minecraft mc, boolean nextAction, boolean tickStart) {
        Turn t = turn;
        boolean worldOpen = mc.world != null && mc.player != null;
        String reason = HoldRelease.reason(worldOpen, worldOpen && !died(mc), mc.currentScreen != null,
                t.focusedAtStart, focused(), nextAction, t.done, t.ticks);
        if (reason == null && tickStart) {
            t.done++;
            mc.player.turn(Aim.mouseYaw(Aim.step(t.yaw, t.ticks, t.done)),
                    Aim.mousePitch(Aim.step(t.pitch, t.ticks, t.done)));
            if (t.done >= t.ticks) {
                reason = HoldRelease.RELEASED_DONE;
            }
        }
        if (reason != null) {
            endTurn(mc, reason);
        }
    }

    private void endTurn(Minecraft mc, String reason) {
        Turn t = turn;
        turn = null;
        Map<String, String> fields = new LinkedHashMap<String, String>(t.fields);
        if (mc.player != null) {
            fields.put("endYaw", angle(mc.player.rotationYaw));
            fields.put("endPitch", angle(mc.player.rotationPitch));
        }
        fields.put("turnTicks", String.valueOf(t.done));
        fields.put("released", reason);
        records.finish(mc, t.asked, "done", null, PlayerInput.screenName(mc.currentScreen), null, fields);
    }

    private void reassertRaw() {
        for (Hold hold : holds.values()) {
            if (hold.raw && hold.stage == PressStages.Stage.DOWN) {
                rawDown(hold.keyCode, true);
            }
        }
        for (Press press : presses) {
            if (press.raw && press.stage == PressStages.Stage.DOWN) {
                rawDown(press.keyCode, true);
            }
        }
    }

    /**
     * Writes the keyboard library's own state of a key. A key goes down only while the backend
     * works; it is let go through the backend that set it even after that backend failed, so no
     * key stays down.
     */
    private static void rawDown(int keyCode, boolean down) {
        RawKeys keys = down ? RawKeys.Choice.get() : RawKeys.Choice.resolved();
        if (keys == null) {
            return;
        }
        try {
            keys.setDown(keyCode, down);
        } catch (RuntimeException failure) {
            RawKeys.Choice.failed(failure);
        }
    }

    /**
     * Whether the player has died. The death screen can arrive a tick before the health: the
     * server sends the death, which opens it, before the health update that brings the health
     * to 0. A death screen another mod puts in its place is not recognised here.
     */
    static boolean died(Minecraft mc) {
        return mc.player.isDead || mc.player.getHealth() <= 0.0F || mc.currentScreen instanceof GuiGameOver;
    }

    /**
     * The client world's tick, for the moment a key went down or up; left out without a world,
     * since a report carries no empty value.
     */
    static void putTick(Map<String, String> fields, String name, Minecraft mc) {
        if (mc.world != null) {
            fields.put(name, String.valueOf(mc.world.getTotalWorldTime()));
        }
    }

    /** An angle in degrees, two decimals, whatever the machine's language. */
    static String angle(double degrees) {
        return String.format(java.util.Locale.ROOT, "%.2f", degrees);
    }
}
