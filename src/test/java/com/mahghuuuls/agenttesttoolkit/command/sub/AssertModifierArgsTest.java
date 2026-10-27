package com.mahghuuuls.agenttesttoolkit.command.sub;

import net.minecraft.command.CommandException;
import net.minecraft.command.WrongUsageException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** How {@code assert attribute ... modifier <uuid|name> [amount <n>]} reads its words. */
class AssertModifierArgsTest {

    private static AssertSubCommand.ModifierArgs parse(String line) throws CommandException {
        return AssertSubCommand.parseModifier(line.split(" "), 4, "usage");
    }

    @Test
    @DisplayName("a name with spaces and a trailing amount")
    void nameWithAmount() throws Exception {
        AssertSubCommand.ModifierArgs a = parse("attribute @s generic.armor modifier Armor modifier amount 2");
        assertEquals("Armor modifier", a.reference);
        assertEquals(2.0, a.amount);
    }

    @Test
    @DisplayName("a UUID with no amount checks presence only, and amount is read in any case")
    void presenceAndCase() throws Exception {
        AssertSubCommand.ModifierArgs a = parse("attribute @s generic.armor modifier 845DB27C-C624-495F-8C9F-6020A9A58B6B");
        assertEquals("845DB27C-C624-495F-8C9F-6020A9A58B6B", a.reference);
        assertNull(a.amount);
        assertEquals(1.5, parse("attribute @s generic.armor modifier setworks:set_bonus AMOUNT 1.5").amount);
    }

    @Test
    @DisplayName("a missing number, a missing name, or a bad number is a usage error, not a lookup")
    void malformed() {
        assertThrows(WrongUsageException.class, () -> parse("attribute @s generic.armor modifier X amount"));
        assertThrows(WrongUsageException.class, () -> parse("attribute @s generic.armor modifier amount 2"));
        assertThrows(CommandException.class, () -> parse("attribute @s generic.armor modifier X amount two"));
    }
}
