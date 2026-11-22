package com.mahghuuuls.agenttesttoolkit.net;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A server drops a whole report that carries a field name it does not know, so the target's
 * {@code alive} and {@code health} go only to a server that said hello with 1.10.0 or later.
 */
class ReportFieldsTest {

    private static Map<String, String> target() {
        Map<String, String> fields = new LinkedHashMap<String, String>();
        fields.put("target", "entity");
        fields.put("entityUuid", "11111111-2222-3333-4444-555555555555");
        fields.put("alive", "true");
        fields.put("health", "20.00");
        fields.put("held", "empty");
        return fields;
    }

    @Test
    @DisplayName("a 1.10.0 server gets alive and health; an older one, or none, gets the rest unchanged")
    void newFieldsOnlyToNewServers() {
        assertEquals(target(), ClientActionReport.fieldsFor("1.10.0", target()));
        for (String older : new String[] {"1.9.0", "1.7.0", null}) {
            Map<String, String> sent = ClientActionReport.fieldsFor(older, target());
            assertFalse(sent.containsKey("alive"), String.valueOf(older));
            assertFalse(sent.containsKey("health"), String.valueOf(older));
            assertEquals("11111111-2222-3333-4444-555555555555", sent.get("entityUuid"));
            assertEquals(3, sent.size());
        }
    }

    @Test
    @DisplayName("a 1.10.0 server accepts a report with alive and health")
    void newServerAcceptsThem() {
        ClientActionReport report = new ClientActionReport("attack", "done", "", "none", "", "0123456789abcdef0123456789abcdef", 5L)
                .withFields(target());
        assertTrue(report.isValid());
    }
}
