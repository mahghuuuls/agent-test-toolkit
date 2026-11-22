package com.mahghuuuls.agenttesttoolkit.client;

import com.mahghuuuls.agenttesttoolkit.inbox.InputOptions;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.opengl.Display;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Takes the steps of the input message: a key binding pressed, held or released by its name, the
 * bindings listed, a click by another route, a turn of the view, the camera view, and the
 * window's focus emulated. Each goes through the game's own path for that input, so another mod
 * listening there reacts as to a person, and each record names the route it drove. What lasts
 * beyond this tick is handed to {@link HeldInputs}, which also writes its record.
 *
 * <p>Client thread only, at a client tick end, after the common checks of {@link PlayerInput}.
 */
final class InputSteps {

    /** A {@code face} step without a point the server found. */
    static final String REASON_NO_TARGET = "noTarget";
    /** {@code focus gained} while the real window has no focus: the game would not take input. */
    static final String REASON_NO_REAL_FOCUS = "noRealFocus";

    private final HeldInputs held;
    private final HeldInputs.Records records;

    InputSteps(HeldInputs held, HeldInputs.Records records) {
        this.held = held;
        this.records = records;
    }

    /** @param screenOpen a screen is open, or about to open from an action asked this tick */
    void take(Minecraft mc, Asked asked, boolean screenOpen) {
        InputOptions options = asked.options();
        String step = options.getStep();
        String screen = PlayerInput.screenName(mc.currentScreen);
        if (mc.world == null || mc.player == null) {
            refuse(mc, asked, PlayerActionGate.REASON_NO_WORLD, new LinkedHashMap<String, String>());
            return;
        }
        if (InputOptions.KEYS.equals(step)) {
            list(mc, asked, options);
            return;
        }
        if (InputOptions.VIEW.equals(step)) {
            view(mc, asked, options, screen);
            return;
        }
        if (InputOptions.FOCUS.equals(step)) {
            focus(mc, asked, options, screen);
            return;
        }
        if (InputOptions.KEY.equals(step) && InputOptions.RELEASE.equals(options.getMode())) {
            // A screen may be open: the release only lets go.
            key(mc, asked, options, screen);
            return;
        }
        if (screenOpen) {
            refuse(mc, asked, PlayerActionGate.REASON_SCREEN_OPEN, new LinkedHashMap<String, String>());
            return;
        }
        if (InputOptions.KEY.equals(step)) {
            key(mc, asked, options, screen);
        } else if (InputOptions.USE.equals(step) || InputOptions.ATTACK.equals(step)) {
            click(mc, asked, options, screen);
        } else if (InputOptions.HOLD_INPUT.equals(step)) {
            hold(mc, asked, options, screen);
        } else {
            turn(mc, asked, options, screen);
        }
    }

    /** One {@code KEY_BINDING} record per binding under the prefix; changes nothing. */
    private void list(Minecraft mc, Asked asked, InputOptions options) {
        List<KeyLookup.Entry> entries = KeyBindings.entries(mc.gameSettings.keyBindings);
        List<KeyLookup.Listed> listed = KeyLookup.list(entries, options.getName());
        for (KeyLookup.Listed each : listed) {
            LogRecord record = LogRecord.of(EventType.KEY_BINDING)
                    .add("side", "CLIENT")
                    .add("binding", each.entry.name)
                    .add("category", each.entry.category)
                    .add("keyCode", each.entry.keyCode)
                    .add("key", each.entry.keyName)
                    .add("modifier", each.entry.modifier)
                    .add("context", each.entry.context);
            if (each.duplicate) {
                record.add("duplicate", true);
            }
            ToolkitLog.write(record.add("requestTick", asked.requestTick()));
        }
        Map<String, String> fields = new LinkedHashMap<String, String>();
        fields.put("listed", String.valueOf(listed.size()));
        fields.put("truncated", String.valueOf(KeyLookup.matching(entries, options.getName()) > listed.size()));
        records.finish(mc, asked, "done", null, PlayerInput.screenName(mc.currentScreen), null, fields);
    }

