package com.mahghuuuls.agenttesttoolkit.inspect;

import com.mahghuuuls.agenttesttoolkit.bundle.CommandOutcome;
import com.mahghuuuls.agenttesttoolkit.command.CommandOutcomes;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Attribute records and the attribute assertions, on observations shaped like four iron pieces. */
class AttributesTest {

    private static final String BOOTS = "845db27c-c624-495f-8c9f-6020a9a58b6b";
    private static final String LEGS = "d8499b04-0e66-4726-ab29-64469d734e0d";
    private static final String CHEST = "9f3d476d-c118-4544-8365-64846904b48e";
    private static final String HELMET = "2ad3f246-fee1-4e67-b886-69fd380bb150";

    /** Vanilla gives all four armor pieces the same name. */
    private static final List<Attributes.Modifier> IRON = Arrays.asList(
            new Attributes.Modifier(BOOTS, "Armor modifier", 0, 2.0),
            new Attributes.Modifier(LEGS, "Armor modifier", 0, 5.0),
            new Attributes.Modifier(CHEST, "Armor modifier", 0, 6.0),
            new Attributes.Modifier(HELMET, "Armor modifier", 0, 2.0));

    @Test
    @DisplayName("one ATTRIBUTE record, then one ATTRIBUTE_MODIFIER per modifier")
    void records() {
        List<LogRecord> records = Attributes.records("minecraft:player", "uuid-1",
                Collections.singletonList(new Attributes.Attribute("generic.armor", 0.0, 15.0, IRON)));

        assertEquals(5, records.size());
        String head = records.get(0).render();
        assertTrue(head.contains("[ATTRIBUTE]"), head);
        assertTrue(head.contains("name=generic.armor base=0.00 value=15.00 modifiers=4"), head);
        String first = records.get(1).render();
        assertTrue(first.contains("[ATTRIBUTE_MODIFIER]"), first);
        assertTrue(first.contains("attribute=generic.armor uuid=" + BOOTS), first);
        assertTrue(first.contains("name=\"Armor modifier\" operation=0 operationName=add amount=2.00"), first);
    }

    @Test
    @DisplayName("operations keep their 1.12.2 numbers and get a name")
    void operations() {
        assertEquals("add", Attributes.operationName(0));
        assertEquals("multiplyBase", Attributes.operationName(1));
        assertEquals("multiplyTotal", Attributes.operationName(2));
        assertEquals("7", Attributes.operationName(7));
    }

    @Test
    @DisplayName("a value matches to the two decimals the record shows, and a missing attribute fails")
    void value() {
        assertTrue(Assertions.attributeValue(15, 15.0).passed());
        assertTrue(Assertions.attributeValue(0.1, 0.1000001).passed());
        Assertions.Result wrong = Assertions.attributeValue(15, 13.0);
        assertFalse(wrong.passed());
        assertEquals("15.00", wrong.getExpected());
        assertEquals("13.00", wrong.getObserved());
        assertEquals(Assertions.KEY_FAILED, wrong.failureKey());
        assertEquals("absent", Assertions.attributeValue(15, null).getObserved());
    }

    @Test
    @DisplayName("a modifier by UUID passes with the right amount and fails with both values otherwise")
    void modifierByUuid() {
        assertTrue(Assertions.modifier(BOOTS.toUpperCase(), 2.0, IRON).passed(), "UUIDs match in any case");
        Assertions.Result wrong = Assertions.modifier(BOOTS, 3.0, IRON);
        assertFalse(wrong.passed());
        assertEquals("amount 3.00", wrong.getExpected());
        assertEquals("amount 2.00", wrong.getObserved());
        assertTrue(Assertions.modifier(CHEST, null, IRON).passed(), "presence only");
    }

    @Test
    @DisplayName("a name shared by several modifiers fails as ambiguous rather than picking one")
    void ambiguousName() {
        Assertions.Result result = Assertions.modifier("Armor modifier", 2.0, IRON);
        assertFalse(result.passed());
        assertEquals("4 matches", result.getObserved());
        assertEquals(Assertions.KEY_AMBIGUOUS, result.failureKey());
    }

    @Test
    @DisplayName("a unique name passes, and a missing modifier or attribute fails")
    void uniqueNameAndMissing() {
        List<Attributes.Modifier> set = Collections.singletonList(
                new Attributes.Modifier("0e3b6a7c-0000-4000-8000-000000000001", "setworks:set_bonus", 0, 2.0));
        assertTrue(Assertions.modifier("setworks:set_bonus", 2.0, set).passed());
        assertEquals("absent", Assertions.modifier("setworks:other", null, set).getObserved());
        assertEquals("absent", Assertions.modifier(BOOTS, null, null).getObserved());
        assertEquals(Assertions.KEY_FAILED, Assertions.modifier("setworks:other", null, set).failureKey());
    }

    @Test
    @DisplayName("the new failure keys classify as failures and are tolerable when declared")
    void keys() {
        for (String key : Arrays.asList(Assertions.KEY_AMBIGUOUS, CommandOutcomes.KEY_SAVE_FAILED)) {
            CommandOutcome plain = CommandOutcomes.classify(0, key);
            assertFalse(plain.succeeded(), key);
            CommandOutcome declared = CommandOutcomes.classify(0, key, Collections.singleton(key));
            assertTrue(declared.succeeded(), key);
            assertEquals(key, declared.getToleratedKey());
        }
        assertEquals("devtool.assert.ambiguous", CommandOutcomes.KEY_ASSERT_AMBIGUOUS);
        assertEquals("devtool.save.failed", CommandOutcomes.KEY_SAVE_FAILED);
    }

    @Test
    @DisplayName("the new failure keys have chat translations")
    void keysTranslated() throws Exception {
        java.io.InputStream in = AttributesTest.class
                .getResourceAsStream("/assets/agenttesttoolkit/lang/en_us.lang");
        java.util.Scanner scanner = new java.util.Scanner(in, "UTF-8").useDelimiter("\\A");
        String text = scanner.hasNext() ? scanner.next() : "";
        scanner.close();
        assertTrue(text.contains("\ndevtool.assert.ambiguous=[DevToolkit] "));
        assertTrue(text.contains("\ndevtool.save.failed=[DevToolkit] "));
    }

    @Test
    @DisplayName("a modifier reference is a UUID only when it has a UUID's shape")
    void uuidShape() {
        assertTrue(Attributes.looksLikeUuid(BOOTS));
        assertFalse(Attributes.looksLikeUuid("Armor modifier"));
        assertFalse(Attributes.looksLikeUuid(null));
    }
}
