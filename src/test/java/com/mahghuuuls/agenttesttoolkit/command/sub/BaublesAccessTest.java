package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.integration.BaublesBridge;
import com.mahghuuuls.agenttesttoolkit.integration.Integrations;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import net.minecraft.command.CommandException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The command code's one access point to the Baubles bridge, and the record it writes for an unsupported provider. */
class BaublesAccessTest {

    /** A bridge whose slot type and set fail with the key given. */
    private static final class Failing implements BaublesBridge {
        private final String key;

        Failing(String key) {
            this.key = key;
        }

        @Override
        public int slotCount(EntityPlayer player) {
            return 7;
        }

        @Override
        public String slotType(EntityPlayer player, int slot) throws CommandException {
            throw new CommandException(key, "getSlotType (IndexOutOfBoundsException)");
        }

        @Override
        public ItemStack get(EntityPlayer player, int slot) {
            return null;
        }

        @Override
        public boolean accepts(EntityPlayer player, int slot, ItemStack stack) {
            return true;
        }

        @Override
        public void set(EntityPlayer player, int slot, ItemStack stack) throws CommandException {
            throw new CommandException(key, "isEventBlocked (IllegalStateException)");
        }
    }

    @BeforeEach
    void start() {
        ToolkitLog.startCaptureForTesting();
    }

    @AfterEach
    void stop() {
        ToolkitLog.stopCaptureForTesting();
        Integrations.setBaublesForTesting(null);
    }

    private static List<String> errors() {
        List<String> found = new ArrayList<String>();
        for (String record : ToolkitLog.capturedForTesting()) {
            if (record.contains("[ERROR]")) {
                found.add(record);
            }
        }
        return found;
    }

    @Test
    @DisplayName("an unsupported provider writes one ERROR naming the failed method, and the failure goes on unchanged")
    void unsupportedIsRecorded() {
        Integrations.setBaublesForTesting(new Failing(BaublesBridge.KEY_UNSUPPORTED));
        BaublesBridge bridge = BaublesAccess.bridge();

        CommandException set = assertThrows(CommandException.class, () -> bridge.set(null, 1, null));
        assertEquals(BaublesBridge.KEY_UNSUPPORTED, set.getMessage());
        assertEquals(1, errors().size(), errors().toString());
        assertTrue(errors().get(0).contains("detail=\"isEventBlocked (IllegalStateException)\""), errors().get(0));

        assertThrows(CommandException.class, () -> bridge.slotType(null, 4));
        assertEquals(2, errors().size(), errors().toString());
        assertTrue(errors().get(1).contains("detail=\"getSlotType (IndexOutOfBoundsException)\""), errors().get(1));
        assertEquals(7, bridge.slotCount(null), "the other calls pass through");
    }

    @Test
    @DisplayName("another failure of the bridge is passed on without that record")
    void otherFailuresAreNotRecorded() {
        Integrations.setBaublesForTesting(new Failing(BaublesSubCommand.KEY_REJECTED));
        CommandException e = assertThrows(CommandException.class, () -> BaublesAccess.bridge().set(null, 1, null));
        assertEquals(BaublesSubCommand.KEY_REJECTED, e.getMessage());
        assertTrue(errors().isEmpty(), errors().toString());
    }

    @Test
    @DisplayName("without Baubles there is no bridge")
    void absent() {
        assertNull(BaublesAccess.bridge());
    }
}