    /** A key binding by name: one press, a hold, or the end of a hold. */
    private void key(Minecraft mc, Asked asked, InputOptions options, String screen) {
        KeyLookup.Found found = KeyLookup.find(KeyBindings.entries(mc.gameSettings.keyBindings), options.getName());
        if (found.refusal != null) {
            refuse(mc, asked, found.refusal, new LinkedHashMap<String, String>());
            return;
        }
        KeyBinding binding = mc.gameSettings.keyBindings[found.index];
        int code = binding.getKeyCode();
        Map<String, String> fields = new LinkedHashMap<String, String>();
        fields.put("binding", options.getName());
        fields.put("keyCode", String.valueOf(code));
        fields.put("modifier", KeyBindings.modifier(binding));
        fields.put("context", KeyBindings.context(binding));
        if (InputOptions.RELEASE.equals(options.getMode())) {
            // Only this key's hold ends; the others go on.
            fields.put("wasHeld", String.valueOf(held.release(mc, code)));
            records.finish(mc, asked, "done", null, screen, null, fields);
            return;
        }
        String heldRefusal = HoldRelease.heldRefusal(options.isKeepHold(), code, held.heldKeyCodes());
        if (heldRefusal != null) {
            refuse(mc, asked, heldRefusal, fields);
            return;
        }
        if (options.isRaw() && refuseRaw(mc, asked, code, fields)) {
            return;
        }
        String route = options.getRoute();
        boolean queued = InputOptions.ROUTE_QUEUE.equals(route);
        boolean press = InputOptions.PRESS.equals(options.getMode());
        if (queued && refuseKeyQueue(mc, asked, code, fields)) {
            return;
        }
        fields.put("route", route + (options.isRaw() ? "+raw" : ""));
        if (press) {
            records.screenshotAfter(asked, fields);
            held.press(mc, asked, binding, route, true, options.isRaw(), fields);
            return;
        }
        fields.put("ticks", String.valueOf(options.getTicks()));
        if (queued) {
            // Its started record is written when its key goes down, at the next tick start.
            records.screenshotAfter(asked, fields);
            held.queueHold(asked, binding, options.getTicks(), options.isRaw(), fields);
            return;
        }
        HeldInputs.putTick(fields, "pressedTick", mc);
        records.screenshotAfter(asked, fields);
        String countedOn = held.hold(mc, asked, binding, options.getTicks(), options.isRaw(), true, fields);
        Map<String, String> started = new LinkedHashMap<String, String>(fields);
        if (countedOn != null) {
            // Held all the same, as a real key is; its press went to another binding on the key.
            started.put("countedOn", countedOn);
        }
        records.finish(mc, asked, "started", null, screen, null, started);
    }

    /**
     * A use or attack held beside running holds, as the player action's hold is: the button's
     * binding down, one press counted, the target and the item read as it goes down.
     */
    private void hold(Minecraft mc, Asked asked, InputOptions options, String screen) {
        KeyBinding binding = InputOptions.USE.equals(options.getMode())
                ? mc.gameSettings.keyBindUseItem : mc.gameSettings.keyBindAttack;
        int code = binding.getKeyCode();
        Map<String, String> fields = new LinkedHashMap<String, String>();
        if (code == 0) {
            refuse(mc, asked, PlayerActionGate.REASON_UNBOUND, fields);
            return;
        }
        String heldRefusal = HoldRelease.heldRefusal(options.isKeepHold(), code, held.heldKeyCodes());
        if (heldRefusal != null) {
            refuse(mc, asked, heldRefusal, fields);
            return;
        }
        fields.put("input", options.getMode());
        fields.put("ticks", String.valueOf(options.getTicks()));
        try {
            PlayerInput.describeTarget(mc, mc.objectMouseOver, fields);
            PlayerInput.describeHeld(mc.player.getHeldItemMainhand(), fields);
        } catch (RuntimeException failure) {
            records.finish(mc, asked, "failed", failure.getClass().getSimpleName(), screen, null,
                    new LinkedHashMap<String, String>());
            return;
        }
        Map<String, String> releaseFields = new LinkedHashMap<String, String>();
        releaseFields.put("input", options.getMode());
        held.hold(mc, asked, binding, options.getTicks(), false, false, releaseFields);
        records.screenshotAfter(asked, fields);
        records.finish(mc, asked, "started", null, screen, null, fields);
    }

