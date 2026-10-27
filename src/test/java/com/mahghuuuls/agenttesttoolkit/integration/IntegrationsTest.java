package com.mahghuuuls.agenttesttoolkit.integration;

import com.mahghuuuls.agenttesttoolkit.bundle.CommandOutcome;
import com.mahghuuuls.agenttesttoolkit.command.CommandOutcomes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The optional Baubles integration: slot naming, the absent case, and its failure keys. */
class IntegrationsTest {

    /** Baubles 1.5.2's types and slots, in its declared order. */
    private static Map<String, int[]> baubles152() {
        Map<String, int[]> types = new LinkedHashMap<String, int[]>();
        types.put("amulet", new int[] {0});
        types.put("ring", new int[] {1, 2});
        types.put("belt", new int[] {3});
        types.put("trinket", new int[] {0, 1, 2, 3, 4, 5, 6});
        types.put("head", new int[] {4});
        types.put("body", new int[] {5});
        types.put("charm", new int[] {6});
        return types;
    }

    @AfterEach
    void reset() {
        Integrations.setBaublesForTesting(null);
    }

    @Test
    @DisplayName("each slot is named by its specific type, never by trinket, which fits every slot")
    void slotNames() {
        Map<String, int[]> types = baubles152();
        String[] expected = {"amulet", "ring", "ring", "belt", "head", "body", "charm"};
        for (int slot = 0; slot < expected.length; slot++) {
            assertEquals(expected[slot], Integrations.slotTypeName(slot, types), "slot " + slot);
        }
        Map<String, int[]> trinketOnly = Collections.singletonMap("trinket", new int[] {7});
        assertEquals("trinket", Integrations.slotTypeName(7, trinketOnly));
        assertEquals("unknown", Integrations.slotTypeName(9, types));
    }

    @Test
    @DisplayName("without Baubles there is no bridge, and no Baubles class is touched")
    void absent() {
        assertNull(Integrations.init(false));
        assertNull(Integrations.baubles());
    }

    @Test
    @DisplayName("the bridge class named by Integrations exists, found without linking Baubles")
    void bridgeNameResolves() throws Exception {
        Class<?> impl = Class.forName(Integrations.BAUBLES_IMPL, false, IntegrationsTest.class.getClassLoader());
        assertTrue(BaublesBridge.class.isAssignableFrom(impl));
    }

    private static boolean baublesOnTestClasspath() {
        try {
            Class.forName("baubles.api.BaubleType", false, IntegrationsTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }

    @Test
    @DisplayName("Baubles reported loaded but its classes missing: no bridge, and the reason is given")
    void loadedButUnusable() {
        // Baubles is compile-only; with -Pdev_runtime_baubles=true it reaches the test runtime too,
        // and this case cannot happen, so it is skipped rather than failed.
        org.junit.jupiter.api.Assumptions.assumeFalse(baublesOnTestClasspath());
        String problem = Integrations.init(true);
        assertNotNull(problem);
        assertNull(Integrations.baubles());
    }

    @Test
    @DisplayName("the two failure keys are failures, tolerable when declared")
    void keys() {
        for (String key : Arrays.asList(CommandOutcomes.KEY_BAUBLES_ABSENT, CommandOutcomes.KEY_BAUBLES_REJECTED)) {
            assertTrue(key.startsWith("devtool.baubles."), key);
            assertFalse(CommandOutcomes.classify(0, key).succeeded(), key);
            CommandOutcome declared = CommandOutcomes.classify(0, key, Collections.singleton(key));
            assertTrue(declared.succeeded(), key);
        }
    }

    @Test
    @DisplayName("the two keys have chat translations")
    void keysTranslated() {
        java.io.InputStream in = IntegrationsTest.class.getResourceAsStream("/assets/agenttesttoolkit/lang/en_us.lang");
        java.util.Scanner scanner = new java.util.Scanner(in, "UTF-8").useDelimiter("\\A");
        String text = scanner.hasNext() ? scanner.next() : "";
        scanner.close();
        assertTrue(text.contains("\ndevtool.baubles.absent=[DevToolkit] "));
        assertTrue(text.contains("\ndevtool.baubles.rejected=[DevToolkit] "));
        assertTrue(text.contains("\ndevtool.screenshot.noClient=[DevToolkit] "));
    }
}
