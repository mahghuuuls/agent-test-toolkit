package com.mahghuuuls.agenttesttoolkit.bundle;

import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bundle merging, conflict handling and directory loading.
 *
 * <p>The conflict and partial-loading rules are the ones with real consequences, and both are
 * awkward to stage in a game: they need several files, one of them deliberately broken.
 * Exercised directly here instead.
 */
class BundleRegistryTest {

    @BeforeEach
    void startCapture() {
        // The registry reports problems through ToolkitLog. Captured so tests do not depend on
        // log4j configuration and do not write to a real log file.
        ToolkitLog.startCaptureForTesting();
    }

    @AfterEach
    void stopCapture() {
        ToolkitLog.stopCaptureForTesting();
    }

    private static BundleDefinition bundle(String name) {
        return new BundleDefinition(name, null, true,
                Collections.singletonList(new BundleCommand("say " + name, 0)));
    }

    private static Map<String, BundleDefinition> bundles(String... names) {
        Map<String, BundleDefinition> map = new LinkedHashMap<String, BundleDefinition>();
        for (String name : names) {
            map.put(name, bundle(name));
        }
        return map;
    }

    // --- Merging and conflicts ------------------------------------------------------

    @Test
    @DisplayName("bundles from separate files share one namespace")
    void bundlesFromSeparateFilesMerge() {
        BundleRegistry.LoadReport report = BundleRegistry.mergeSources(
                BundleRegistry.source("combat.json", bundles("a", "b")),
                BundleRegistry.source("blocks.json", bundles("c")));

        assertEquals(3, report.getLoaded().size());
        assertFalse(report.hasProblems());
    }

    @Test
    @DisplayName("a duplicated name loads from neither file")
    void duplicateNameLoadsFromNeither() {
        // REQ-012. Picking a winner by file order would be deterministic but wrong: the two
        // definitions differ, so running either is a coin flip on which the author meant.
        BundleRegistry.LoadReport report = BundleRegistry.mergeSources(
                BundleRegistry.source("combat.json", bundles("shared", "only_a")),
                BundleRegistry.source("wizardry.json", bundles("shared", "only_b")));

        assertNull(report.getLoaded().get("shared"));
        assertEquals(2, report.getLoaded().size());
        assertTrue(report.hasProblems());
    }

    @Test
    @DisplayName("a conflict names every file involved")
    void conflictNamesEveryFile() {
        BundleRegistry.LoadReport report = BundleRegistry.mergeSources(
                BundleRegistry.source("combat.json", bundles("shared")),
                BundleRegistry.source("wizardry.json", bundles("shared")),
                BundleRegistry.source("misc.json", bundles("shared")));

        String problem = report.getProblems().get(0);
        assertTrue(problem.contains("shared"), problem);
        assertTrue(problem.contains("combat.json"), problem);
        assertTrue(problem.contains("wizardry.json"), problem);
        assertTrue(problem.contains("misc.json"), problem);
    }

    @Test
    @DisplayName("a conflict does not affect other bundles in the same files")
    void conflictDoesNotPoisonSiblings() {
        // A stray duplicate should cost exactly one bundle, not a whole file's worth.
        BundleRegistry.LoadReport report = BundleRegistry.mergeSources(
                BundleRegistry.source("a.json", bundles("shared", "keep_a")),
                BundleRegistry.source("b.json", bundles("shared", "keep_b")));

        assertNotNull(report.getLoaded().get("keep_a"));
        assertNotNull(report.getLoaded().get("keep_b"));
    }

    @Test
    @DisplayName("merging nothing yields nothing without complaint")
    void mergingNothingIsClean() {
        BundleRegistry.LoadReport report = BundleRegistry.mergeSources();
        assertTrue(report.getLoaded().isEmpty());
        assertFalse(report.hasProblems());
    }

    // --- Directory loading ----------------------------------------------------------

