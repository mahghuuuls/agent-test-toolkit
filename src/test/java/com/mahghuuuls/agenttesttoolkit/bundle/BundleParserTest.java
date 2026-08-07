package com.mahghuuuls.agenttesttoolkit.bundle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bundle file parsing.
 *
 * <p>Bundles are authored by an agent writing JSON, so a misparse is not a crash, it is a test
 * that quietly does the wrong thing. These cases exist to make malformed input fail loudly and
 * with a message that names what is wrong.
 */
class BundleParserTest {

    private static Map<String, BundleDefinition> parse(String json) throws BundleParseException {
        return BundleParser.parse(json);
    }

    @Test
    @DisplayName("parses the specification's own example shape")
    void parsesSpecificationExample() throws Exception {
        Map<String, BundleDefinition> parsed = parse(
                "{\"spell_damage_setup\": {"
                        + "\"description\": \"Prepare manual spell damage test\","
                        + "\"stopOnFailure\": true,"
                        + "\"commands\": ["
                        + "  \"gamemode creative @p\","
                        + "  \"devtool arena reset\","
                        + "  {\"command\": \"devtool mark SETUP_COMPLETE\", \"delayTicks\": 20}"
                        + "]}}");

        assertEquals(1, parsed.size());
        BundleDefinition bundle = parsed.get("spell_damage_setup");
        assertEquals("Prepare manual spell damage test", bundle.getDescription());
        assertTrue(bundle.isStopOnFailure());
        assertEquals(3, bundle.size());
        assertEquals("gamemode creative @p", bundle.getCommands().get(0).getCommand());
        assertEquals(0, bundle.getCommands().get(0).getDelayTicks());
        assertEquals(20, bundle.getCommands().get(2).getDelayTicks());
    }

    @Test
    @DisplayName("a file may define several bundles, and order is preserved")
    void severalBundlesPerFile() throws Exception {
        Map<String, BundleDefinition> parsed = parse(
                "{\"first\": {\"commands\": [\"say a\"]},"
                        + "\"second\": {\"commands\": [\"say b\"]},"
                        + "\"third\": {\"commands\": [\"say c\"]}}");

        assertEquals(3, parsed.size());
        assertEquals("[first, second, third]", parsed.keySet().toString());
    }

    @Test
    @DisplayName("stopOnFailure defaults to true when omitted")
    void stopOnFailureDefaultsTrue() throws Exception {
        // Setup bundles build on each other, so continuing past a failure usually
        // produces a half-prepared environment that wastes the next test run.
        BundleDefinition bundle = parse("{\"b\": {\"commands\": [\"say a\"]}}").get("b");
        assertTrue(bundle.isStopOnFailure());
    }

    @Test
    @DisplayName("stopOnFailure can be turned off explicitly")
    void stopOnFailureCanBeDisabled() throws Exception {
        BundleDefinition bundle =
                parse("{\"b\": {\"stopOnFailure\": false, \"commands\": [\"say a\"]}}").get("b");
        assertFalse(bundle.isStopOnFailure());
    }

    @Test
    @DisplayName("description is optional")
    void descriptionIsOptional() throws Exception {
        assertNull(parse("{\"b\": {\"commands\": [\"say a\"]}}").get("b").getDescription());
    }

    @Test
    @DisplayName("a command may be a bare string or an object with a delay")
    void commandMayBeStringOrObject() throws Exception {
        BundleDefinition bundle = parse(
                "{\"b\": {\"commands\": [\"plain\", {\"command\": \"delayed\", \"delayTicks\": 5}]}}")
                .get("b");
        assertEquals("plain", bundle.getCommands().get(0).getCommand());
        assertEquals(0, bundle.getCommands().get(0).getDelayTicks());
        assertEquals("delayed", bundle.getCommands().get(1).getCommand());
        assertEquals(5, bundle.getCommands().get(1).getDelayTicks());
    }

    @Test
    @DisplayName("an object command without delayTicks defaults to no delay")
    void objectCommandWithoutDelay() throws Exception {
        BundleDefinition bundle = parse("{\"b\": {\"commands\": [{\"command\": \"x\"}]}}").get("b");
        assertEquals(0, bundle.getCommands().get(0).getDelayTicks());
    }

    @Test
    @DisplayName("an empty command list is allowed")
    void emptyCommandListIsAllowed() throws Exception {
        // Useless but harmless, and rejecting it would be a rule the specification never set.
        assertEquals(0, parse("{\"b\": {\"commands\": []}}").get("b").size());
    }

    // --- Failure cases. Each message must name what is wrong. ------------------------

    @Test
    @DisplayName("invalid JSON is rejected with a message")
    void invalidJsonRejected() {
        BundleParseException e = assertThrows(BundleParseException.class,
                () -> parse("{ this is not json"));
        assertTrue(e.getMessage().toLowerCase().contains("json"), e.getMessage());
    }

    @Test
    @DisplayName("an empty file is rejected")
    void emptyFileRejected() {
        assertThrows(BundleParseException.class, () -> parse(""));
        assertThrows(BundleParseException.class, () -> parse("   "));
        assertThrows(BundleParseException.class, () -> parse(null));
    }

