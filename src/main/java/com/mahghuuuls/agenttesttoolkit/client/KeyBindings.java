package com.mahghuuuls.agenttesttoolkit.client;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.settings.IKeyConflictContext;
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

    private static boolean resolved;
    private static Field pressTime;

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
