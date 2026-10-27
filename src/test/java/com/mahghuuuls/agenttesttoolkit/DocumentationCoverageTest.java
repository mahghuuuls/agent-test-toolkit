package com.mahghuuuls.agenttesttoolkit;

import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code AGENTS.md} is the agent's only manual, so everything an agent can meet in a log must be
 * named there: every record type and every toolkit failure key (REQ-195). Checked over the
 * source, so a new record or key without documentation fails the build.
 */
class DocumentationCoverageTest {

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), Charset.forName("UTF-8"));
    }

    private static void collect(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collect(child, out);
            } else if (child.getName().endsWith(".java")) {
                out.add(child);
            }
        }
    }

    @Test
    @DisplayName("every record type is named in AGENTS.md")
    void everyRecordTypeDocumented() throws IOException {
        String agents = read(new File("AGENTS.md"));
        List<String> missing = new ArrayList<String>();
        for (EventType type : EventType.values()) {
            if (!agents.contains("`" + type.name() + "`")) {
                missing.add(type.name());
            }
        }
        assertEquals(new ArrayList<String>(), missing);
    }

    @Test
    @DisplayName("every devtool. failure key in the source is named in AGENTS.md")
    void everyFailureKeyDocumented() throws IOException {
        List<File> files = new ArrayList<File>();
        collect(new File("src/main/java"), files);
        assertTrue(files.size() > 50, "the scan found the sources");
        Pattern key = Pattern.compile("\"(devtool\\.[a-z]+\\.[A-Za-z]+)\"");
        TreeSet<String> keys = new TreeSet<String>();
        for (File file : files) {
            Matcher m = key.matcher(read(file));
            while (m.find()) {
                keys.add(m.group(1));
            }
        }
        assertTrue(keys.size() >= 10, "the scan found the keys: " + keys);
        String agents = read(new File("AGENTS.md"));
        List<String> missing = new ArrayList<String>();
        for (String k : keys) {
            if (!agents.contains(k)) {
                missing.add(k);
            }
        }
        assertEquals(new ArrayList<String>(), missing);
    }
}