    @Test
    @DisplayName("a top level array is rejected, since names come from object keys")
    void topLevelArrayRejected() {
        BundleParseException e = assertThrows(BundleParseException.class,
                () -> parse("[{\"commands\": []}]"));
        assertTrue(e.getMessage().contains("top level"), e.getMessage());
    }

    @Test
    @DisplayName("a missing commands array is rejected and the bundle is named")
    void missingCommandsRejected() {
        BundleParseException e = assertThrows(BundleParseException.class,
                () -> parse("{\"my_bundle\": {\"description\": \"x\"}}"));
        assertTrue(e.getMessage().contains("my_bundle"), e.getMessage());
        assertTrue(e.getMessage().contains("commands"), e.getMessage());
    }

    @Test
    @DisplayName("commands must be an array")
    void commandsMustBeArray() {
        BundleParseException e = assertThrows(BundleParseException.class,
                () -> parse("{\"b\": {\"commands\": \"say a\"}}"));
        assertTrue(e.getMessage().contains("array"), e.getMessage());
    }

    @Test
    @DisplayName("a malformed entry names the bundle and its index")
    void malformedEntryNamesPosition() {
        // With several commands in a bundle, "failed to parse" alone would leave an agent
        // rereading the whole file.
        BundleParseException e = assertThrows(BundleParseException.class,
                () -> parse("{\"b\": {\"commands\": [\"ok\", \"ok\", 42]}}"));
        assertTrue(e.getMessage().contains("'b'"), e.getMessage());
        assertTrue(e.getMessage().contains("2"), e.getMessage());
    }

    @Test
    @DisplayName("an object command without a command field is rejected")
    void objectCommandWithoutCommandField() {
        BundleParseException e = assertThrows(BundleParseException.class,
                () -> parse("{\"b\": {\"commands\": [{\"delayTicks\": 5}]}}"));
        assertTrue(e.getMessage().contains("command"), e.getMessage());
    }

    @Test
    @DisplayName("a blank command is rejected")
    void blankCommandRejected() {
        assertThrows(BundleParseException.class, () -> parse("{\"b\": {\"commands\": [\"\"]}}"));
        assertThrows(BundleParseException.class, () -> parse("{\"b\": {\"commands\": [\"   \"]}}"));
    }

    @Test
    @DisplayName("a negative delay is rejected rather than silently clamped")
    void negativeDelayRejected() {
        // Clamping would hide an authoring mistake behind behaviour that looks deliberate.
        BundleParseException e = assertThrows(BundleParseException.class,
                () -> parse("{\"b\": {\"commands\": [{\"command\": \"x\", \"delayTicks\": -5}]}}"));
        assertTrue(e.getMessage().contains("negative"), e.getMessage());
    }

    @Test
    @DisplayName("wrong types for optional fields are rejected")
    void wrongTypesRejected() {
        assertThrows(BundleParseException.class,
                () -> parse("{\"b\": {\"description\": 5, \"commands\": []}}"));
        assertThrows(BundleParseException.class,
                () -> parse("{\"b\": {\"stopOnFailure\": \"yes\", \"commands\": []}}"));
        assertThrows(BundleParseException.class,
                () -> parse("{\"b\": {\"commands\": [{\"command\": \"x\", \"delayTicks\": \"soon\"}]}}"));
    }

    @Test
    @DisplayName("the same bundle name twice in one file is rejected, not silently deduplicated")
    void duplicateNameWithinOneFileRejected() {
        // Found in review. Gson's object model is map backed, so a repeated key overwrites the
        // earlier value before this parser ever sees it: one definition would vanish with no
        // error at all. This is refused across files; the same copy-paste mistake inside
        // one file was invisible.
        BundleParseException e = assertThrows(BundleParseException.class,
                () -> parse("{\"same\": {\"commands\": [\"first\"]},"
                        + "\"same\": {\"commands\": [\"second\"]}}"));
        assertTrue(e.getMessage().contains("same"), e.getMessage());
        assertTrue(e.getMessage().contains("more than once"), e.getMessage());
    }

    @Test
    @DisplayName("distinct names in one file are unaffected by the duplicate check")
    void distinctNamesAreNotFlagged() throws Exception {
        assertEquals(2, parse("{\"a\": {\"commands\": []}, \"b\": {\"commands\": []}}").size());
    }

    @Test
    @DisplayName("a leading byte order mark is tolerated")
    void byteOrderMarkTolerated() throws Exception {
        // Some Windows editors write one. It sits above the space character so trim() will not
        // remove it, and the parser error would point at an invisible character on line one.
        // This project has already lost a build cycle to a BOM once.
        String json = ((char) 0xFEFF) + "{\"b\": {\"commands\": [\"say a\"]}}";
        assertEquals(1, parse(json).size());
    }

    @Test
    @DisplayName("no scripting constructs are interpreted")
    void noScriptingConstructs() throws Exception {
        // A value that looks like a variable stays a literal string; it is passed to
        // the command handler unchanged rather than expanded.
        BundleDefinition bundle =
                parse("{\"b\": {\"commands\": [\"say ${player} $(x) %v%\"]}}").get("b");
        assertEquals("say ${player} $(x) %v%", bundle.getCommands().get(0).getCommand());
    }
}
