package com.mahghuuuls.agenttesttoolkit.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An item's tooltip as record fields, from the lines the game builds: the count, then each line
 * with its formatting codes removed. Pure, so the bounds are tested without a game.
 */
final class TooltipLines {

    /** At most this many lines are written into the client's record; {@code lines} still counts them all. */
    static final int MAX_LINES = 200;
    /**
     * At most this many go into the report to the server, so the report keeps within its field
     * limit and one client-to-server packet; the client's own record has the rest.
     */
    static final int MAX_REPORT_LINES = 20;
    /** And each at most this many characters. */
    static final int MAX_LINE_LENGTH = 256;

    /** The game's formatting code marker: it and the character after it set a colour or a style. */
    private static final char FORMAT = '§';

    private TooltipLines() {
    }

    /** {@code lines}, then {@code line1} and on, in order. */
    static Map<String, String> fields(List<String> lines) {
        Map<String, String> fields = new LinkedHashMap<String, String>();
        fields.put("lines", String.valueOf(lines.size()));
        for (int i = 0; i < lines.size() && i < MAX_LINES; i++) {
            String plain = plain(lines.get(i));
            fields.put("line" + (i + 1), plain.length() <= MAX_LINE_LENGTH ? plain : plain.substring(0, MAX_LINE_LENGTH));
        }
        return fields;
    }

    /**
     * The fields as the report to the server carries them: every field but the lines past
     * {@link #MAX_REPORT_LINES}. A map without lines comes back as it is.
     */
    static Map<String, String> forReport(Map<String, String> fields) {
        Map<String, String> report = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            String key = field.getKey();
            if (key.startsWith("line") && !"lines".equals(key) && lineNumber(key) > MAX_REPORT_LINES) {
                continue;
            }
            report.put(key, field.getValue());
        }
        return report;
    }

    private static int lineNumber(String key) {
        try {
            return Integer.parseInt(key.substring(4));
        } catch (NumberFormatException notALine) {
            return 0;
        }
    }

    /** The text without formatting codes or control characters. */
    static String plain(String line) {
        if (line == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(line.length());
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == FORMAT) {
                i++;
            } else if (!Character.isISOControl(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }
}