    private static void write(File dir, String relativePath, String content) throws IOException {
        File file = new File(dir, relativePath);
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), content.getBytes(Charset.forName("UTF-8")));
    }

    @Test
    @DisplayName("loads json files recursively, so bundles can be organised in folders")
    void loadsRecursively(@TempDir File dir) throws Exception {
        write(dir, "top.json", "{\"top\": {\"commands\": [\"say a\"]}}");
        write(dir, "nested/deep.json", "{\"deep\": {\"commands\": [\"say b\"]}}");
        write(dir, "nested/deeper/deepest.json", "{\"deepest\": {\"commands\": [\"say c\"]}}");

        BundleRegistry registry = new BundleRegistry();
        BundleRegistry.LoadReport report = registry.loadFrom(dir);

        assertEquals(3, report.getLoaded().size());
        // Each name asserted, not just the count. A count of three would still pass if the
        // walker found the top level file three times and never descended at all.
        assertTrue(registry.contains("top"));
        assertTrue(registry.contains("deep"));
        assertTrue(registry.contains("deepest"));
    }

    @Test
    @DisplayName("a malformed file does not prevent valid files from loading")
    void malformedFileDoesNotBlockOthers(@TempDir File dir) throws Exception {
        // REQ-020. Partial success is deliberate: a toolkit that refuses to load anything over
        // one typo is useless for diagnosing whatever was actually being tested.
        write(dir, "good1.json", "{\"good1\": {\"commands\": [\"say a\"]}}");
        write(dir, "broken.json", "{ not json at all");
        write(dir, "good2.json", "{\"good2\": {\"commands\": [\"say b\"]}}");

        BundleRegistry registry = new BundleRegistry();
        BundleRegistry.LoadReport report = registry.loadFrom(dir);

        assertEquals(2, report.getLoaded().size());
        assertTrue(registry.contains("good1"));
        assertTrue(registry.contains("good2"));
        assertEquals(1, report.getProblems().size());
        assertTrue(report.getProblems().get(0).contains("broken.json"), report.getProblems().get(0));
    }

    @Test
    @DisplayName("a load problem names the file it came from")
    void problemNamesTheFile(@TempDir File dir) throws Exception {
        write(dir, "nested/bad.json", "{\"b\": {\"commands\": [{\"delayTicks\": 5}]}}");

        BundleRegistry.LoadReport report = new BundleRegistry().loadFrom(dir);
        String problem = report.getProblems().get(0);
        assertTrue(problem.contains("nested/bad.json"), problem);
        assertTrue(problem.contains("command"), problem);
    }

    @Test
    @DisplayName("non-json files are ignored")
    void nonJsonFilesIgnored(@TempDir File dir) throws Exception {
        write(dir, "real.json", "{\"real\": {\"commands\": [\"say a\"]}}");
        write(dir, "notes.txt", "this is not a bundle");
        write(dir, "backup.json.bak", "{ garbage");

        BundleRegistry.LoadReport report = new BundleRegistry().loadFrom(dir);
        assertEquals(1, report.getLoaded().size());
        assertFalse(report.hasProblems());
    }

    @Test
    @DisplayName("an absent or empty directory loads nothing without complaint")
    void absentDirectoryIsNotAnError(@TempDir File dir) {
        BundleRegistry registry = new BundleRegistry();

        assertTrue(registry.loadFrom(new File(dir, "does_not_exist")).getLoaded().isEmpty());
        assertFalse(registry.loadFrom(dir).hasProblems());
        assertTrue(registry.loadFrom(null).getLoaded().isEmpty());
    }

    @Test
    @DisplayName("reloading replaces the previous contents rather than accumulating")
    void reloadReplacesContents(@TempDir File dir) throws Exception {
        write(dir, "a.json", "{\"first\": {\"commands\": [\"say a\"]}}");
        BundleRegistry registry = new BundleRegistry();
        registry.loadFrom(dir);
        assertTrue(registry.contains("first"));

        new File(dir, "a.json").delete();
        write(dir, "b.json", "{\"second\": {\"commands\": [\"say b\"]}}");
        registry.loadFrom(dir);

        assertFalse(registry.contains("first"), "a deleted bundle must not survive a reload");
        assertTrue(registry.contains("second"));
        assertEquals(1, registry.size());
    }

    @Test
    @DisplayName("loading does not log; the caller decides when problems are reported")
    void loadingDoesNotLogDirectly(@TempDir File dir) throws Exception {
        // REQ-111 requires the startup summary to carry the bundle count AND to precede error
        // records. Both are only possible if loading holds its problems back for the caller.
        write(dir, "bad1.json", "{ broken");
        write(dir, "bad2.json", "{ also broken");

        BundleRegistry.LoadReport report = new BundleRegistry().loadFrom(dir);

        assertEquals(2, report.getProblems().size());
        assertTrue(ToolkitLog.capturedForTesting().isEmpty(),
                "loadFrom must not write records itself");
    }

    @Test
    @DisplayName("problems are reported individually, never summarised away")
    void problemsReachTheLogWhenReported(@TempDir File dir) throws Exception {
        write(dir, "bad1.json", "{ broken");
        write(dir, "bad2.json", "{ also broken");

        BundleRegistry.LoadReport report = new BundleRegistry().loadFrom(dir);
        BundleRegistry.reportProblems(report);

        java.util.List<String> records = ToolkitLog.capturedForTesting();
        assertEquals(2, records.size());
        assertTrue(records.get(0).contains("[ERROR]"), records.get(0));
    }

    @Test
    @DisplayName("a bundle's own command count is reported accurately")
    void commandCountIsAccurate(@TempDir File dir) throws Exception {
        // Feeds the list output, where a wrong count would quietly misrepresent a file.
        write(dir, "counts.json",
                "{\"one\": {\"commands\": [\"a\"]},"
                        + "\"three\": {\"commands\": [\"a\", \"b\", \"c\"]},"
                        + "\"none\": {\"commands\": []}}");

        BundleRegistry registry = new BundleRegistry();
        registry.loadFrom(dir);

        assertEquals(1, registry.get("one").size());
        assertEquals(3, registry.get("three").size());
        assertEquals(0, registry.get("none").size());
    }

    @Test
    @DisplayName("load order is stable, so conflict reports do not vary between runs")
    void loadOrderIsStable(@TempDir File dir) throws Exception {
        write(dir, "zebra.json", "{\"z\": {\"commands\": [\"say z\"]}}");
        write(dir, "alpha.json", "{\"a\": {\"commands\": [\"say a\"]}}");
        write(dir, "middle.json", "{\"m\": {\"commands\": [\"say m\"]}}");

        BundleRegistry registry = new BundleRegistry();
        registry.loadFrom(dir);

        // The order asserted outright, not merely compared against itself. Two identical reads
        // of the same unsorted directory listing would agree with each other and still be the
        // arbitrary order the filesystem happened to return.
        assertEquals("[a, m, z]", registry.names().toString(),
                "files load in sorted name order: alpha, middle, zebra");

        registry.loadFrom(dir);
        assertEquals("[a, m, z]", registry.names().toString());
    }
}
