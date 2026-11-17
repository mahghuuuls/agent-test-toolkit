package com.mahghuuuls.agenttesttoolkit.client.screen;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Clicks and types on the open screen through the screen's own handlers, as the game's input
 * handling calls them for a person: {@code mouseClicked} then {@code mouseReleased} at a point in
 * the screen's GUI units, and {@code keyTyped} with a key. So a button fires the game's and
 * Forge's action events (which other mods' buttons use), a container finds the slot under the
 * point and sends the click to the server itself, and a screen's own handling, a mod's included,
 * decides what the click does.
 *
 * <p>The handlers are protected; they are found once by their names in the game's mappings and
 * called on the screen, so a screen's own override runs. Client thread only.
 */
public final class ScreenClicker {

    /** {@code GuiScreen.mouseClicked(int, int, int)}. */
    private static final String MOUSE_CLICKED = "func_73864_a";
    /** {@code GuiScreen.mouseReleased(int, int, int)}. */
    private static final String MOUSE_RELEASED = "func_146286_b";
    /** {@code GuiScreen.keyTyped(char, int)}. */
    private static final String KEY_TYPED = "func_73869_a";
    /** {@code GuiContainer.hoveredSlot}: the slot the game last found under the mouse. */
    private static final String HOVERED_SLOT = "field_147006_u";

    /** The game's mouse buttons. */
    public static final int LEFT = 0;
    public static final int RIGHT = 1;
    /** The key code of Escape, the one a screen closes on. */
    public static final int KEY_ESCAPE = 1;

    private static boolean resolved;
    private static Method mouseClicked;
    private static Method mouseReleased;
    private static Method keyTyped;
    private static Field hoveredSlot;
    private static String failure;

    private ScreenClicker() {
    }

    /** Whether the handlers were found in this game; when not, {@link #failure} names why. */
    public static synchronized boolean available() {
        resolve();
        return mouseClicked != null && mouseReleased != null && keyTyped != null;
    }

    /** What failed when the handlers could not be found; null when they were. */
    public static synchronized String failure() {
        resolve();
        return failure;
    }

    /** A press then a release of that mouse button at that point, as one click. */
    public static void click(GuiScreen screen, int x, int y, int button) throws Exception {
        available();
        invoke(mouseClicked, screen, x, y, button);
        invoke(mouseReleased, screen, x, y, button);
    }

    /** One typed key, as a key going down on the screen. */
    public static void type(GuiScreen screen, char character, int keyCode) throws Exception {
        available();
        invoke(keyTyped, screen, character, keyCode);
    }

    /**
     * Sets the slot the container treats as under the mouse, as its drawing does each frame, so
     * a key typed next acts on it. The next frame sets it again from the real mouse.
     */
    public static void hover(GuiContainer screen, Slot slot) throws IllegalAccessException {
        if (!canHover()) {
            throw new IllegalStateException("noHoveredSlot");
        }
        hoveredSlot.set(screen, slot);
    }

    private static void invoke(Method method, GuiScreen screen, Object... arguments) throws Exception {
        if (method == null) {
            throw new IllegalStateException("noHandler");
        }
        try {
            method.invoke(screen, arguments);
        } catch (InvocationTargetException thrown) {
            // What the screen's own handler threw, not the reflection around it.
            Throwable cause = thrown.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw thrown;
        }
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            mouseClicked = ObfuscationReflectionHelper.findMethod(GuiScreen.class, MOUSE_CLICKED, void.class,
                    int.class, int.class, int.class);
            mouseReleased = ObfuscationReflectionHelper.findMethod(GuiScreen.class, MOUSE_RELEASED, void.class,
                    int.class, int.class, int.class);
            keyTyped = ObfuscationReflectionHelper.findMethod(GuiScreen.class, KEY_TYPED, void.class,
                    char.class, int.class);
        } catch (RuntimeException notFound) {
            mouseClicked = null;
            mouseReleased = null;
            keyTyped = null;
            failure = notFound.getClass().getSimpleName();
        }
        try {
            hoveredSlot = ObfuscationReflectionHelper.findField(GuiContainer.class, HOVERED_SLOT);
        } catch (RuntimeException notFound) {
            hoveredSlot = null;
        }
    }

    /** Whether the hovered slot can be set, which a number key over a slot needs. */
    public static synchronized boolean canHover() {
        resolve();
        return hoveredSlot != null;
    }
}
