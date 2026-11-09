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

    private static final Set<String> CLIENT_MAY_USE =
            new HashSet<String>(Arrays.asList("client", "net", "logging", "config"));
    /** From the inbox, the client uses only the request-file protocol. */
    private static final Set<String> CLIENT_MAY_USE_FROM_INBOX = new HashSet<String>(Arrays.asList("InboxFiles"));
    /** From the inbox, the session code uses only the pure name check. */
    private static final Set<String> SESSION_MAY_USE_FROM_INBOX = new HashSet<String>(Arrays.asList("LocalJoin"));

    /** Decisions kept pure; a class listed here that does not exist yet is skipped. */
    private static final String[] PURE = {
            "fixture/FixtureOwnership.java", "inbox/InboxRequest.java", "inbox/LocalJoin.java",
            "inbox/PlayerActionOptions.java", "client/menu/MenuRequest.java", "client/menu/WorldOptions.java",
            "client/menu/SaveFolders.java", "client/menu/LocalAddresses.java", "client/PlayerActionGate.java",
            "client/HoldRelease.java", "client/TooltipLines.java"};

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
        if (Arrays.asList(PURE).contains(unixPath) && GAME_REFERENCE.matcher(text).find()) {
            out.add(unixPath + ": a pure decision uses a game type");
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
        assertTrue(pureFiles >= 9, "pure files reached: " + pureFiles);
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
    }
}
