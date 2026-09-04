package com.mahghuuuls.agenttesttoolkit.bundle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The placeholder lookup.
 *
 * <p>The boundaries are what matter: only four forms are known, an unknown one fails at
 * parse, a missing value fails the command with one key and an unsafe value with another,
 * and nothing is ever substituted partially. A placeholder that quietly became an empty
 * string would be the worst outcome, so that case is pinned explicitly.
 */
class PlaceholdersTest {

    private static final class Bound implements PlaceholderSource {
        @Override
        public String operatorName() {
            return "Developer";
        }

        @Override
        public String operatorUuid() {
            return "a01a21f5-c117-3c1f-b5d0-e055b927f733";
        }

        @Override
        public String operatorSelector() {
            return "@p[name=Developer]";
        }

        @Override
        public String fixtureUuid(String handle) {
            return "source".equals(handle) ? "0f01c58f-7f2e-4afd-860b-1e8dbd9e197b" : null;
        }
    }

    @Test
    @DisplayName("the four forms validate and anything else is named as unknown")
    void validation() {
        assertTrue(Placeholders.validate("gamemode creative ${operator.name}").isEmpty());
        assertTrue(Placeholders.validate("${operator.uuid} ${operator.selector}").isEmpty());
        assertTrue(Placeholders.validate("devtool nbt entity ${fixture.source.uuid}").isEmpty());
        assertTrue(Placeholders.validate("say no placeholders here").isEmpty());

        List<String> problems = Placeholders.validate("${operator.typo} and ${fixture..uuid} and ${x}");
        assertEquals(3, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("${operator.typo}"), problems.get(0));
        assertTrue(problems.get(1).contains("${fixture..uuid}"), problems.get(1));
        assertTrue(problems.get(2).contains("${x}"), problems.get(2));
    }

    @Test
    @DisplayName("a bound source fills every form, including inside SNBT quotes")
    void substitution() throws PlaceholderException {
        Bound source = new Bound();
        assertEquals("gamemode creative Developer",
                Placeholders.substitute("gamemode creative ${operator.name}", source));
        assertEquals("tp @p[name=Developer] ~ ~ ~",
                Placeholders.substitute("tp ${operator.selector} ~ ~ ~", source));
        assertEquals("summon minecraft:pig ~ ~ ~ {CustomName:\"Developer\"}",
                Placeholders.substitute("summon minecraft:pig ~ ~ ~ {CustomName:\"${operator.name}\"}", source));
        assertEquals("devtool nbt entity 0f01c58f-7f2e-4afd-860b-1e8dbd9e197b",
                Placeholders.substitute("devtool nbt entity ${fixture.source.uuid}", source));
        assertEquals("say plain", Placeholders.substitute("say plain", source));
    }

    @Test
    @DisplayName("a missing value fails with the unbound key and substitutes nothing")
    void unbound() {
        PlaceholderException e = assertThrows(PlaceholderException.class,
                () -> Placeholders.substitute("gamemode creative ${operator.name}", PlaceholderSource.EMPTY));
        assertEquals(PlaceholderException.KEY_UNBOUND, e.getKey());
        assertEquals("${operator.name}", e.getPlaceholder());

        PlaceholderException fixture = assertThrows(PlaceholderException.class,
                () -> Placeholders.substitute("kill ${fixture.missing.uuid}", new Bound()));
        assertEquals(PlaceholderException.KEY_UNBOUND, fixture.getKey());
    }

    @Test
    @DisplayName("a value with whitespace, a quote or a brace is never substituted")
    void unsafe() {
        PlaceholderSource evil = new PlaceholderSource() {
            @Override
            public String operatorName() {
                return "Two Words";
            }

            @Override
            public String operatorUuid() {
                return "abc\"}";
            }

            @Override
            public String operatorSelector() {
                return "";
            }

            @Override
            public String fixtureUuid(String handle) {
                return null;
            }
        };
        for (String command : new String[] {
                "say ${operator.name}", "say ${operator.uuid}", "say ${operator.selector}"}) {
            PlaceholderException e = assertThrows(PlaceholderException.class,
                    () -> Placeholders.substitute(command, evil), command);
            assertEquals(PlaceholderException.KEY_UNSAFE, e.getKey(), command);
        }
    }

    @Test
    @DisplayName("the parser refuses a file with an unknown placeholder and names it")
    void parserRejectsUnknown() {
        BundleParseException e = assertThrows(BundleParseException.class,
                () -> BundleParser.parse("{\"t\": {\"commands\": [\"say ${operator.typo}\"]}}"));
        assertTrue(e.getMessage().contains("${operator.typo}"), e.getMessage());
        assertTrue(e.getMessage().contains("command 0"), e.getMessage());
    }

    @Test
    @DisplayName("the parser accepts the known forms and keeps them verbatim for dispatch")
    void parserKeepsKnown() throws BundleParseException {
        Map<String, BundleDefinition> parsed = BundleParser.parse(
                "{\"t\": {\"commands\": [\"gamemode creative ${operator.name}\"]}}");
        assertEquals("gamemode creative ${operator.name}",
                parsed.get("t").getCommands().get(0).getCommand());
        assertFalse(Placeholders.isSafe(""));
    }
}
