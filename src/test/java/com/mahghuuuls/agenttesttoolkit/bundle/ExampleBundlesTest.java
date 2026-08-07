package com.mahghuuuls.agenttesttoolkit.bundle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped example bundles.
 *
 * <p>Two properties matter and neither is about the examples' content. They must actually parse,
 * since shipping a broken example would greet a first-time operator with a load error from a
 * file they did not write. And every name must be prefixed, because a name defined twice
 * destroys **both** definitions, so an unprefixed shipped name would silently take an
 * operator's own bundle down with it.
 */
class ExampleBundlesTest {

    @Test
    @DisplayName("the shipped examples parse")
    void examplesParse() throws Exception {
        // Written as a Java string literal, so a stray escape is entirely possible and would
        // not show up until someone installed the mod.
        File dir = Files.createTempDirectory("examples").toFile();
        ExampleBundles.seed(dir);

        BundleRegistry registry = new BundleRegistry();
        BundleRegistry.LoadReport report = registry.loadFrom(dir);

        assertFalse(report.hasProblems(), "shipped examples must load cleanly: "
                + report.getProblems());
        assertTrue(report.getLoaded().size() >= 5, "expected the full example set");
    }

    @Test
    @DisplayName("every shipped name is prefixed, so it cannot collide with an operator's own")
    void everyNameIsPrefixed(@TempDir File dir) {
        // The hazard is specific: a duplicated name loads from neither file, so an
        // unprefixed shipped `teardown` would destroy an operator's `teardown` and
        // report a conflict naming a file they never created.
        ExampleBundles.seed(dir);
        BundleRegistry registry = new BundleRegistry();
        Map<String, BundleDefinition> loaded = registry.loadFrom(dir).getLoaded();

        for (String name : loaded.keySet()) {
            assertTrue(name.startsWith("example_"), "unprefixed shipped bundle: " + name);
        }
    }

    @Test
    @DisplayName("seeding never overwrites an existing file")
    void seedingNeverOverwrites(@TempDir File dir) throws Exception {
        // Rewriting on launch destroys edits; updating on version change destroys
        // them silently, which is worse. After the first run these files are the operator's.
        ExampleBundles.seed(dir);
        File written = dir.listFiles()[0];
        Files.write(written.toPath(),
                "{\"mine\": {\"commands\": [\"say edited\"]}}".getBytes(Charset.forName("UTF-8")));

        ExampleBundles.seed(dir);

        String after = new String(Files.readAllBytes(written.toPath()), Charset.forName("UTF-8"));
        assertEquals("{\"mine\": {\"commands\": [\"say edited\"]}}", after,
                "an edited example must survive a second seed");
    }

    @Test
    @DisplayName("the portal examples are present, since they carry the positioning argument")
    void portalExamplesPresent(@TempDir File dir) {
        // The public documentation positions the toolkit honestly against what vanilla
        // already does. These two are that argument in runnable form, which is why their
        // absence should fail a test rather than pass unnoticed.
        ExampleBundles.seed(dir);
        Map<String, BundleDefinition> loaded = new BundleRegistry().loadFrom(dir).getLoaded();

        assertTrue(loaded.containsKey("example_nether_portal"));
        assertTrue(loaded.containsKey("example_end_portal"));
    }
}
