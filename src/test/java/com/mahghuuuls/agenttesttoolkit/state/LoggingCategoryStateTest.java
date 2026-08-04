package com.mahghuuuls.agenttesttoolkit.state;

import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.LoggingCategory;
import com.mahghuuuls.agenttesttoolkit.observe.ObserverGate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Logging category state transitions and the category vocabulary.
 *
 * <p>These are the parts of IMP-005 reachable without a running game. The handler itself needs
 * Forge to fire an event, so its verification is manual, but everything the handler consults
 * before doing any work is covered here.
 */
class LoggingCategoryStateTest {

    @BeforeEach
    @AfterEach
    void reset() {
        ToolkitState.resetForTesting();
    }

    @Test
    @DisplayName("no category is enabled in a fresh process")
    void freshProcessHasNothingEnabled() {
        // REQ-035. The resting state must be silent, so an unexplained log is never the
        // toolkit's fault by default.
        assertTrue(ToolkitState.getEnabledCategories().isEmpty());
        for (LoggingCategory category : LoggingCategory.values()) {
            assertFalse(ToolkitState.isEnabled(category), category.getCategoryName());
        }
    }

    @Test
    @DisplayName("enable and disable toggle a single category")
    void enableAndDisable() {
        assertTrue(ToolkitState.enable(LoggingCategory.BLOCK_PLACE));
        assertTrue(ToolkitState.isEnabled(LoggingCategory.BLOCK_PLACE));
        assertFalse(ToolkitState.isEnabled(LoggingCategory.BLOCK_BREAK));

        assertTrue(ToolkitState.disable(LoggingCategory.BLOCK_PLACE));
        assertFalse(ToolkitState.isEnabled(LoggingCategory.BLOCK_PLACE));
    }

    @Test
    @DisplayName("repeating an operation reports that nothing changed")
    void repeatedOperationsReportNoChange() {
        // The command surface distinguishes "enabled" from "was already enabled", which tells
        // an agent whether its assumption about current state was right.
        assertTrue(ToolkitState.enable(LoggingCategory.ENTITY_DAMAGE));
        assertFalse(ToolkitState.enable(LoggingCategory.ENTITY_DAMAGE));

        assertTrue(ToolkitState.disable(LoggingCategory.ENTITY_DAMAGE));
        assertFalse(ToolkitState.disable(LoggingCategory.ENTITY_DAMAGE));
    }

    @Test
    @DisplayName("disableAll clears every category and reports the count")
    void disableAllClearsEverything() {
        ToolkitState.enable(LoggingCategory.BLOCK_PLACE);
        ToolkitState.enable(LoggingCategory.ENTITY_DEATH);
        ToolkitState.enable(LoggingCategory.ITEM_USE);

        assertEquals(3, ToolkitState.disableAll());
        assertTrue(ToolkitState.getEnabledCategories().isEmpty());
        assertEquals(0, ToolkitState.disableAll());
    }

    @Test
    @DisplayName("the enabled view is immutable")
    void enabledViewIsImmutable() {
        // Handed out on every status call. A caller mutating it would corrupt diagnostic
        // state from outside the one class that owns it.
        ToolkitState.enable(LoggingCategory.BLOCK_PLACE);
        Set<LoggingCategory> view = ToolkitState.getEnabledCategories();
        assertThrows(UnsupportedOperationException.class, () -> view.add(LoggingCategory.ITEM_USE));
    }

    @Test
    @DisplayName("categories are independent of one another")
    void categoriesAreIndependent() {
        ToolkitState.enable(LoggingCategory.BLOCK_PLACE);
        ToolkitState.enable(LoggingCategory.BLOCK_BREAK);
        ToolkitState.disable(LoggingCategory.BLOCK_PLACE);

        assertFalse(ToolkitState.isEnabled(LoggingCategory.BLOCK_PLACE));
        assertTrue(ToolkitState.isEnabled(LoggingCategory.BLOCK_BREAK));
    }

