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
 * named there: every record type, every toolkit failure key, and every reason a record or a
 * result file can carry. Checked over the source, so a new record, key or reason without
 * documentation fails the build.
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
    @DisplayName("the record-type counts written in AGENTS.md, docs/logging.md and README.md match the vocabulary, and docs/logging.md names every type")
    void recordTypeCountsAndHumanDocs() throws IOException {
        int count = EventType.values().length;
        String agents = read(new File("AGENTS.md"));
        String logging = read(new File("docs/logging.md"));
        String readme = read(new File("README.md"));
        assertTrue(agents.contains("There are " + count + " record types"), "AGENTS.md count");
        assertTrue(logging.contains(count + " in total."), "docs/logging.md count");
        assertTrue(readme.contains("all " + count + " record types"), "README.md count");
        List<String> missing = new ArrayList<String>();
        for (EventType type : EventType.values()) {
            if (!logging.contains("`" + type.name() + "`")) {
                missing.add(type.name());
            }
        }
        assertEquals(new ArrayList<String>(), missing, "docs/logging.md");
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

    /**
     * The shapes the source writes a reason in. A reason is a plain word, not a key, so it is
     * found by where it is written: a {@code REASON_} constant, the value of a field named
     * {@code reason}, {@code reason=} text, the reason argument of the methods that write
     * refusals, failures and closings for the inbox, the client request channel and client
     * actions, and the words returned by a method whose name says it gives a refusal. A
     * {@code detail}, such as which create option was wrong, is not a reason and is not scanned.
     * A reason written in another shape is not found, so a new one goes in one of these.
     */
    private static final Pattern[] REASON_SHAPES = {
            Pattern.compile("REASON_[A-Z_]+\\s*=\\s*\"([A-Za-z]+)\""),
            Pattern.compile("\"reason\"\\s*,\\s*\"([A-Za-z]+)\""),
            Pattern.compile("reason=([A-Za-z]{2,})"),
            Pattern.compile("recordRefusal\\([^,;]+,[^,;]+,\\s*\"([A-Za-z]+)\""),
            Pattern.compile("InboxResult\\.(?:refused|failed)\\([^,;]+,\\s*\"([A-Za-z]+)\""),
            Pattern.compile("(?<![A-Za-z])close\\(\\s*\"([A-Za-z]+)\""),
            Pattern.compile("(?:record|write)\\([^,;]+,\\s*\"(?:refused|failed)\"\\s*,\\s*[^,;]+,\\s*\"([A-Za-z]+)\""),
            Pattern.compile("refuse\\(\\s*files\\s*,\\s*request\\s*,\\s*\"([A-Za-z]+)\""),
    };

    /** A method that returns the reason for a refusal, such as {@code respawnRefusal} or {@code refusalFor}. */
    private static final Pattern REFUSAL_METHOD =
            Pattern.compile("String\\s+[A-Za-z]*[Rr]efusal[A-Za-z]*\\s*\\([^)]*\\)\\s*\\{");
    private static final Pattern RETURNED_WORD = Pattern.compile("return\\s+\"([A-Za-z]+)\"\\s*;");

    /** A step's outcome and reason, where the reason may be a choice between two words. */
    private static final Pattern FINISH_REASON =
            Pattern.compile("finish\\([^,;]+,\\s*\"(?:failed|refused)\"\\s*,\\s*([^,;]+)");
    private static final Pattern WORD = Pattern.compile("\"([A-Za-z]+)\"");

    static TreeSet<String> reasonsIn(String source) {
        TreeSet<String> reasons = new TreeSet<String>();
        for (Pattern shape : REASON_SHAPES) {
            Matcher m = shape.matcher(source);
            while (m.find()) {
                reasons.add(m.group(1));
            }
        }
        Matcher finish = FINISH_REASON.matcher(source);
        while (finish.find()) {
            Matcher word = WORD.matcher(finish.group(1));
            while (word.find()) {
                reasons.add(word.group(1));
            }
        }
        Matcher method = REFUSAL_METHOD.matcher(source);
        while (method.find()) {
            Matcher word = RETURNED_WORD.matcher(body(source, method.end()));
            while (word.find()) {
                reasons.add(word.group(1));
            }
        }
        return reasons;
    }

    /** The method body that starts after an opening brace at {@code start}, up to its closing brace. */
    private static String body(String source, int start) {
        int depth = 1;
        int i = start;
        while (depth > 0 && i < source.length()) {
            char c = source.charAt(i++);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            }
        }
        return source.substring(start, i);
    }

    /** Named as a word in backticks, or as a whole word after {@code reason=} in an example. */
    static List<String> undocumented(TreeSet<String> reasons, String agents) {
        List<String> missing = new ArrayList<String>();
        for (String reason : reasons) {
            boolean example = Pattern.compile("reason=" + Pattern.quote(reason) + "(?![A-Za-z])")
                    .matcher(agents).find();
            if (!agents.contains("`" + reason + "`") && !example) {
                missing.add(reason);
            }
        }
        return missing;
    }

    @Test
    @DisplayName("every reason in the source is named in AGENTS.md")
    void everyReasonDocumented() throws IOException {
        List<File> files = new ArrayList<File>();
        collect(new File("src/main/java"), files);
        TreeSet<String> reasons = new TreeSet<String>();
        for (File file : files) {
            reasons.addAll(reasonsIn(read(file)));
        }
        for (String known : new String[] {"localJoinNotHost", "placedBeforeStart", "notLocal",
                "localJoinNotOperator", "stillAlive", "connectFailed", "openForAnother", "tokenMismatch",
                "rejoinTimeout", "returnedOtherRoute", "wrongBundle", "changedHash", "notDeadYet",
                "noDeathScreen"}) {
            assertTrue(reasons.contains(known), "the scan found " + known + ": " + reasons);
        }
        assertEquals(new ArrayList<String>(), undocumented(reasons, read(new File("AGENTS.md"))));
    }

    @Test
    @DisplayName("a reason missing from the manual is reported by name")
    void missingReasonIsReported() {
        TreeSet<String> reasons = reasonsIn("refuse(files, request, \"notLocal\", null);\n"
                + "record.add(\"reason\", \"stillAlive\");");
        assertEquals(java.util.Arrays.asList("notLocal"), undocumented(reasons, "a `stillAlive` reason"));
        TreeSet<String> one = new TreeSet<String>(java.util.Arrays.asList("localJoin"));
        assertEquals(java.util.Arrays.asList("localJoin"), undocumented(one, "`reason=localJoinNotOperator`"),
                "a longer reason does not document a shorter one");
        assertEquals(new ArrayList<String>(), undocumented(one, "`INBOX action=opened reason=localJoin`"));
    }
}
