package com.mahghuuuls.agenttesttoolkit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rule 12, checked over the source: client-only code is reachable only from {@code client} and
 * {@code ClientProxy}. A dedicated server loads every shared class, and a shared class naming a
 * client type fails there with a missing class, which no other unit test would notice.
 */
class ClientIsolationTest {

    private static final File MAIN = new File("src/main/java/com/mahghuuuls/agenttesttoolkit");

    private static boolean allowed(File file) {
        String path = file.getPath().replace('\\', '/');
        return path.contains("/agenttesttoolkit/client/") || path.endsWith("/proxy/ClientProxy.java");
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

    /** Client-only packages, and client-only classes that live in shared packages. */
    private static final String[] CLIENT_ONLY = {
            "net.minecraft.client.", "net.minecraftforge.client.", "net.minecraftforge.fml.client.",
            "org.lwjgl.", "net.minecraft.util.ScreenShotHelper", "com.mahghuuuls.agenttesttoolkit.client."};

    private static boolean refersToClientCode(String text) {
        for (String name : CLIENT_ONLY) {
            if (text.contains(name)) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("no shared class imports net.minecraft.client or the toolkit's client package")
    void sharedCodeHasNoClientImports() throws IOException {
        assertTrue(MAIN.isDirectory(), "the test runs from the project folder: " + MAIN.getAbsolutePath());
        List<File> files = new ArrayList<File>();
        collect(MAIN, files);
        assertTrue(files.size() > 50, "the scan found the sources");

        List<String> violations = new ArrayList<String>();
        for (File file : files) {
            if (allowed(file)) {
                continue;
            }
            String text = new String(Files.readAllBytes(file.toPath()), Charset.forName("UTF-8"));
            if (refersToClientCode(text)) {
                violations.add(file.getPath());
            }
        }
        assertEquals(new ArrayList<String>(), violations);
    }

    /** Baubles' own packages; not the toolkit's {@code integration.baubles}, a key, or a variable named baubles. */
    private static final java.util.regex.Pattern BAUBLES = java.util.regex.Pattern.compile("(?<![\\w.])baubles\\.(api|common|client)\\b");

    @Test
    @DisplayName("the Baubles pattern catches every way of naming Baubles, and nothing of the toolkit's own")
    void baublesPattern() {
        for (String hit : new String[] {"import baubles.api.BaublesApi;", "import static baubles.api.X.y;",
                "(baubles.common.Baubles) x", "List<baubles.api.BaubleType>", "\tbaubles.client.Y"}) {
            assertTrue(BAUBLES.matcher(hit).find(), hit);
        }
        for (String miss : new String[] {"com.mahghuuuls.agenttesttoolkit.integration.baubles.BaublesBridgeImpl",
                "\"devtool.baubles.absent\"", "integration/baubles/", "baubles.slotCount(player)"}) {
            assertTrue(!BAUBLES.matcher(miss).find(), miss);
        }
    }

    @Test
    @DisplayName("rule 11: only integration/baubles refers to Baubles, so the toolkit loads without it")
    void onlyTheBridgeImportsBaubles() throws IOException {
        List<File> files = new ArrayList<File>();
        collect(MAIN, files);
        List<String> violations = new ArrayList<String>();
        boolean bridgeFound = false;
        for (File file : files) {
            String path = file.getPath().replace('\\', '/');
            String text = new String(Files.readAllBytes(file.toPath()), Charset.forName("UTF-8"));
            boolean refersToBaubles = BAUBLES.matcher(text).find();
            if (path.contains("/integration/baubles/")) {
                bridgeFound |= refersToBaubles;
            } else if (refersToBaubles) {
                violations.add(file.getPath());
            }
        }
        assertTrue(bridgeFound, "the scan sees the bridge itself, so it is not vacuous");
        assertEquals(new ArrayList<String>(), violations);
    }
}
