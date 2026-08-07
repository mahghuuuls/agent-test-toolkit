package com.mahghuuuls.agenttesttoolkit.env;

import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.LoggingCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The capabilities report.
 *
 * <p>The property under test is not the wording, it is that nothing here is written by hand.
 * A capabilities command that can drift from the build is worse than not having one: it stays
 * confidently wrong after a feature is removed, and an agent has no independent way to check
 * it. These cases exist to make drift fail the build instead of misleading a reader.
 */
class CapabilitiesTest {

    private static String render(java.util.Collection<String> commands,
                                 java.util.Collection<String> inspectionTypes) {
        LogRecord record = LogRecord.of(EventType.CAPABILITIES);
        Capabilities.record(record, "1.2.3", commands, inspectionTypes);
        return record.render();
    }

    @Test
    @DisplayName("every logging category the build defines is reported")
    void everyCategoryReported() {
        // Derived from the enum the logging gate itself consults, so a category added to the
        // build cannot be missing here and one removed cannot linger.
        String rendered = render(Arrays.asList("mark"), Arrays.asList("player"));

        for (LoggingCategory category : LoggingCategory.values()) {
            assertTrue(rendered.contains(category.getCategoryName()),
                    category.getCategoryName() + " missing from: " + rendered);
        }
        assertTrue(rendered.contains("loggingCategoryCount=" + LoggingCategory.values().length),
                rendered);
    }

    @Test
    @DisplayName("the category count matches the number of categories actually listed")
    void categoryCountMatchesList() {
        // Guards the failure that would otherwise be invisible: a count and a list that
        // disagree, where an agent would trust whichever it read first.
        String names = Capabilities.categoryNames();
        assertEquals(LoggingCategory.values().length, names.split(",").length, names);
    }

    @Test
    @DisplayName("commands come from the caller's live registry, not a fixed list")
    void commandsComeFromRegistry() {
        String rendered = render(Arrays.asList("alpha", "beta", "gamma"), Arrays.asList("player"));
        assertTrue(rendered.contains("commands=alpha,beta,gamma"), rendered);
        assertTrue(rendered.contains("commandCount=3"), rendered);
    }

    @Test
    @DisplayName("a command absent from the registry is absent from the report")
    void absentCommandNotReported() {
        // The direction that matters: a feature missing from the build must be reported
        // missing, and a hand-maintained list would keep claiming it.
        String rendered = render(Arrays.asList("mark"), Arrays.asList("player"));
        assertTrue(rendered.contains("commands=mark"), rendered);
        assertEquals(-1, rendered.indexOf("removed_feature"), rendered);
    }

    @Test
    @DisplayName("inspection types are reported as given")
    void inspectionTypesReported() {
        String rendered = render(Arrays.asList("inspect"),
                Arrays.asList("player", "entity", "block", "inventory"));
        assertTrue(rendered.contains("inspectionTypes=player,entity,block,inventory"), rendered);
    }

    @Test
    @DisplayName("the toolkit version is reported")
    void versionReported() {
        assertTrue(render(Arrays.asList("mark"), Arrays.asList("player"))
                .contains("toolkitVersion=1.2.3"));
    }

    @Test
    @DisplayName("empty collections are handled without producing a malformed record")
    void emptyCollections() {
        String rendered = render(Collections.<String>emptyList(), Collections.<String>emptyList());
        assertTrue(rendered.contains("commandCount=0"), rendered);
        // Empty values are omitted rather than rendered as an empty field.
        assertEquals(-1, rendered.indexOf("commands="), rendered);
    }

    @Test
    @DisplayName("null collections do not throw")
    void nullCollections() {
        LogRecord record = LogRecord.of(EventType.CAPABILITIES);
        Capabilities.record(record, "1.0.0", null, null);
        assertTrue(record.render().contains("commandCount=0"));
    }
}
