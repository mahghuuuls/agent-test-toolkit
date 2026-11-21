package com.mahghuuuls.agenttesttoolkit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The package rules the player actions touch, checked over the source, so a forbidden reference
 * fails the build instead of waiting for a reviewer: what the client code may use, what the
 * session code may take from the inbox, and which decisions stay free of the game so their
 * tests need none.
 */
class PackageRulesTest {

    private static final File MAIN = new File("src/main/java/com/mahghuuuls/agenttesttoolkit");

    /** A reference to one of the toolkit's packages: the package, then the class or subpackage. */
    private static final Pattern TOOLKIT_REFERENCE =
            Pattern.compile("com\\.mahghuuuls\\.agenttesttoolkit\\.([a-z]\\w*)\\.(\\w+|\\*)");
    /** A class in the toolkit's root package, such as the mod class. */
    private static final Pattern ROOT_REFERENCE = Pattern.compile("com\\.mahghuuuls\\.agenttesttoolkit\\.([A-Z]\\w*)");
    /** Any Minecraft or Forge type, imported or written out. */
    private static final Pattern GAME_REFERENCE = Pattern.compile("\\bnet\\.minecraft(forge)?\\.");
    /**
     * A string literal. A pure class may compare class names as text, which loads nothing, so a
     * game class's name inside quotes is not a use of the game type.
     */
    private static final Pattern STRING_LITERAL = Pattern.compile("\"(?:[^\"\\\\\n]|\\\\.)*\"");
    /**
     * A class loaded by its name. Text alone loads nothing, but text handed to these calls does,
     * so a pure class may not reach a game type through quotes this way.
     */
    private static final Pattern LOAD_BY_NAME = Pattern.compile("\\bClass\\.forName\\s*\\(|\\bloadClass\\s*\\(");

    private static final Set<String> CLIENT_MAY_USE =
            new HashSet<String>(Arrays.asList("client", "net", "logging", "config"));
    /**
     * From the inbox, the client uses only the request-file protocol, the pure parsers of the
     * newer steps, and the pure hide set its pictures share with the server.
     */
    private static final Set<String> CLIENT_MAY_USE_FROM_INBOX = new HashSet<String>(Arrays.asList(
            "InboxFiles", "InputOptions", "ScreenOptions", "HideSet"));
    /** From the inbox, the session code uses only the pure name check. */
    private static final Set<String> SESSION_MAY_USE_FROM_INBOX = new HashSet<String>(Arrays.asList("LocalJoin"));

    /** Decisions kept pure; a class listed here that does not exist yet is skipped. */
    private static final String[] PURE = {
            "fixture/FixtureOwnership.java", "inbox/InboxRequest.java", "inbox/LocalJoin.java",
            "inbox/PlayerActionOptions.java", "client/menu/MenuRequest.java", "client/menu/WorldOptions.java",
            "client/menu/SaveFolders.java", "client/menu/LocalAddresses.java", "client/PlayerActionGate.java",
            "client/HoldRelease.java", "client/TooltipLines.java", "client/LeaveStep.java", "inbox/InputOptions.java",
            "inbox/ScreenOptions.java", "inbox/StepExtras.java", "command/sub/ClientStepRoute.java",
            "client/KeyLookup.java", "client/Aim.java", "client/PressTaken.java", "client/RouteState.java",
            "client/screen/ScreenTargets.java", "client/menu/ConfirmScreens.java", "client/menu/ModListChoice.java",
            "client/FrameHide.java", "inbox/HideSet.java", "client/menu/QuestionAnswers.java",
            "client/PanelLayout.java", "wait/PromptPanels.java", "client/PressStages.java", "client/QueueChoice.java",
            "client/TooltipCapture.java", "integration/baubles/BaubleEquipCalls.java",
            "command/BuiltInTolerance.java"};

