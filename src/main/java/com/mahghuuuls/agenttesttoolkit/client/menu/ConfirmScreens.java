package com.mahghuuuls.agenttesttoolkit.client.menu;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Which screens ask a person to decide something, which of them a world asks as it opens (the
 * one owner of that list, for the channel's menu check and the {@code open} step's watch), what
 * kind of question it is, and whether a screen is the one a {@code close} names, from class
 * names alone.
 *
 * <p>Pure: no game type, so the lists and their order are unit-tested. A screen is judged by its
 * class and every superclass, most specific first, so a subclass of a yes-or-no screen (the old
 * save's question, a link's confirmation, a mod's own) counts as one. A mod's own yes-or-no
 * screen that does not extend these cannot be recognised.
 */
final class ConfirmScreens {

    static final String YES_NO = "net.minecraft.client.gui.GuiYesNo";
    /** Forge's notice; its question, {@code GuiConfirmation}, extends it. */
    static final String NOTIFICATION = "net.minecraftforge.fml.client.GuiNotification";
    static final String CONFIRMATION = "net.minecraftforge.fml.client.GuiConfirmation";
    static final String BACKUP_FAILED = "net.minecraftforge.fml.client.GuiBackupFailed";
    /** The question of a save with modded data from an older Forge, a yes-or-no screen. */
    static final String OLD_SAVE = "net.minecraftforge.fml.client.GuiOldSaveLoadConfirm";

    /** The screens a {@code close} never closes, and their subclasses. */
    static final List<String> CONFIRMATIONS = Collections.unmodifiableList(Arrays.asList(YES_NO, NOTIFICATION,
            BACKUP_FAILED));

    /** The kinds of question a world can show as it opens. */
    static final String KIND_OLD_SAVE = "oldSave";
    static final String KIND_MISSING_REGISTRIES = "missingRegistries";
    static final String KIND_MISSING_ENTRIES = "missingEntries";
    static final String KIND_BACKUP_LEVEL_DAT = "backupLevelDat";
    /** A notice with only "OK", such as a save that cannot be loaded. */
    static final String KIND_NOTICE = "notice";
    /** A yes-or-no question of Forge's whose text names none of the kinds above. */
    static final String KIND_OTHER = "other";

    /** {@code close}'s refusals. */
    static final String REASON_CONFIRM_SCREEN = "confirmScreen";
    static final String REASON_SCREEN_MISMATCH = "screenMismatch";
    static final String REASON_NOT_PAUSED = "notPaused";
    /** The screen's key handler cannot be found in this game; the word the in-world screen steps use for it. */
    static final String REASON_ROUTE_UNAVAILABLE = "routeUnavailable";

    private ConfirmScreens() {
    }

    /** Whether a screen of this class hierarchy asks a person to decide, and must be left for them. */
    static boolean isConfirmation(List<String> hierarchy) {
        for (String name : CONFIRMATIONS) {
            if (hierarchy.contains(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a screen of this hierarchy is one a world's opening asks with: the old save's
     * question, or one of Forge's startup questions or notices. None of them is a menu the channel
     * may act on: each waits for a person or for the {@code open} step that watches it.
     */
    static boolean isQuestion(List<String> hierarchy) {
        return isOldSave(hierarchy) || isStartupQuestion(hierarchy);
    }

    /**
     * Whether a screen of this hierarchy is one of Forge's startup questions or notices, which
     * show while the world's start holds the client thread: no client tick runs and no frame is
     * drawn but the loading loop's.
     */
    static boolean isStartupQuestion(List<String> hierarchy) {
        return hierarchy.contains(NOTIFICATION);
    }

    /** Whether a screen of this hierarchy is the old save's question, an ordinary screen where the client ticks. */
    static boolean isOldSave(List<String> hierarchy) {
        return hierarchy.contains(OLD_SAVE);
    }

    /**
     * Whether {@code close} may close the screen of this hierarchy, and why not: {@code notPaused}
     * when the game is not paused, {@code confirmScreen} for a screen that asks a person,
     * {@code screenMismatch} when the named class is neither the screen's nor a superclass.
     * Null when it may.
     */
    static String closeRefusal(boolean paused, List<String> hierarchy, String named) {
        if (!paused || hierarchy.isEmpty()) {
            return REASON_NOT_PAUSED;
        }
        if (isConfirmation(hierarchy)) {
            return REASON_CONFIRM_SCREEN;
        }
        return hierarchy.contains(named) ? null : REASON_SCREEN_MISMATCH;
    }

    /**
     * The kind of a question a world shows as it opens: the old save's own screen, a notice
     * (Forge's question screen without a yes and a no), or, for Forge's yes-or-no question, the
     * kind its text names; Forge writes these texts itself, in English.
     *
     * @param hierarchy the screen's class and superclasses
     * @param text      the question's text; empty or null when the screen has none
     */
    static String questionKind(List<String> hierarchy, String text) {
        if (isOldSave(hierarchy)) {
            return KIND_OLD_SAVE;
        }
        if (!hierarchy.contains(CONFIRMATION)) {
            return KIND_NOTICE;
        }
        String t = text == null ? "" : text;
        if (t.contains("missing/unknown registrie")) {
            return KIND_MISSING_REGISTRIES;
        }
        if (t.contains("missing registry entries")) {
            return KIND_MISSING_ENTRIES;
        }
        if (t.contains("backup level.dat")) {
            return KIND_BACKUP_LEVEL_DAT;
        }
        return KIND_OTHER;
    }

    /** The question's text as one line for a record: line breaks as {@code " / "}, at most 256 characters. */
    static String oneLine(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (String part : text.split("\n")) {
            StringBuilder printable = new StringBuilder();
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                printable.append(Character.isISOControl(c) ? ' ' : c);
            }
            String trimmed = printable.toString().trim();
            if (!trimmed.isEmpty()) {
                out.append(out.length() == 0 ? "" : " / ").append(trimmed);
            }
        }
        String line = out.toString();
        return line.length() <= 256 ? line : line.substring(0, 256);
    }
}
