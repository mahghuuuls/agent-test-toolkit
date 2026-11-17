package com.mahghuuuls.agenttesttoolkit.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.toasts.GuiToast;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Leaves the toasts out of one frame, and puts them back after it.
 *
 * <p>The game draws the toasts after the screen and offers no event to skip them; it skips them
 * only while F1 hides everything. So for the frame the toolkit takes the toasts shown out of the
 * toast overlay's private slots, and the ones waiting out of its private queue (else the overlay
 * would move a waiting one into an empty slot during that very frame and the picture would show
 * it), and puts both back exactly as they were after the capture. The slots' array and the queue
 * stay the same objects: only their contents are moved, so nothing else holding them is
 * affected. The fields are found by their names in the game's mappings, once.
 *
 * <p>Client thread only.
 */
final class ToastHide {

    /** {@code GuiToast.visible}: the toasts on screen, one per slot. */
    private static final String VISIBLE = "field_191791_g";
    /** {@code GuiToast.toastsQueue}: the toasts waiting for a free slot. */
    private static final String QUEUE = "field_191792_h";

    private static boolean resolved;
    private static Field visibleField;
    private static Field queueField;

    private Object[] slots;
    private Object[] savedSlots;
    private Deque<Object> queue;
    private List<Object> savedQueue;

    /** Whether the overlay's toasts are out of the frame now; false when they could not be reached. */
    boolean hide(Minecraft mc) {
        restore();
        if (!resolve()) {
            return false;
        }
        GuiToast overlay = mc.getToastGui();
        if (overlay == null) {
            return false;
        }
        try {
            Object array = visibleField.get(overlay);
            Object waiting = queueField.get(overlay);
            if (!(array instanceof Object[]) || !(waiting instanceof Deque)) {
                return false;
            }
            @SuppressWarnings("unchecked")
            Deque<Object> deque = (Deque<Object>) waiting;
            take((Object[]) array, deque);
            return true;
        } catch (IllegalAccessException | RuntimeException unreadable) {
            restore();
            return false;
        }
    }

    /**
     * The toast overlay's slots as they are now, one per row, null where a row is empty; null
     * when they cannot be read. Read only, for the prompt panel to stay below the toasts.
     */
    static Object[] visibleSlots(Minecraft mc) {
        if (!resolve()) {
            return null;
        }
        GuiToast overlay = mc.getToastGui();
        if (overlay == null) {
            // No overlay draws no toasts.
            return new Object[0];
        }
        try {
            Object array = visibleField.get(overlay);
            return array instanceof Object[] ? (Object[]) array : null;
        } catch (IllegalAccessException | RuntimeException unreadable) {
            return null;
        }
    }

    /** Empties the slots and the queue, keeping what they held for {@link #restore}; the objects themselves stay. */
    void take(Object[] visible, Deque<Object> waiting) {
        restore();
        slots = visible;
        savedSlots = visible.clone();
        queue = waiting;
        savedQueue = new ArrayList<Object>(waiting);
        java.util.Arrays.fill(visible, null);
        waiting.clear();
    }

    /** Puts back what {@link #hide} took, in the same slots and the same queue order; nothing when nothing was taken. */
    void restore() {
        if (slots != null) {
            for (int i = 0; i < slots.length && i < savedSlots.length; i++) {
                if (savedSlots[i] != null) {
                    slots[i] = savedSlots[i];
                }
            }
        }
        if (queue != null) {
            // Ahead of anything added meanwhile, so the order the game had is kept.
            for (int i = savedQueue.size() - 1; i >= 0; i--) {
                queue.addFirst(savedQueue.get(i));
            }
        }
        slots = null;
        savedSlots = null;
        queue = null;
        savedQueue = null;
    }

    private static synchronized boolean resolve() {
        if (!resolved) {
            resolved = true;
            try {
                visibleField = ObfuscationReflectionHelper.findField(GuiToast.class, VISIBLE);
                queueField = ObfuscationReflectionHelper.findField(GuiToast.class, QUEUE);
            } catch (RuntimeException notFound) {
                visibleField = null;
                queueField = null;
            }
        }
        return visibleField != null && queueField != null;
    }
}
