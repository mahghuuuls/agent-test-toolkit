package com.mahghuuuls.agenttesttoolkit.client;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.client.settings.KeyBindingMap;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * The game's key bindings as {@link KeyLookup} reads them, and the one private value of a binding
 * the toolkit reads: how many presses are counted and not yet taken.
 */
final class KeyBindings {

    /** The press count's name in the obfuscated game; the helper maps it in a development game. */
    private static final String PRESS_TIME = "field_151474_i";

    /** The map of bindings by key, in the obfuscated game; the helper maps it in a development game. */
    private static final String HASH = "field_74514_b";

    private static boolean resolved;
    private static Field pressTime;
    private static boolean lookupResolved;
    private static KeyBindingMap lookup;

    private KeyBindings() {
    }

    /** Every registered binding, in the controls' order, as plain values. */
    static List<KeyLookup.Entry> entries(KeyBinding[] bindings) {
        List<KeyLookup.Entry> out = new ArrayList<KeyLookup.Entry>(bindings.length);
        for (KeyBinding binding : bindings) {
            out.add(new KeyLookup.Entry(binding.getKeyDescription(), binding.getKeyCategory(), binding.getKeyCode(),
                    displayName(binding), modifier(binding), context(binding)));
        }
        return out;
    }

    static String modifier(KeyBinding binding) {
        return binding.getKeyModifier().name();
    }

    /** The conflict context's name; a mod's own context, which is no enum, by its class. */
    static String context(KeyBinding binding) {
        IKeyConflictContext context = binding.getKeyConflictContext();
        if (context instanceof Enum) {
            return ((Enum<?>) context).name();
        }
        return context == null ? "none" : context.getClass().getSimpleName();
    }

    private static String displayName(KeyBinding binding) {
        try {
            return binding.getDisplayName();
        } catch (RuntimeException unnamed) {
            // A key code the keyboard library has no name for.
            return String.valueOf(binding.getKeyCode());
        }
    }

    /**
     * Counts one press of the named binding's key, as a key going down does, and says which
     * binding the game counted it on when that was not the named one (see
     * {@link KeyLookup#countedElsewhere}); null when it was, or when the counts cannot be read.
     */
    static String countPress(KeyBinding[] all, KeyBinding named) {
        int code = named.getKeyCode();
        List<KeyBinding> same = new ArrayList<KeyBinding>();
        List<String> names = new ArrayList<String>();
        int at = 0;
        for (KeyBinding binding : all) {
            if (binding.getKeyCode() == code) {
                if (binding == named) {
                    at = same.size();
                }
                same.add(binding);
                names.add(binding.getKeyDescription());
            }
        }
        if (!same.contains(named)) {
            at = same.size();
            same.add(named);
            names.add(named.getKeyDescription());
        }
        int[] before = counts(same);
        KeyBinding.onTick(code);
        return KeyLookup.countedElsewhere(names, before, counts(same), at);
    }

    /**
     * Which binding the game would count a press of the named binding's key on now, when not the
     * named one, decided before the key goes down (see {@link KeyLookup#countedOnActive}): the
     * one the game's own lookup finds, which is how the game picks the binding it counts a key
     * or button going down on. Null when it is the named one, or when the lookup cannot be read.
     */
    static String countedOnBeforePress(KeyBinding named) {
        KeyBindingMap map = lookup();
        if (map == null) {
            return KeyLookup.countedOnActive(false, false, null);
        }
        KeyBinding active;
        try {
            active = map.lookupActive(named.getKeyCode());
        } catch (RuntimeException unreadable) {
            return KeyLookup.countedOnActive(false, false, null);
        }
        return KeyLookup.countedOnActive(true, active == named, active == null ? null : active.getKeyDescription());
    }

    /** The game's own map of the bindings by key, the one its key handling reads; null when it cannot be read. */
    private static synchronized KeyBindingMap lookup() {
        if (!lookupResolved) {
            lookupResolved = true;
            try {
                Field field = ObfuscationReflectionHelper.findField(KeyBinding.class, HASH);
                Object value = field.get(null);
                lookup = value instanceof KeyBindingMap ? (KeyBindingMap) value : null;
            } catch (IllegalAccessException | RuntimeException notFound) {
                lookup = null;
            }
        }
        return lookup;
    }

    private static int[] counts(List<KeyBinding> bindings) {
        int[] out = new int[bindings.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = pressCount(bindings.get(i));
        }
        return out;
    }

    /** The binding's counted presses not yet taken; -1 when this game's binding cannot be read. */
    static synchronized int pressCount(KeyBinding binding) {
        if (!resolved) {
            resolved = true;
            try {
                pressTime = ObfuscationReflectionHelper.findField(KeyBinding.class, PRESS_TIME);
            } catch (RuntimeException notFound) {
                pressTime = null;
            }
        }
        if (pressTime == null) {
            return -1;
        }
        try {
            return pressTime.getInt(binding);
        } catch (IllegalAccessException | RuntimeException unreadable) {
            return -1;
        }
    }
}