    /**
     * The roots a source file may import from. Another loader's or mod's classes are reached by
     * reflection by name, so the build never needs them; Baubles is the one integration compiled
     * against, and only its bridge may import it.
     */
    private static final String[] IMPORT_ROOTS = {"java.", "javax.", "net.minecraft.", "net.minecraftforge.",
            "com.mahghuuuls.agenttesttoolkit.", "com.google.", "com.mojang.", "io.netty.", "org.apache.", "org.lwjgl."};
    private static final Pattern IMPORT = Pattern.compile("(?m)^import\\s+(?:static\\s+)?([\\w.]+)");
    /**
     * Baubles' API written out in the code rather than imported. Only the bridge's package may
     * use it at all, since a provider of that API may lack any part of it.
     */
    private static final Pattern BAUBLES_API_REFERENCE = Pattern.compile("\\bbaubles\\.api\\.");
    /**
     * The per-slot type that only some Baubles providers have. Never a compile reference, so the
     * toolkit loads on a provider without it; it is reached by reflection, by method name.
     */
    private static final Pattern BAUBLE_TYPE_REFERENCE = Pattern.compile("\\bIBaubleType\\b");
    /** A direct call to the event flag only official Baubles has; on another provider the call does not link. */
    private static final Pattern EVENT_FLAG_CALL = Pattern.compile("\\.\\s*isEventBlocked\\s*\\(");
    private static final Pattern COMMENT = Pattern.compile("(?s)/\\*.*?\\*/|//[^\\n]*");
    /**
     * Asking for the Baubles bridge. Only the command code's one access point may, so every
     * caller gets the bridge that records an unsupported provider and none can forget to.
     */
    private static final Pattern BRIDGE_LOOKUP = Pattern.compile("\\bIntegrations\\s*\\.\\s*baubles\\s*\\(");
    private static final String BRIDGE_ACCESS = "command/sub/BaublesAccess.java";

    /** The rule broken by a file at this path, relative to the toolkit's package, with this text. */
    static List<String> violations(String path, String text) {
        List<String> out = new ArrayList<String>();
        String unixPath = path.replace('\\', '/');
        boolean client = unixPath.startsWith("client/");
        boolean session = unixPath.startsWith("session/");
        Matcher reference = TOOLKIT_REFERENCE.matcher(text);
        while (reference.find()) {
            String pkg = reference.group(1);
            String name = reference.group(2);
            if (client && !CLIENT_MAY_USE.contains(pkg)
                    && !("inbox".equals(pkg) && CLIENT_MAY_USE_FROM_INBOX.contains(name))) {
                out.add(unixPath + ": client uses " + pkg + "." + name);
            }
            if (session && "inbox".equals(pkg) && !SESSION_MAY_USE_FROM_INBOX.contains(name)) {
                out.add(unixPath + ": session uses inbox." + name);
            }
        }
        Matcher root = ROOT_REFERENCE.matcher(text);
        while (root.find()) {
            // The build's own constants are shared; the mod class and the rest are not.
            if (client && !"Tags".equals(root.group(1))) {
                out.add(unixPath + ": client uses " + root.group(1));
            }
        }
        if (Arrays.asList(PURE).contains(unixPath)) {
            String code = STRING_LITERAL.matcher(text).replaceAll("\"\"");
            if (GAME_REFERENCE.matcher(code).find()) {
                out.add(unixPath + ": a pure decision uses a game type");
            }
            if (LOAD_BY_NAME.matcher(code).find()) {
                out.add(unixPath + ": a pure decision loads a class by name");
            }
        }
        // Quoted text and comments name things without using them, so they are left out here.
        String code = COMMENT.matcher(STRING_LITERAL.matcher(text).replaceAll("\"\"")).replaceAll(" ");
        if (!unixPath.startsWith("integration/baubles/") && BAUBLES_API_REFERENCE.matcher(code).find()) {
            out.add(unixPath + ": uses baubles.api outside the bridge");
        }
        if (BAUBLE_TYPE_REFERENCE.matcher(code).find()) {
            out.add(unixPath + ": references IBaubleType");
        }
        if (EVENT_FLAG_CALL.matcher(code).find()) {
            out.add(unixPath + ": calls isEventBlocked directly");
        }
        if (!BRIDGE_ACCESS.equals(unixPath) && BRIDGE_LOOKUP.matcher(code).find()) {
            out.add(unixPath + ": asks Integrations for the Baubles bridge instead of " + BRIDGE_ACCESS);
        }
        Matcher imported = IMPORT.matcher(text);
        while (imported.find()) {
            String name = imported.group(1);
            boolean allowed = unixPath.startsWith("integration/baubles/") && name.startsWith("baubles.");
            for (String importRoot : IMPORT_ROOTS) {
                allowed |= name.startsWith(importRoot);
            }
            if (!allowed) {
                out.add(unixPath + ": imports " + name);
            }
        }
        return out;
    }

