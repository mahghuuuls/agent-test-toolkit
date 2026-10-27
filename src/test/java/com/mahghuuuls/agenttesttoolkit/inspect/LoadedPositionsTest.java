package com.mahghuuuls.agenttesttoolkit.inspect;

import com.mahghuuuls.agenttesttoolkit.bundle.CommandOutcome;
import com.mahghuuuls.agenttesttoolkit.command.CommandOutcomes;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import net.minecraft.command.CommandException;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The refusal an inspection gives for a position whose chunk is not loaded. */
class LoadedPositionsTest {

    @Test
    @DisplayName("a refused read names the position and the dimension")
    void describesPosition() {
        assertEquals("2000000 64 2000000 in dimension 0",
                LoadedPositions.describe(new BlockPos(2000000, 64, 2000000), 0));
        assertEquals("-5 10 7 in dimension -1",
                LoadedPositions.describe(new BlockPos(-5, 10, 7), -1));
        assertEquals("1 2 3", LoadedPositions.describe(new BlockPos(1, 2, 3), null));
    }

    @Test
    @DisplayName("a read with no loaded world is refused with the key and an ERROR record")
    void requireRefuses() {
        ToolkitLog.startCaptureForTesting();
        try {
            CommandException thrown = assertThrows(CommandException.class,
                    () -> LoadedPositions.require(null, new BlockPos(2000000, 64, 2000000)));
            assertEquals("devtool.inspect.notLoaded", thrown.getMessage());
            List<String> lines = ToolkitLog.capturedForTesting();
            assertEquals(1, lines.size());
            assertTrue(lines.get(0).contains("[ERROR]"), lines.get(0));
            assertTrue(lines.get(0).contains("2000000 64 2000000"), lines.get(0));
        } finally {
            ToolkitLog.stopCaptureForTesting();
        }
    }

    @Test
    @DisplayName("the block inspector refuses before it reads anything")
    void blockInspectorIsGuarded() {
        ToolkitLog.startCaptureForTesting();
        try {
            CommandException thrown = assertThrows(CommandException.class,
                    () -> Inspectors.block(null, new BlockPos(1, 2, 3)));
            assertEquals(LoadedPositions.KEY_NOT_LOADED, thrown.getMessage());
        } finally {
            ToolkitLog.stopCaptureForTesting();
        }
    }

    @Test
    @DisplayName("the key is a failure in a bundle, and tolerable when declared")
    void keyClassifies() {
        CommandOutcome plain = CommandOutcomes.classify(0, LoadedPositions.KEY_NOT_LOADED);
        assertFalse(plain.succeeded());

        CommandOutcome declared = CommandOutcomes.classify(0, LoadedPositions.KEY_NOT_LOADED,
                Collections.singleton("devtool.inspect.notLoaded"));
        assertTrue(declared.succeeded());
        assertEquals("devtool.inspect.notLoaded", declared.getToleratedKey());
        assertEquals(LoadedPositions.KEY_NOT_LOADED, CommandOutcomes.KEY_INSPECT_NOT_LOADED);
    }

    @Test
    @DisplayName("the key has a chat translation, so chat never shows the bare key")
    void keyIsTranslated() {
        InputStream in = LoadedPositionsTest.class
                .getResourceAsStream("/assets/agenttesttoolkit/lang/en_us.lang");
        assertNotNull(in, "the language file is on the classpath");
        Scanner scanner = new Scanner(in, Charset.forName("UTF-8").name()).useDelimiter("\\A");
        String text = scanner.hasNext() ? scanner.next() : "";
        scanner.close();
        assertTrue(text.contains("\ndevtool.inspect.notLoaded=[DevToolkit] Not loaded: %s"));
    }
}
