package com.mahghuuuls.agenttesttoolkit.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.gameevent.InputEvent;
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
 * let go and recorded on the tick after, once the game has handled them; clicks through the
 * mouse queue (their life is {@link PressStages}'); and one turn of the view spread over ticks.
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
    /** Held bindings by key code: one key code is one key, whatever the bindings on it. */
    private final Map<Integer, Hold> holds = new LinkedHashMap<Integer, Hold>();
    private final List<Press> presses = new ArrayList<Press>();
    private Turn turn;
    /** {@code client focus lost} took the game's focus, and the game has not taken it back yet. */
    private boolean focusLost;

    private static final class Hold {
        final Asked asked;
        final KeyBinding binding;
        final int keyCode;
        final int askedTicks;
        final boolean raw;
        final boolean focusedAtStart;
        /** A key step's record names the tick it was let go. */
        final boolean keyStep;
        /** What the release's record carries besides its ticks and reason. */
        final Map<String, String> fields;
        int heldTicks;

        Hold(Asked asked, KeyBinding binding, int askedTicks, boolean raw, boolean focusedAtStart, boolean keyStep,
             Map<String, String> fields) {
            this.asked = asked;
            this.binding = binding;
            this.keyCode = binding.getKeyCode();
            this.askedTicks = askedTicks;
            this.raw = raw;
            this.focusedAtStart = focusedAtStart;
            this.keyStep = keyStep;
            this.fields = fields;
        }
    }

    private static final class Press {
        final Asked asked;
        final KeyBinding binding;
        final int keyCode;
        final String route;
        final boolean queued;
        /** A key press sets the binding down for a tick; a click only counts the press, as a player action's does. */
        final boolean setsState;
        final boolean raw;
        /** Made while a hand was active, on a binding the game then drains: its record says whether it was taken. */
        final boolean watched;
        final Map<String, String> fields;
        PressStages.Stage stage;
        /** The binding the game counted the press on instead of this one; null when this one. */
        String countedOn;
        int expected = -1;
        boolean handActive;
        boolean takenByEvent;
        int beforeEvent = -1;

        Press(Asked asked, KeyBinding binding, String route, boolean setsState, boolean raw, boolean watched,
              Map<String, String> fields) {
            this.asked = asked;
            this.binding = binding;
            this.keyCode = binding.getKeyCode();
            this.route = route;
            this.queued = ROUTE_QUEUE.equals(route);
            this.setsState = setsState;
            this.raw = raw;
            this.watched = watched;
            this.fields = fields;
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

    /** The key codes held now. */
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
        Hold hold = new Hold(asked, binding, ticks, raw, focused(), keyStep, fields);
        holds.put(hold.keyCode, hold);
        KeyBinding.setKeyBindState(hold.keyCode, true);
        String countedOn = KeyBindings.countPress(mc.gameSettings.keyBindings, binding);
        if (raw) {
            rawDown(hold.keyCode, true);
        }
        return countedOn;
    }

    /**
     * One press, by a route: {@code binding} counts it on the binding, {@code event} also posts
     * Forge's mouse input event right after, so listeners hear it before the game's next key
     * handling, and {@code queue} puts a real button event in the mouse queue at the next tick
     * start. Its record is written at the end of the tick the game handles it in.
     */
    void press(Minecraft mc, Asked asked, KeyBinding binding, String route, boolean setsState, boolean raw,
               Map<String, String> fields) {
        boolean watched = mc.player != null && mc.player.isHandActive() && drainedWhileHandActive(mc, binding);
        Press press = new Press(asked, binding, route, setsState, raw, watched, fields);
        presses.add(press);
        if (press.queued) {
            press.stage = PressStages.Stage.QUEUE_DUE;
            return;
        }
        press.stage = PressStages.Stage.DOWN;
        putTick(fields, "pressedTick", mc);
        if (setsState) {
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
     * every hold and the turn end as a loss of focus, and every press and queued click is let go
     * or cancelled now.
     */
    void loseFocus(Minecraft mc) {
        focusLost = true;
        for (Hold hold : new ArrayList<Hold>(holds.values())) {
            release(mc, hold, HoldRelease.RELEASED_FOCUS_LOST);
        }
        if (turn != null) {
            endTurn(mc, HoldRelease.RELEASED_FOCUS_LOST);
        }
        for (Press press : new ArrayList<Press>(presses)) {
            PressStages.Act act;
            while ((act = PressStages.onFocusLost(press.stage)) != PressStages.Act.NONE) {
                perform(mc, press, act, PlayerActionGate.REASON_NO_FOCUS);
            }
        }
    }

    /** {@code client focus gained}: the game has its focus again. */
    void gainFocus() {
        focusLost = false;
    }

    /**
     * The start of a client tick, before the game reads its input: the turn's next step, the
     * queued clicks' buttons, the raw keys written again, and the hand's state for the presses
     * the game handles in this tick.
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
        for (Press press : new ArrayList<Press>(presses)) {
            PressStages.Act act = PressStages.atStart(press.stage, blocked);
            if (act != PressStages.Act.NONE) {
                perform(mc, press, act, blocked);
            } else if (press.stage == PressStages.Stage.DOWN && worldOpen) {
                press.handActive = mc.player.isHandActive();
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
            if (press.stage == PressStages.Stage.DOWN && press.queued
                    && MouseQueue.button(press.keyCode) == event.getButton()) {
                press.takenByEvent = true;
            }
        }
    }

    /**
     * Does one act of a press's life and moves it on. A button that cannot go into the queue
     * turns the act into its fallback: a press never queued is cancelled, a release never queued
     * lets the binding go directly.
     */
    private void perform(Minecraft mc, Press press, PressStages.Act act, String reason) {
        PressStages.Act done = act;
        String why = reason;
        if (act == PressStages.Act.ADD_PRESS && !queuePress(mc, press)) {
            done = PressStages.Act.CANCEL;
            why = RouteState.REASON_ROUTE_UNAVAILABLE;
        } else if (act == PressStages.Act.ADD_RELEASE && !queueButton(press, false)) {
            done = PressStages.Act.UNPRESS;
        }
        if (done == PressStages.Act.CANCEL) {
            cancel(mc, press, why);
        } else if (done == PressStages.Act.LET_GO) {
            letGo(mc, press);
        } else if (done == PressStages.Act.UNPRESS
                || (done == PressStages.Act.UNPRESS_IF_UP && !buttonDown(press))) {
            unpress(press.keyCode);
        }
        press.stage = PressStages.after(press.stage, done, press.queued);
        if (press.stage == PressStages.Stage.OVER) {
            presses.remove(press);
        }
    }

    private boolean queuePress(Minecraft mc, Press press) {
        int before = KeyBindings.pressCount(press.binding);
        press.handActive = mc.player.isHandActive();
        if (!queueButton(press, true)) {
            return false;
        }
        // The game counts the press as it reads the event, unless a mod cancels its mouse event.
        press.expected = before < 0 ? -1 : before + 1;
        putTick(press.fields, "pressedTick", mc);
        return true;
    }

    private static boolean queueButton(Press press, boolean down) {
        MouseQueue queue = MouseQueue.Choice.get();
        if (queue == null) {
            return false;
        }
        try {
            queue.add(MouseQueue.button(press.keyCode), down);
            return true;
        } catch (RuntimeException failure) {
            MouseQueue.Choice.failed(failure);
            return false;
        }
    }

    private static boolean buttonDown(Press press) {
        try {
            return Mouse.isButtonDown(MouseQueue.button(press.keyCode));
        } catch (RuntimeException noMouse) {
            return false;
        }
    }

    /** Lets a binding go, unless a hold keeps its key down. */
    private void unpress(int keyCode) {
        if (!holds.containsKey(keyCode)) {
            KeyBinding.setKeyBindState(keyCode, false);
        }
    }

    /** A queued click whose button never went down: refused with why, and nothing was clicked. */
    private void cancel(Minecraft mc, Press press, String reason) {
        Map<String, String> fields = new LinkedHashMap<String, String>();
        if (RouteState.REASON_ROUTE_UNAVAILABLE.equals(reason)) {
            String failure = MouseQueue.Choice.state().takeFailureName();
            if (failure != null) {
                fields.put("detail", failure);
            }
        }
        fields.put("route", press.route);
        records.finish(mc, press.asked, "refused", reason, PlayerInput.screenName(mc.currentScreen), null, fields);
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
        if (tickEnd) {
            hold.heldTicks++;
        }
        boolean worldOpen = mc.world != null && mc.player != null;
        String released = HoldRelease.reason(worldOpen, worldOpen && !died(mc), mc.currentScreen != null,
                hold.focusedAtStart, focused(), nextAction, hold.heldTicks, hold.askedTicks);
        if (released == null) {
            KeyBinding.setKeyBindState(hold.keyCode, true);
            return;
        }
        release(mc, hold, released);
    }

    private void release(Minecraft mc, Hold hold, String released) {
        holds.remove(hold.keyCode);
        KeyBinding.setKeyBindState(hold.keyCode, false);
        if (hold.raw) {
            rawDown(hold.keyCode, false);
        }
        if (hold.heldTicks == 0) {
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
            if (hold.raw) {
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