    /** A use or attack click by the route asked, its record written once the game has handled it. */
    private void click(Minecraft mc, Asked asked, InputOptions options, String screen) {
        KeyBinding binding = InputOptions.USE.equals(options.getStep())
                ? mc.gameSettings.keyBindUseItem : mc.gameSettings.keyBindAttack;
        int code = binding.getKeyCode();
        Map<String, String> fields = new LinkedHashMap<String, String>();
        if (code == 0) {
            refuse(mc, asked, PlayerActionGate.REASON_UNBOUND, fields);
            return;
        }
        String heldRefusal = HoldRelease.heldRefusal(options.isKeepHold(), code, held.heldKeyCodes());
        if (heldRefusal != null) {
            refuse(mc, asked, heldRefusal, fields);
            return;
        }
        String route = options.getRoute();
        if (InputOptions.ROUTE_QUEUE.equals(route) && refuseQueue(mc, asked, code, fields)) {
            return;
        }
        try {
            // What the click will meet and with what, read before it is made.
            PlayerInput.describeTarget(mc, mc.objectMouseOver, fields);
            PlayerInput.describeHeld(mc.player.getHeldItemMainhand(), fields);
        } catch (RuntimeException failure) {
            // Reading the target or the item threw, as a modded block or item can: nothing is pressed.
            records.finish(mc, asked, "failed", failure.getClass().getSimpleName(), screen, null,
                    new LinkedHashMap<String, String>());
            return;
        }
        fields.put("route", route);
        records.screenshotAfter(asked, fields);
        held.press(mc, asked, binding, route, false, false, fields);
    }

    /**
     * {@code look} turns to a yaw and pitch, {@code face} to the point the server found. At once,
     * the turn is made now and the crosshair's target read again, so a click asked in the same
     * tick meets what the view now faces; over ticks, {@link HeldInputs} takes a step each tick.
     */
    private void turn(Minecraft mc, Asked asked, InputOptions options, String screen) {
        EntityPlayerSP player = mc.player;
        Map<String, String> fields = new LinkedHashMap<String, String>();
        double targetYaw;
        double targetPitch;
        if (InputOptions.FACE.equals(options.getStep())) {
            if (!asked.input.isAimed()) {
                refuse(mc, asked, REASON_NO_TARGET, fields);
                return;
            }
            double[] aim = Aim.toward(player.rotationYaw, player.posX, player.posY + player.getEyeHeight(), player.posZ,
                    asked.input.getAimX(), asked.input.getAimY(), asked.input.getAimZ());
            targetYaw = aim[0];
            targetPitch = aim[1];
            fields.put("aimX", HeldInputs.angle(asked.input.getAimX()));
            fields.put("aimY", HeldInputs.angle(asked.input.getAimY()));
            fields.put("aimZ", HeldInputs.angle(asked.input.getAimZ()));
        } else {
            targetYaw = options.getYaw();
            targetPitch = Aim.clampPitch(options.getPitch());
        }
        fields.put("startYaw", HeldInputs.angle(player.rotationYaw));
        fields.put("startPitch", HeldInputs.angle(player.rotationPitch));
        double yaw = Aim.yawChange(player.rotationYaw, targetYaw);
        double pitch = targetPitch - player.rotationPitch;
        int over = InputOptions.LOOK.equals(options.getStep()) ? options.getOver() : 0;
        if (over > 0) {
            fields.put("over", String.valueOf(over));
            records.screenshotAfter(asked, fields);
            held.turn(asked, yaw, pitch, over, fields);
            records.finish(mc, asked, "started", null, screen, null, new LinkedHashMap<String, String>(fields));
            return;
        }
        player.turn(Aim.mouseYaw(yaw), Aim.mousePitch(pitch));
        mc.entityRenderer.getMouseOver(1.0F);
        fields.put("endYaw", HeldInputs.angle(player.rotationYaw));
        fields.put("endPitch", HeldInputs.angle(player.rotationPitch));
        fields.put("turnTicks", "0");
        if (InputOptions.FACE.equals(options.getStep())) {
            try {
                // What the view now faces, so the server can check it against the entity it expected.
                PlayerInput.describeTarget(mc, mc.objectMouseOver, fields);
            } catch (RuntimeException failure) {
                // A modded target that cannot be read leaves the turn made and its record without a target.
            }
        }
        records.screenshotAfter(asked, fields);
        records.finish(mc, asked, "done", null, screen, null, fields);
    }

    /**
     * Sets the camera view as F5 does: the view, the entities' display lists rebuilt, and the
     * shader of the entity watched loaded again in first person and dropped otherwise.
     */
    private void view(Minecraft mc, Asked asked, InputOptions options, String screen) {
        int was = mc.gameSettings.thirdPersonView;
        int view = InputOptions.VIEWS.indexOf(options.getMode());
        mc.gameSettings.thirdPersonView = view;
        mc.renderGlobal.setDisplayListEntitiesDirty();
        mc.entityRenderer.loadEntityShader(view == 0 ? mc.getRenderViewEntity() : null);
        Map<String, String> fields = new LinkedHashMap<String, String>();
        fields.put("viewWas", was >= 0 && was < InputOptions.VIEWS.size() ? InputOptions.VIEWS.get(was) : String.valueOf(was));
        fields.put("view", options.getMode());
        records.screenshotAfter(asked, fields);
        records.finish(mc, asked, "done", null, screen, null, fields);
    }

