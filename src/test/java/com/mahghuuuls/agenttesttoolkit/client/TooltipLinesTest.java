package com.mahghuuuls.agenttesttoolkit.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A tooltip as record fields: the count, then the lines without formatting, bounded. */
class TooltipLinesTest {

    @Test
    @DisplayName("formatting codes and control characters are removed, the order and the count kept")
    void plainLines() {
        Map<String, String> fields = TooltipLines.fields(Arrays.asList(
                "\u00a7bDiamond Sword", "\u00a77\u00a79+7 Attack Damage", "Mod line\twith a tab", "\u00a7"));
        assertEquals("4", fields.get("lines"));
        assertEquals("Diamond Sword", fields.get("line1"));
        assertEquals("+7 Attack Damage", fields.get("line2"));
        assertEquals("Mod linewith a tab", fields.get("line3"));
        assertEquals("", fields.get("line4"), "a code at the very end");
        assertEquals("[lines, line1, line2, line3, line4]", fields.keySet().toString());
    }

    @Test
    @DisplayName("the client's record keeps up to 200 lines of up to 256 characters; the count says how many there were")
    void bounds() {
        List<String> many = new ArrayList<String>();
        StringBuilder longLine = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            longLine.append('x');
        }
        many.add(longLine.toString());
        for (int i = 2; i <= 250; i++) {
            many.add("line " + i);
        }
        Map<String, String> fields = TooltipLines.fields(many);
        assertEquals("250", fields.get("lines"));
        assertEquals(256, fields.get("line1").length());
        assertTrue(fields.containsKey("line200"));
        assertFalse(fields.containsKey("line201"));
    }

    @Test
    @DisplayName("the report to the server keeps the first twenty lines, the count and every other field")
    void forReport() {
        List<String> many = new ArrayList<String>();
        for (int i = 1; i <= 45; i++) {
            many.add("line " + i);
        }
        Map<String, String> fields = new java.util.LinkedHashMap<String, String>();
        fields.put("slot", "0");
        fields.putAll(TooltipLines.fields(many));
        fields.put("closed", "done");
        Map<String, String> report = TooltipLines.forReport(fields);
        assertEquals("45", report.get("lines"));
        assertTrue(report.containsKey("line20"));
        assertFalse(report.containsKey("line21"));
        assertFalse(report.containsKey("line45"));
        assertEquals("0", report.get("slot"));
        assertEquals("done", report.get("closed"));
        assertEquals(1 + 1 + 20 + 1, report.size());
        Map<String, String> noLines = new java.util.LinkedHashMap<String, String>();
        noLines.put("held", "minecraft:bow");
        assertEquals(noLines, TooltipLines.forReport(noLines));
    }
}
