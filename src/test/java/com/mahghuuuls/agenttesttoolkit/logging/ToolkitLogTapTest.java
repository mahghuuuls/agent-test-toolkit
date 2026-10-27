package com.mahghuuuls.agenttesttoolkit.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A bundle's tap sees only the records its own thread writes. */
class ToolkitLogTapTest {

    @AfterEach
    void clear() {
        ToolkitLog.setTap(null);
        ToolkitLog.stopCaptureForTesting();
    }

    @Test
    @DisplayName("a record written on another thread, as the client thread does in single player, skips the tap")
    void otherThreadSkipsTap() throws Exception {
        ToolkitLog.startCaptureForTesting();
        final List<LogRecord> seen = new ArrayList<LogRecord>();
        ToolkitLog.setTap(new ToolkitLog.Tap() {
            @Override
            public void accept(LogRecord record) {
                seen.add(record);
            }
        });

        ToolkitLog.write(LogRecord.of(EventType.MARK).add("label", "server"));
        Thread client = new Thread(new Runnable() {
            @Override
            public void run() {
                ToolkitLog.write(LogRecord.of(EventType.SCREENSHOT).add("label", "client"));
            }
        });
        client.start();
        client.join();

        assertEquals(1, seen.size(), "only this thread's record reaches the tap");
        assertEquals(2, ToolkitLog.capturedForTesting().size(), "both still reach the log");
    }
}
