package com.mahghuuuls.agenttesttoolkit.client.menu;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The save folder of a new world, and the rule that it never replaces one (REQ-217, ARC-028).
 *
 * <p>Pure, for tests (rule 18). A world name is already letters, digits, {@code _} and {@code -}
 * ({@link MenuRequest}), so the folder is the name, except a name Windows reserves for a device,
 * which Create New World wraps in {@code _} the same way. The screen then adds {@code -} until
 * the folder is unused, but its check misses a folder with no readable {@code level.dat}, and
 * creating there writes over it (FQ39). So any existing entry of that name refuses the request.
 */
public final class SaveFolders {

    private static final Set<String> RESERVED = new HashSet<String>(Arrays.asList(
            "CON", "COM", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "PRN", "AUX", "CLOCK$", "NUL",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"));

    private SaveFolders() {
    }

    /** The folder Create New World would choose for this name, before its collision suffix. */
    public static String folderFor(String name) {
        return RESERVED.contains(name.toUpperCase(Locale.ROOT)) ? "_" + name + "_" : name;
    }

    /**
     * True when the folder is free: no entry of that name exists in {@code saves/}, whatever it
     * holds. The predicate answers whether an entry exists; on a case-insensitive file system it
     * answers for any case.
     */
    public static boolean isFree(String folder, Predicate<String> exists) {
        return !exists.test(folder);
    }
}
