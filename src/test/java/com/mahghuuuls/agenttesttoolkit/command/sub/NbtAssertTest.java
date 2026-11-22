package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.inspect.Assertions;
import com.mahghuuuls.agenttesttoolkit.inspect.NbtLookup;
import com.mahghuuuls.agenttesttoolkit.inspect.NbtPath;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code assert nbt}: numbers compared at two decimals when the tag is a number and the declared
 * value reads as one, text compared exactly otherwise, a missing path failing with
 * {@code observed=missing}, an unloaded target neither pass nor fail; and the words after the
 * target split into a path and a value.
 */
class NbtAssertTest {

    @Test
    @DisplayName("numbers agree at two decimals: Health 20 matches a stored 20.0, Damage 6 does not match 5")
    void numbersAtTwoDecimals() {
        assertTrue(Assertions.nbtValue("20", true, "20.0", true).passed());
        assertTrue(Assertions.nbtValue("5", true, "5", true).passed());
        assertFalse(Assertions.nbtValue("6", true, "5", true).passed());
        assertTrue(Assertions.nbtValue("0.1", true, "0.10000000149011612", true).passed(), "a float's double text");
        assertTrue(Assertions.nbtValue("2.68", true, "2.675", true).passed(), "rounded half up, as written");
        assertTrue(Assertions.nbtValue("2.67", true, "2.665", true).passed(),
                "half up on the written digits; through a double 2.665 would round down to 2.66");
        assertFalse(Assertions.nbtValue("2.66", true, "2.665", true).passed());
        assertFalse(Assertions.nbtValue("1234567890123456788", true, "1234567890123456789", true).passed(),
                "a long keeps every digit");
        Assertions.Result result = Assertions.nbtValue("20", true, "20.0", true);
        assertEquals("20", result.getExpected(), "the declared value as typed");
        assertEquals("20.0", result.getObserved(), "the value as stored");
    }

    @Test
    @DisplayName("text is compared exactly, and a string tag that looks like a number is still text")
    void textExactly() {
        assertTrue(Assertions.nbtValue("minecraft:stone", true, "minecraft:stone", false).passed());
        assertFalse(Assertions.nbtValue("Minecraft:stone", true, "minecraft:stone", false).passed(), "case counts");
        assertFalse(Assertions.nbtValue("5", true, "5.0", false).passed(), "a string tag is not a number");
        assertFalse(Assertions.nbtValue("five", true, "5", true).passed(), "a word against a number is text");
        assertTrue(Assertions.nbtValue("NaN", true, "NaN", true).passed(), "not a decimal: compared as text");
        assertFalse(Assertions.nbtComparesAsNumbers("5", "5.0", false));
        assertTrue(Assertions.nbtComparesAsNumbers("5", "5.0", true));
    }

    @Test
    @DisplayName("a missing path fails with observed=missing and the failure key; an unloaded target is neither")
    void missingAndUnloaded() {
        Assertions.Result missing = Assertions.nbtValue("1", false, null, false);
        assertEquals(Assertions.Outcome.FAIL, missing.getOutcome());
        assertEquals("missing", missing.getObserved());
        assertEquals("devtool.assert.failed", missing.failureKey());
        Assertions.Result unloaded = Assertions.nbtUnloaded("1");
        assertEquals(Assertions.Outcome.UNLOADED, unloaded.getOutcome());
        assertEquals("devtool.assert.unloaded", unloaded.failureKey());
    }

    @Test
    @DisplayName("the record carries found, type, value and how it was compared, or where the path stopped")
    void recordFields() {
        NBTTagCompound stack = new NBTTagCompound();
        stack.setShort("Damage", (short) 5);

        LogRecord passed = LogRecord.of(EventType.ASSERT);
        Assertions.Result ok = AssertSubCommand.compareNbt("5",
                NbtLookup.resolve(stack, NbtPath.parse("Damage"), 100), passed);
        assertTrue(ok.passed());
        Map<String, String> fields = passed.getFields();
        assertEquals("true", fields.get("found"));
        assertEquals("short", fields.get("type"));
        assertEquals("5", fields.get("value"));
        assertEquals("number", fields.get("comparedAs"));
        assertNull(fields.get("missingAt"));

        LogRecord failed = LogRecord.of(EventType.ASSERT);
        Assertions.Result no = AssertSubCommand.compareNbt("1",
                NbtLookup.resolve(stack, NbtPath.parse("No.Such.Path"), 100), failed);
        assertEquals("missing", no.getObserved());
        assertEquals("false", failed.getFields().get("found"));
        assertEquals("No", failed.getFields().get("missingAt"));
        assertNull(failed.getFields().get("comparedAs"));

        LogRecord nothing = LogRecord.of(EventType.ASSERT);
        assertEquals("missing", AssertSubCommand.compareNbt("1", null, nothing).getObserved(),
                "no tile entity or an empty hand: nothing to read is missing");
        assertEquals("false", nothing.getFields().get("found"));
    }

    @Test
    @DisplayName("the path ends at the first space outside quotes; the rest, spaces included, is the value")
    void pathAndValue() {
        assertArrayEquals(new String[] {"Damage", "5"}, AssertSubCommand.splitPathAndValue("Damage 5"));
        assertArrayEquals(new String[] {"display.\"my name\"", "a b"},
                AssertSubCommand.splitPathAndValue("display.\"my name\" a b"));
        assertNull(AssertSubCommand.splitPathAndValue("Damage"), "no value");
        assertNull(AssertSubCommand.splitPathAndValue("Damage "), "an empty value");
        assertEquals(2, NbtTargets.words("entity"));
        assertEquals(4, NbtTargets.words("block"));
        assertEquals(1, NbtTargets.words("held"));
        assertEquals(-1, NbtTargets.words("player"));
        assertEquals("a \"b c\" d", NbtTargets.joinFrom(new String[] {"x", "a", "\"b", "c\"", "d"}, 1),
                "the words are joined as the command line was split");
    }
}
