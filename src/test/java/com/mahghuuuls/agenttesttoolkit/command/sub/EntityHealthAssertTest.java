package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.inspect.Assertions;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import net.minecraft.command.CommandException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The branches of {@code assert entity <selector|uuid> health <n>} after the lookup: unloaded only
 * for a fixture last seen in a place not loaded now, a non-living entity refused, a dying one
 * compared with {@code dead=true} on its record.
 */
class EntityHealthAssertTest {

    private static final String UUID = "11111111-2222-3333-4444-555555555555";
    private static final int[] PLACE = {0, 10, 64, -20};

    @BeforeEach
    void capture() {
        ToolkitLog.startCaptureForTesting();
    }

    @AfterEach
    void stop() {
        ToolkitLog.stopCaptureForTesting();
    }

    @Test
    @DisplayName("unloaded only when no loaded entity has the UUID and the fixture's last place is known and not loaded")
    void unloadedBranch() {
        LogRecord record = LogRecord.of(EventType.ASSERT);
        Assertions.Result unloaded = AssertSubCommand.unloadedHealth(10, UUID, false, PLACE, false, record);
        assertEquals(Assertions.Outcome.UNLOADED, unloaded.getOutcome());
        assertEquals("false", record.getFields().get("regionLoaded"));
        assertEquals(UUID, record.getFields().get("uuid"));
        assertEquals("0", record.getFields().get("dimension"));
        assertNull(AssertSubCommand.unloadedHealth(10, UUID, true, PLACE, false, LogRecord.of(EventType.ASSERT)),
                "a loaded entity is compared, wherever its fixture was last seen");
        assertNull(AssertSubCommand.unloadedHealth(10, UUID, false, null, false, LogRecord.of(EventType.ASSERT)),
                "no known place: the resolver refuses it as not found");
        assertNull(AssertSubCommand.unloadedHealth(10, UUID, false, PLACE, true, LogRecord.of(EventType.ASSERT)),
                "the place is loaded and the entity is not there: not found, not unloaded");
    }

    @Test
    @DisplayName("a non-living entity is refused, with no comparison")
    void nonLivingRefused() {
        LogRecord record = LogRecord.of(EventType.ASSERT);
        assertThrows(CommandException.class, () -> AssertSubCommand.livingHealth(10, "@e[type=item,c=1]", UUID,
                "minecraft:item", false, 0.0F, false, record));
        assertFalse(record.getFields().containsKey("targetUuid"));
    }

    @Test
    @DisplayName("a living entity is compared; a dying one, at zero health or dead, carries dead=true")
    void livingCompared() throws CommandException {
        LogRecord alive = LogRecord.of(EventType.ASSERT);
        assertEquals(Assertions.Outcome.PASS,
                AssertSubCommand.livingHealth(10, UUID, UUID, "minecraft:cow", true, 10.0F, false, alive).getOutcome());
        assertEquals(UUID, alive.getFields().get("targetUuid"));
        assertFalse(alive.getFields().containsKey("dead"));
        assertEquals(Assertions.Outcome.FAIL,
                AssertSubCommand.livingHealth(10, UUID, UUID, "minecraft:cow", true, 7.0F, false,
                        LogRecord.of(EventType.ASSERT)).getOutcome());

        LogRecord dying = LogRecord.of(EventType.ASSERT);
        Assertions.Result zero = AssertSubCommand.livingHealth(0, UUID, UUID, "minecraft:cow", true, 0.0F, false, dying);
        assertTrue(zero.passed(), "its current health is compared");
        assertEquals("true", dying.getFields().get("dead"));
        LogRecord dead = LogRecord.of(EventType.ASSERT);
        AssertSubCommand.livingHealth(3, UUID, UUID, "minecraft:cow", true, 3.0F, true, dead);
        assertEquals("true", dead.getFields().get("dead"));
    }
}
