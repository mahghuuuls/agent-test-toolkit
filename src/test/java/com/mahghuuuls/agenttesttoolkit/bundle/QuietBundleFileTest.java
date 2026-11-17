package com.mahghuuuls.agenttesttoolkit.bundle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** A bundle file's {@code quiet}, and that it leaves the bundle's hash alone. */
class QuietBundleFileTest {

    private static BundleDefinition parse(String quiet) throws BundleParseException {
        String field = quiet == null ? "" : "\"quiet\": " + quiet + ", ";
        return BundleParser.parse("{\"b\": {" + field + "\"commands\": [\"tp @s 0 64 0\"]}}").get("b");
    }

    @Test
    @DisplayName("quiet is true, false, or not said")
    void parsed() throws BundleParseException {
        assertEquals(Boolean.TRUE, parse("true").getQuiet());
        assertEquals(Boolean.FALSE, parse("false").getQuiet());
        assertNull(parse(null).getQuiet());
    }

    @Test
    @DisplayName("quiet that is not true or false fails the file, naming the bundle")
    void refused() {
        BundleParseException e = assertThrows(BundleParseException.class, () -> parse("\"yes\""));
        assertEquals(true, e.getMessage().contains("bundle 'b': quiet"), e.getMessage());
    }

    @Test
    @DisplayName("quiet changes what the chat shows, not what the bundle does, so the hash is the same")
    void hashUnchanged() throws BundleParseException {
        assertEquals(parse(null).getHash(), parse("true").getHash());
    }
}
