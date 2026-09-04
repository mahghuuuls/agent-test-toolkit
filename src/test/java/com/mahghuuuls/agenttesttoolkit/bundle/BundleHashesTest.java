package com.mahghuuuls.agenttesttoolkit.bundle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Provenance hashes.
 *
 * <p>The property under test is the one evidence tooling depends on: a harmless edit leaves
 * the normalized hash alone, and a meaningful edit changes exactly the bundle it touched. The
 * raw hash is the opposite, by design, and one case pins that too so nobody later makes the
 * two agree.
 */
class BundleHashesTest {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private static final String COMPACT =
            "{\"a\":{\"commands\":[\"say hi\",{\"command\":\"say bye\",\"delayTicks\":20}]},"
            + "\"b\":{\"commands\":[\"say b\"]}}";

    private static final String SPACED =
            "{\n  \"a\": {\n    \"description\": \"greets\",\n    \"commands\": [\n"
            + "      \"say hi\",\n      { \"command\": \"say bye\", \"delayTicks\": 20 }\n    ]\n  },\n"
            + "  \"b\": { \"commands\": [ \"say b\" ] }\n}\n";

    private static final String EDITED =
            "{\"a\":{\"commands\":[\"say hi\",{\"command\":\"say bye\",\"delayTicks\":21}]},"
            + "\"b\":{\"commands\":[\"say b\"]}}";

    private static Map<String, BundleDefinition> parse(String json) throws BundleParseException {
        return BundleParser.parse(json);
    }

    @Test
    @DisplayName("sha256 of known bytes matches the published value")
    void sha256IsStandard() {
        // The empty input has a well-known digest; anyone can check it against sha256sum.
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                BundleHashes.sha256(new byte[0]));
        assertEquals(64, BundleHashes.sha256("abc".getBytes(UTF8)).length());
    }

    @Test
    @DisplayName("whitespace and description changes leave every normalized hash unchanged")
    void normalizedIgnoresFormatting() throws BundleParseException {
        Map<String, BundleDefinition> compact = parse(COMPACT);
        Map<String, BundleDefinition> spaced = parse(SPACED);
        assertEquals(compact.get("a").getHash(), spaced.get("a").getHash());
        assertEquals(compact.get("b").getHash(), spaced.get("b").getHash());
        // While the raw file hash does change, which is its job.
        assertNotEquals(BundleHashes.sha256(COMPACT.getBytes(UTF8)),
                BundleHashes.sha256(SPACED.getBytes(UTF8)));
    }

    @Test
    @DisplayName("a command edit changes exactly the bundle it touched")
    void normalizedIsSensitiveToMeaning() throws BundleParseException {
        Map<String, BundleDefinition> before = parse(COMPACT);
        Map<String, BundleDefinition> after = parse(EDITED);
        assertNotEquals(before.get("a").getHash(), after.get("a").getHash());
        assertEquals(before.get("b").getHash(), after.get("b").getHash());
    }

    @Test
    @DisplayName("the rendering is versioned and length-prefixes every string")
    void normalizedShape() throws BundleParseException {
        String text = BundleHashes.normalized(parse(COMPACT).get("a"));
        assertTrue(text.startsWith("v" + BundleHashes.NORMALIZATION_VERSION + "\n"), text);
        assertTrue(text.contains("command=6:say hi\n"), text);
        assertTrue(text.contains("delayTicks=20\n"), text);
        assertTrue(text.contains("stopOnFailure=true\n"), text);
    }

    @Test
    @DisplayName("attaching a source keeps the normalized hash and records the file")
    void withSourceKeepsHash() throws BundleParseException {
        BundleDefinition bare = parse(COMPACT).get("a");
        BundleDefinition sourced = bare.withSource("dir/file.json", "abc123");
        assertEquals(bare.getHash(), sourced.getHash());
        assertEquals("dir/file.json", sourced.getSourceName());
        assertEquals("abc123", sourced.getSourceHash());
    }
}