    private static void collect(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collect(child, out);
            } else if (child.getName().endsWith(".java")) {
                out.add(child);
            }
        }
    }

    @Test
    @DisplayName("the client, session and pure classes keep to their package rules")
    void sourcesKeepTheRules() throws IOException {
        assertTrue(MAIN.isDirectory(), "the test runs from the project folder: " + MAIN.getAbsolutePath());
        List<File> files = new ArrayList<File>();
        collect(MAIN, files);
        assertTrue(files.size() > 50, "the scan found the sources");
        String base = MAIN.getPath().replace('\\', '/') + "/";
        List<String> found = new ArrayList<String>();
        int clientFiles = 0;
        int pureFiles = 0;
        for (File file : files) {
            String path = file.getPath().replace('\\', '/').substring(base.length());
            if (path.startsWith("client/")) {
                clientFiles++;
            }
            if (Arrays.asList(PURE).contains(path)) {
                pureFiles++;
            }
            found.addAll(violations(path, new String(Files.readAllBytes(file.toPath()), Charset.forName("UTF-8"))));
        }
        assertEquals(new ArrayList<String>(), found);
        // The paths were read as the rules name them, so the rules were applied at all.
        assertTrue(clientFiles >= 5, "client files reached: " + clientFiles);
        assertTrue(pureFiles >= 28, "pure files reached: " + pureFiles);
        assertTrue(new File(MAIN, "inbox/PlayerActionOptions.java").isFile(), "a pure class the list names exists");
    }

    @Test
    @DisplayName("each rule catches a planted breach, and allows what the rules allow")
    void rulesCatchBreaches() {
        assertFalse(violations("client/PlayerInput.java",
                "import com.mahghuuuls.agenttesttoolkit.inbox.PlayerActionOptions;").isEmpty(), "client to inbox");
        assertFalse(violations("client/menu/MenuChannel.java",
                "com.mahghuuuls.agenttesttoolkit.state.ToolkitState.get()").isEmpty(), "client to state, written out");
        assertTrue(violations("client/menu/MenuChannel.java",
                "import com.mahghuuuls.agenttesttoolkit.inbox.InboxFiles;").isEmpty(), "the request-file protocol");
        assertFalse(violations("client/menu/MenuChannel.java",
                "import com.mahghuuuls.agenttesttoolkit.inbox.*;").isEmpty(), "a wildcard import");
        assertFalse(violations("client/PlayerInput.java",
                "com.mahghuuuls.agenttesttoolkit.AgentTestToolkitMod.instance()").isEmpty(), "the mod class");
        assertTrue(violations("client/PlayerInput.java",
                "import com.mahghuuuls.agenttesttoolkit.net.ToolkitChannel;\nimport com.mahghuuuls.agenttesttoolkit.Tags;")
                .isEmpty(), "net, and the build's own constants");
        assertFalse(violations("session/HostName.java",
                "import com.mahghuuuls.agenttesttoolkit.inbox.InboxGate;").isEmpty(), "session to the inbox gate");
        assertTrue(violations("session/HostName.java",
                "import com.mahghuuuls.agenttesttoolkit.inbox.LocalJoin;").isEmpty(), "the pure name check");
        assertFalse(violations("inbox/PlayerActionOptions.java", "import net.minecraft.client.Minecraft;").isEmpty());
        assertFalse(violations("client/PlayerActionGate.java",
                "boolean x = net.minecraftforge.fml.common.Loader.isModLoaded(\"a\");").isEmpty());
        assertTrue(violations("client/PlayerInput.java", "import net.minecraft.client.Minecraft;").isEmpty(),
                "not a pure class");
        assertTrue(violations("client/PlayerInput.java",
                "import com.mahghuuuls.agenttesttoolkit.inbox.InputOptions;\nimport com.mahghuuuls.agenttesttoolkit.inbox.ScreenOptions;")
                .isEmpty(), "the pure parsers of the newer steps");
        assertFalse(violations("client/LeaveStep.java", "import net.minecraft.client.Minecraft;").isEmpty(),
                "the leave's decision stays pure");
        assertTrue(violations("client/menu/ConfirmScreens.java",
                "static final String YES_NO = \"net.minecraft.client.gui.GuiYesNo\";").isEmpty(),
                "a class name compared as text loads nothing");
        assertFalse(violations("client/menu/ConfirmScreens.java",
                "String a = \"x\"; String b = net.minecraft.client.gui.GuiYesNo.class.getName();").isEmpty(),
                "the type itself, outside the quotes, is still a use");
        assertFalse(violations("client/menu/ConfirmScreens.java",
                "String a = \"x\"; Object b = net.minecraft.client.gui.GuiYesNo.class; String c = \"y\";").isEmpty(),
                "two quoted texts around a game type on one line: each quote ends at its own closing mark");
        assertFalse(violations("client/menu/ConfirmScreens.java",
                "Class<?> c = Class.forName(\"net.minecraft.client.gui.GuiYesNo\");").isEmpty(),
                "a pure class does not load a class by its name");
        assertFalse(violations("client/menu/ConfirmScreens.java",
                "Object c = getClass().getClassLoader().loadClass(\"a.B\");").isEmpty(), "nor through a loader");
        assertTrue(violations("client/menu/ConfirmScreens.java", "Charset utf8 = Charset.forName(\"UTF-8\");").isEmpty(),
                "a charset by its name loads no class");
        assertTrue(violations("client/menu/MenuRequest.java",
                "import com.mahghuuuls.agenttesttoolkit.inbox.HideSet;").isEmpty(), "the pure hide set");
        assertFalse(violations("client/RawKeys.java", "import com.cleanroommc.client.sdl.Window;").isEmpty(),
                "another loader's class is not compiled against");
        assertTrue(violations("client/RawKeys.java",
                "Class<?> window = Class.forName(\"com.cleanroommc.client.sdl.Window\");").isEmpty(), "but reached by name");
        assertFalse(violations("command/sub/BaublesSubCommand.java", "import baubles.api.BaublesApi;").isEmpty());
        assertTrue(violations("integration/baubles/BaublesBridgeImpl.java", "import baubles.api.BaublesApi;").isEmpty());
        assertFalse(violations("inspect/Inventories.java",
                "Object h = baubles.api.BaublesApi.getBaublesHandler(player);").isEmpty(), "baubles.api written out");
        assertTrue(violations("integration/baubles/BaublesBridgeImpl.java",
                "Object h = baubles.api.BaublesApi.getBaublesHandler(player);").isEmpty(), "inside the bridge");
        assertFalse(violations("integration/baubles/BaublesBridgeImpl.java",
                "import baubles.api.IBaubleType;").isEmpty(), "the per-slot type, even in the bridge");
        assertFalse(violations("integration/baubles/ProviderMethods.java",
                "IBaubleType type = handler.getSlotType(0);").isEmpty(), "the per-slot type, written out");
        assertFalse(violations("integration/baubles/BaublesBridgeImpl.java",
                "boolean events = !handler.isEventBlocked();").isEmpty(), "the event flag called directly");
        assertTrue(violations("integration/baubles/ProviderMethods.java",
                "static final String IS_EVENT_BLOCKED = \"isEventBlocked\"; // not handler.isEventBlocked()").isEmpty(),
                "the method named as text or in a comment is not a call");
        assertTrue(violations("integration/baubles/ProviderMethods.java",
                "/* the provider's IBaubleType */ Method m = type.getMethod(\"getSlotType\", int.class);").isEmpty(),
                "the type named in a comment is not a reference");
        assertFalse(violations("inspect/Inventories.java",
                "BaublesBridge b = com.mahghuuuls.agenttesttoolkit.integration.Integrations.baubles();").isEmpty(),
                "a caller asking for the bridge itself");
        assertFalse(violations("command/sub/BaublesSubCommand.java",
                "BaublesBridge baubles = Integrations.baubles();").isEmpty(), "even a command");
        assertTrue(violations(BRIDGE_ACCESS, "BaublesBridge bridge = Integrations.baubles();").isEmpty(),
                "the one access point");
    }
}