    @Test
    @DisplayName("all eight categories from the requirement exist and are addressable by name")
    void allRequiredCategoriesExist() {
        String[] required = {
                "block_place", "block_break", "entity_spawn", "entity_death",
                "entity_damage", "player_interaction", "entity_interaction", "item_use"
        };
        assertEquals(required.length, LoggingCategory.values().length);
        for (String name : required) {
            assertNotNull(LoggingCategory.byName(name), name);
        }
    }

    @Test
    @DisplayName("category lookup is case insensitive and rejects unknown names")
    void lookupIsCaseInsensitiveAndRejectsUnknown() {
        assertEquals(LoggingCategory.BLOCK_PLACE, LoggingCategory.byName("BLOCK_PLACE"));
        assertEquals(LoggingCategory.BLOCK_PLACE, LoggingCategory.byName("Block_Place"));
        assertNull(LoggingCategory.byName("banana"));
        assertNull(LoggingCategory.byName(null));
    }

    @Test
    @DisplayName("every category maps to an event type in the closed vocabulary")
    void everyCategoryMapsToAVocabularyEntry() {
        for (LoggingCategory category : LoggingCategory.values()) {
            EventType type = category.getEventType();
            assertNotNull(type, category.getCategoryName());
            // Guards against a category being wired to an event name that does not exist in
            // the fixed vocabulary REQ-034 defines.
            assertEquals(type, EventType.valueOf(type.name()));
        }
    }

    @Test
    @DisplayName("session state and category state are independent")
    void sessionAndCategoryStateAreIndependent() {
        ToolkitState.enable(LoggingCategory.BLOCK_PLACE);
        assertFalse(ToolkitState.hasActiveSession());
        assertTrue(ToolkitState.isEnabled(LoggingCategory.BLOCK_PLACE));
    }

    // --- Observer gating ------------------------------------------------------------
    // Extracted from the observer after independent review, so the rule every handler
    // applies before doing any work is checkable without a Forge event.

    @Test
    @DisplayName("a disabled category is not recorded on either side")
    void disabledCategoryIsNeverRecorded() {
        assertFalse(ObserverGate.shouldRecord(LoggingCategory.BLOCK_PLACE, false));
        assertFalse(ObserverGate.shouldRecord(LoggingCategory.BLOCK_PLACE, true));
    }

    @Test
    @DisplayName("an enabled category is recorded on the server and never on the client")
    void enabledCategoryIsServerSideOnly() {
        // REQ-041. In single player both logical sides share a JVM, so without the side check
        // one placement would produce two records.
        ToolkitState.enable(LoggingCategory.BLOCK_PLACE);
        assertTrue(ObserverGate.shouldRecord(LoggingCategory.BLOCK_PLACE, false));
        assertFalse(ObserverGate.shouldRecord(LoggingCategory.BLOCK_PLACE, true));
    }

    @Test
    @DisplayName("gating is per category, not global")
    void gatingIsPerCategory() {
        ToolkitState.enable(LoggingCategory.BLOCK_PLACE);
        assertTrue(ObserverGate.shouldRecord(LoggingCategory.BLOCK_PLACE, false));
        assertFalse(ObserverGate.shouldRecord(LoggingCategory.BLOCK_BREAK, false));
    }

    // --- Session stamping -----------------------------------------------------------
    // Moved from `session` into `state` so observers can stamp records without violating
    // dependency rule 3. Covered here because that is where it now lives.

    @Test
    @DisplayName("session stamping adds nothing when no session is active")
    void sessionStampIsSilentWithoutSession() {
        String out = SessionStamp.apply(LogRecord.of(EventType.BLOCK_PLACE)).render();
        assertEquals("[DevToolkit][BLOCK_PLACE]", out);
    }

    @Test
    @DisplayName("session stamping adds name and tick when a session is active")
    void sessionStampAddsFields() {
        ToolkitState.setActiveSession(new DiagnosticSession("observer_test"));
        String out = SessionStamp.apply(LogRecord.of(EventType.BLOCK_PLACE)).render();
        assertEquals("[DevToolkit][BLOCK_PLACE] session=observer_test sessionTick=0", out);
    }
}
