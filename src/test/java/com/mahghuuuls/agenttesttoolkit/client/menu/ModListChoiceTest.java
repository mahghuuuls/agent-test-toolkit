package com.mahghuuuls.agenttesttoolkit.client.menu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Where a mod is in a Mods list, how far to scroll to it, and how the kept filters are named. */
class ModListChoiceTest {

    @Test
    @DisplayName("the mod by its exact id in the list as shown")
    void index() {
        assertEquals(2, ModListChoice.indexOf(Arrays.asList("minecraft", "forge", "agenttesttoolkit"), "agenttesttoolkit"));
        assertEquals(-1, ModListChoice.indexOf(Arrays.asList("minecraft", "forge"), "agenttesttoolkit"));
        assertEquals(-1, ModListChoice.indexOf(Arrays.asList("AgentTestToolkit"), "agenttesttoolkit"), "case counts");
    }

    @Test
    @DisplayName("the scroll centres the entry, within what the list allows")
    void scroll() {
        // A list of 40 entries of 35, with 160 of view: the content is 1401 high.
        int content = 40 * 35 + 1;
        assertEquals(0.0F, ModListChoice.scrollFor(0, 35, 160, content), "the first entry needs no scroll");
        assertEquals(20 * 35 + 17.5F - 80.0F, ModListChoice.scrollFor(20, 35, 160, content));
        assertEquals(content - (160 - ModListChoice.BORDER), ModListChoice.scrollFor(39, 35, 160, content), 0.001F,
                "the last entry stops where the list stops");
        assertEquals(0.0F, ModListChoice.scrollFor(2, 35, 400, 3 * 35 + 1), "a short list does not scroll");
    }

    @Test
    @DisplayName("the kept filters named in one value, or none")
    void filters() {
        Map<String, Boolean> options = new LinkedHashMap<String, Boolean>();
        options.put("hideLibraries", Boolean.TRUE);
        options.put("hideChildMods", Boolean.FALSE);
        options.put("favouritesOnly", Boolean.TRUE);
        assertEquals("query:jei,hideLibraries,favouritesOnly", ModListChoice.filters(" jei ", options));
        assertEquals("hideLibraries,favouritesOnly", ModListChoice.filters("", options));
        Map<String, Boolean> none = new LinkedHashMap<String, Boolean>();
        none.put("hideLibraries", Boolean.FALSE);
        assertEquals("none", ModListChoice.filters(null, none));
    }
}