    /**
     * What the game does when its window loses or regains focus, without touching the window:
     * the real window keeps its focus, so a mod that asks the window itself sees no change.
     */
    private void focus(Minecraft mc, Asked asked, InputOptions options, String screen) {
        Map<String, String> fields = new LinkedHashMap<String, String>();
        fields.put("emulated", "true");
        fields.put("windowChanged", "false");
        if (InputOptions.FOCUS_GAINED.equals(options.getMode())) {
            if (!Display.isActive()) {
                refuse(mc, asked, REASON_NO_REAL_FOCUS, fields);
                return;
            }
            // Closes an open screen, as a click into the window does.
            held.gainFocus();
            mc.setIngameFocus();
        } else {
            // The holds' and presses' records first, while their counts are still there to read.
            held.loseFocus(mc);
            KeyBinding.unPressAllKeys();
            mc.setIngameNotInFocus();
            if (mc.gameSettings.pauseOnLostFocus) {
                mc.displayInGameMenu();
            }
        }
        records.screenshotAfter(asked, fields);
        records.finish(mc, asked, "done", null, screen, PlayerInput.screenName(mc.currentScreen), fields);
    }

    /** True when the raw key state cannot be written here: refused, with what failed the first time. */
    private boolean refuseRaw(Minecraft mc, Asked asked, int code, Map<String, String> fields) {
        if (code < 0) {
            // A mouse button has no key state in the keyboard library.
            fields.put("detail", "mouseBinding");
            refuse(mc, asked, RouteState.REASON_ROUTE_UNAVAILABLE, fields);
            return true;
        }
        if (RawKeys.Choice.get() != null) {
            return false;
        }
        String failure = RawKeys.Choice.state().takeFailureName();
        if (failure != null) {
            fields.put("detail", failure);
        }
        refuse(mc, asked, RouteState.REASON_ROUTE_UNAVAILABLE, fields);
        return true;
    }

    /** True when a click cannot go through the mouse queue here: refused, with what failed the first time. */
    private boolean refuseQueue(Minecraft mc, Asked asked, int code, Map<String, String> fields) {
        if (MouseQueue.button(code) < 0) {
            // The queue holds mouse buttons; a binding on a key has no button to put there.
            fields.put("detail", "keyboardBinding");
            refuse(mc, asked, RouteState.REASON_ROUTE_UNAVAILABLE, fields);
            return true;
        }
        if (MouseQueue.CHOICE.get() != null) {
            return false;
        }
        return refuseUnavailable(mc, asked, MouseQueue.CHOICE.state(), fields);
    }

    /**
     * True when a key binding's press or hold cannot go through the input queue of its key or
     * button here: refused, with why in {@code detail}. A binding on a mouse button goes through
     * the mouse queue, as a click does, and its key-up has the click's fallback: the binding is
     * let go a tick after the key-up is queued unless the real button is down, so a mod that
     * cancels the button's release leaves nothing held. A binding on a key the keyboard library
     * has no code for is refused {@code notQueueable}.
     */
    private boolean refuseKeyQueue(Minecraft mc, Asked asked, int code, Map<String, String> fields) {
        if (MouseQueue.button(code) >= 0) {
            return refuseQueue(mc, asked, code, fields);
        }
        KeyQueue queue = KeyQueue.CHOICE.get();
        if (queue == null) {
            return refuseUnavailable(mc, asked, KeyQueue.CHOICE.state(), fields);
        }
        if (!queue.reaches(code)) {
            // A key the game knows only by its character: the library has no code to carry it.
            fields.put("detail", "notQueueable");
            refuse(mc, asked, RouteState.REASON_ROUTE_UNAVAILABLE, fields);
            return true;
        }
        return false;
    }

    /** Refuses a route that cannot be driven in this launch, naming its first failure once. */
    private boolean refuseUnavailable(Minecraft mc, Asked asked, RouteState state, Map<String, String> fields) {
        String failure = state.takeFailureName();
        if (failure != null) {
            fields.put("detail", failure);
        }
        refuse(mc, asked, RouteState.REASON_ROUTE_UNAVAILABLE, fields);
        return true;
    }

    private void refuse(Minecraft mc, Asked asked, String reason, Map<String, String> fields) {
        records.finish(mc, asked, "refused", reason, PlayerInput.screenName(mc.currentScreen), null, fields);
    }
}
