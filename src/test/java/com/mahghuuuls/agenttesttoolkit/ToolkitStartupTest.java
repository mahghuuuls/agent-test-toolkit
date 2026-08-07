package com.mahghuuuls.agenttesttoolkit;

import com.mahghuuuls.agenttesttoolkit.bundle.BundleRegistry;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The startup output contract.
 *
 * <p>These exist because of a specific miss. An earlier implementation wrote a STARTUP record
 * before loading and another one after it, so an agent parsing the log would have seen two
 * beginnings for one launch and had no way to tell which counts were real. Thirty-odd tests
 * passed throughout, because the sequence lived inside a Forge lifecycle handler where nothing
 * could observe it.
 */
class ToolkitStartupTest {

    @BeforeEach
    void startCapture() {
        ToolkitLog.startCaptureForTesting();
    }

    @AfterEach
    void stopCapture() {
        ToolkitLog.stopCaptureForTesting();
    }

    private static BundleRegistry.LoadReport load(File dir, String... nameThenContent)
            throws IOException {
        for (int i = 0; i < nameThenContent.length; i += 2) {
            File file = new File(dir, nameThenContent[i]);
            file.getParentFile().mkdirs();
            Files.write(file.toPath(),
                    nameThenContent[i + 1].getBytes(Charset.forName("UTF-8")));
        }
        BundleRegistry.LoadReport report = new BundleRegistry().loadFrom(dir);
        // Loading itself must stay silent, otherwise the ordering below is not this class's
        // to control. Cleared so the assertions see only what announce wrote.
        ToolkitLog.startCaptureForTesting();
        return report;
    }

    private static int countStartupRecords(List<String> records) {
        int count = 0;
        for (String record : records) {
            if (record.contains("[STARTUP]")) {
                count++;
            }
        }
        return count;
    }

    @Test
    @DisplayName("a clean launch writes exactly one STARTUP record and nothing else")
    void cleanLaunchWritesOneRecord(@TempDir File dir) throws Exception {
        BundleRegistry.LoadReport bundles =
                load(dir, "a.json", "{\"one\": {\"commands\": [\"say a\"]}}");

        ToolkitStartup.announce("1.0.0", bundles, Collections.<String>emptyList());

        List<String> records = ToolkitLog.capturedForTesting();
        assertEquals(1, records.size(), records.toString());
        assertEquals(1, countStartupRecords(records));
    }

    @Test
    @DisplayName("still exactly one STARTUP record when everything goes wrong at once")
    void oneStartupRecordEvenWithProblems(@TempDir File dir) throws Exception {
        BundleRegistry.LoadReport bundles = load(dir,
                "good.json", "{\"one\": {\"commands\": [\"say a\"]}}",
                "broken.json", "{ not json");

        ToolkitStartup.announce("1.0.0", bundles,
                Arrays.asList("value adjusted: arena.defaultWidth requested=9999 effective=256"));

        List<String> records = ToolkitLog.capturedForTesting();
        assertEquals(1, countStartupRecords(records),
                "one launch must produce one beginning: " + records);
    }

    @Test
    @DisplayName("the STARTUP record comes first, before any problem is reported")
    void startupRecordComesFirst(@TempDir File dir) throws Exception {
        // Order is the readable half of the requirement. An agent scanning for the launch
        // boundary should not have to skip past errors belonging to that same launch.
        BundleRegistry.LoadReport bundles = load(dir, "broken.json", "{ not json");

        ToolkitStartup.announce("1.0.0", bundles, Arrays.asList("bad config"));

        List<String> records = ToolkitLog.capturedForTesting();
        assertTrue(records.get(0).contains("[STARTUP]"), records.toString());
        for (int i = 1; i < records.size(); i++) {
            assertTrue(records.get(i).contains("[ERROR]"), records.get(i));
        }
    }

    @Test
    @DisplayName("the single record carries the bundle count, so it cannot precede loading")
    void startupRecordCarriesCounts(@TempDir File dir) throws Exception {
        BundleRegistry.LoadReport bundles = load(dir,
                "a.json", "{\"one\": {\"commands\": []}, \"two\": {\"commands\": []}}",
                "broken.json", "{ not json");

        ToolkitStartup.announce("9.9.9", bundles, Collections.<String>emptyList());

        String startup = ToolkitLog.capturedForTesting().get(0);
        assertTrue(startup.contains("version=9.9.9"), startup);
        assertTrue(startup.contains("bundlesLoaded=2"), startup);
        assertTrue(startup.contains("bundleProblems=1"), startup);
        // Every logging category starts disabled, so this is a real zero.
        assertTrue(startup.contains("loggingCategoriesEnabled=0"), startup);
    }

    @Test
    @DisplayName("every problem is reported, from both loaders, none summarised away")
    void everyProblemIsReported(@TempDir File dir) throws Exception {
        BundleRegistry.LoadReport bundles = load(dir,
                "bad1.json", "{ broken",
                "bad2.json", "{ also broken");

        ToolkitStartup.announce("1.0.0", bundles, Arrays.asList("config a", "config b"));

        // Two bundle problems and two configuration problems, each its own record.
        assertEquals(5, ToolkitLog.capturedForTesting().size(),
                ToolkitLog.capturedForTesting().toString());
    }
}
