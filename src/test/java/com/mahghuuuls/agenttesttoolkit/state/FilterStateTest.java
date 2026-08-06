package com.mahghuuuls.agenttesttoolkit.state;

import com.mahghuuuls.agenttesttoolkit.logging.LoggingCategory;
import com.mahghuuuls.agenttesttoolkit.logging.filter.Filter;
import com.mahghuuuls.agenttesttoolkit.logging.filter.RadiusFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How filters combine with the enabled/disabled state.
 *
 * <p>The combination is where this feature can go wrong invisibly. A category enabled with a
 * filter that is never consulted produces a log that is merely larger than requested, which
 * nobody notices; a filter consulted for a disabled category produces nothing, which looks
 * like the feature working.
 */
class FilterStateTest {

    private static final int OVERWORLD = 0;

    @BeforeEach
    @AfterEach
    void reset() {
        ToolkitState.resetForTesting();
    }

    private static Filter radius(double r) {
        return new RadiusFilter(OVERWORLD, 0, 64, 0, r);
    }

    @Test
    @DisplayName("with no filter, an enabled category records everywhere")
    void noFilterRecordsEverywhere() {
        ToolkitState.enable(LoggingCategory.BLOCK_PLACE);
        assertTrue(ToolkitState.shouldRecord(LoggingCategory.BLOCK_PLACE, OVERWORLD, 9999, 64, 9999));
    }

    @Test
    @DisplayName("a disabled category records nothing, filter or not")
    void disabledRecordsNothing() {
        ToolkitState.setFilter(LoggingCategory.BLOCK_PLACE, radius(100));
        assertFalse(ToolkitState.shouldRecord(LoggingCategory.BLOCK_PLACE, OVERWORLD, 0, 64, 0));
    }

    @Test
    @DisplayName("an enabled category with a filter records only what the filter admits")
    void filterNarrowsEnabledCategory() {
        ToolkitState.enable(LoggingCategory.BLOCK_PLACE);
        ToolkitState.setFilter(LoggingCategory.BLOCK_PLACE, radius(20));

        assertTrue(ToolkitState.shouldRecord(LoggingCategory.BLOCK_PLACE, OVERWORLD, 10, 64, 0));
        assertFalse(ToolkitState.shouldRecord(LoggingCategory.BLOCK_PLACE, OVERWORLD, 50, 64, 0));
    }

    @Test
    @DisplayName("applying a filter replaces the previous one rather than combining")
    void filterReplaces() {
        // REQ-047 forbids composition. Accumulating filters would make "why is this event
        // missing?" require reasoning about an expression, which is the question the toolkit
        // exists to make easy.
        Filter first = radius(5);
        Filter second = radius(50);
        ToolkitState.setFilter(LoggingCategory.BLOCK_PLACE, first);
        ToolkitState.setFilter(LoggingCategory.BLOCK_PLACE, second);

        assertSame(second, ToolkitState.getFilter(LoggingCategory.BLOCK_PLACE));
        ToolkitState.enable(LoggingCategory.BLOCK_PLACE);
        assertTrue(ToolkitState.shouldRecord(LoggingCategory.BLOCK_PLACE, OVERWORLD, 30, 64, 0),
                "the replaced narrow filter must not still apply");
    }

    @Test
    @DisplayName("filters are per category and do not bleed into each other")
    void filtersArePerCategory() {
        ToolkitState.enable(LoggingCategory.BLOCK_PLACE);
        ToolkitState.enable(LoggingCategory.ENTITY_DAMAGE);
        ToolkitState.setFilter(LoggingCategory.BLOCK_PLACE, radius(5));

        assertFalse(ToolkitState.shouldRecord(LoggingCategory.BLOCK_PLACE, OVERWORLD, 50, 64, 0));
        assertTrue(ToolkitState.shouldRecord(LoggingCategory.ENTITY_DAMAGE, OVERWORLD, 50, 64, 0));
        assertNull(ToolkitState.getFilter(LoggingCategory.ENTITY_DAMAGE));
    }

    @Test
    @DisplayName("disabling a category keeps its filter for when it is re-enabled")
    void disableKeepsFilter() {
        // Someone toggling a category mid-test expects to get back what they had, not a
        // silently widened one.
        ToolkitState.enable(LoggingCategory.BLOCK_PLACE);
        ToolkitState.setFilter(LoggingCategory.BLOCK_PLACE, radius(5));
        ToolkitState.disable(LoggingCategory.BLOCK_PLACE);

        assertNotNull(ToolkitState.getFilter(LoggingCategory.BLOCK_PLACE));
        ToolkitState.enable(LoggingCategory.BLOCK_PLACE);
        assertFalse(ToolkitState.shouldRecord(LoggingCategory.BLOCK_PLACE, OVERWORLD, 50, 64, 0));
    }

    @Test
    @DisplayName("all off clears filters as well as categories")
    void disableAllClearsFilters() {
        // "all off" means a clean slate. A filter surviving it would surprise the operator on
        // their next enable.
        ToolkitState.enable(LoggingCategory.BLOCK_PLACE);
        ToolkitState.setFilter(LoggingCategory.BLOCK_PLACE, radius(5));
        ToolkitState.disableAll();

        assertNull(ToolkitState.getFilter(LoggingCategory.BLOCK_PLACE));
        ToolkitState.enable(LoggingCategory.BLOCK_PLACE);
        assertTrue(ToolkitState.shouldRecord(LoggingCategory.BLOCK_PLACE, OVERWORLD, 9999, 64, 0));
    }

    @Test
    @DisplayName("a null filter removes any existing one")
    void nullFilterRemoves() {
        ToolkitState.setFilter(LoggingCategory.BLOCK_PLACE, radius(5));
        ToolkitState.setFilter(LoggingCategory.BLOCK_PLACE, null);
        assertNull(ToolkitState.getFilter(LoggingCategory.BLOCK_PLACE));
    }
}
