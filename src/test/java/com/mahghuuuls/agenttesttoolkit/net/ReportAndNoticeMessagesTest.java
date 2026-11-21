package com.mahghuuuls.agenttesttoolkit.net;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The two messages a 1.10.0 client reads or sends: the picture report and the notice, and their version gate. */
class ReportAndNoticeMessagesTest {

    @AfterEach
    void reset() {
        ToolkitChannel.setReportReceiver(null);
        ToolkitChannel.setNoticeReceiver(null);
    }

    private static ScreenshotReport wireReport(ScreenshotReport sent) {
        ByteBuf buf = Unpooled.buffer();
        sent.toBytes(buf);
        ScreenshotReport read = new ScreenshotReport();
        read.fromBytes(buf);
        assertFalse(buf.isReadable(), "every byte read");
        return read;
    }

    private static ToolkitNotice wireNotice(ToolkitNotice sent) {
        ByteBuf buf = Unpooled.buffer();
        sent.toBytes(buf);
        ToolkitNotice read = new ToolkitNotice();
        read.fromBytes(buf);
        assertFalse(buf.isReadable(), "every byte read");
        return read;
    }

    @Test
    @DisplayName("a picture report survives the wire with its label, ticks, file and outcome")
    void reportRoundTrip() {
        ScreenshotReport saved = wireReport(
                new ScreenshotReport("shot_a", 1234L, "screenshots/shot_a-1234.png", true, 98765L));
        assertTrue(saved.isValid());
        assertEquals("shot_a", saved.getLabel());
        assertEquals(1234L, saved.getRequestTick());
        assertEquals("screenshots/shot_a-1234.png", saved.getFile());
        assertTrue(saved.isSaved());
        assertEquals(98765L, saved.getFrameClientTick());

        ScreenshotReport failed = wireReport(new ScreenshotReport("shot-b", 7L, null, false, 0L));
        assertTrue(failed.isValid());
        assertEquals("", failed.getFile(), "no file is written as empty");
        assertFalse(failed.isSaved());
    }

    @Test
    @DisplayName("a report whose label or file could not be a picture of the toolkit is not valid")
    void reportInvalid() {
        assertFalse(new ScreenshotReport("../x", 1L, "", false, 0L).isValid(), "a label with a path");
        assertFalse(new ScreenshotReport(null, 1L, "", false, 0L).isValid());
        assertFalse(new ScreenshotReport("a", 1L, "screenshots/a\n.png", true, 0L).isValid(), "a control character");
        char[] longFile = new char[ScreenshotReport.MAX_FILE + 1];
        java.util.Arrays.fill(longFile, 'f');
        assertFalse(new ScreenshotReport("a", 1L, new String(longFile), true, 0L).isValid());
        assertTrue(new ScreenshotReport("a", 1L, new String(longFile, 0, ScreenshotReport.MAX_FILE), true, 0L)
                .isValid());
    }

    @Test
    @DisplayName("the server acts on a report only from a client that said hello with 1.10.0 or later")
    void reportGate() {
        ScreenshotReport report = new ScreenshotReport("shot_a", 1L, "screenshots/shot_a-1.png", true, 2L);
        assertTrue(report.isAcceptedFrom("1.10.0"));
        assertTrue(report.isAcceptedFrom("1.11.2"));
        assertTrue(report.isAcceptedFrom("2.0.0"));
        assertFalse(report.isAcceptedFrom("1.9.0"), "a 1.9.0 client was never asked to report");
        assertFalse(report.isAcceptedFrom("1.9.9"));
        assertFalse(report.isAcceptedFrom(null), "no hello");
        assertFalse(new ScreenshotReport("../x", 1L, "", true, 0L).isAcceptedFrom("1.10.0"), "an invalid report");
    }

    @Test
    @DisplayName("a notice survives the wire with its text and ticks")
    void noticeRoundTrip() {
        ToolkitNotice read = wireNotice(new ToolkitNotice("[DevToolkit] Bundle x failed at 2: commands.generic.usage", 200));
        assertTrue(read.isValid());
        assertEquals("[DevToolkit] Bundle x failed at 2: commands.generic.usage", read.getText());
        assertEquals(200, read.getTicks());
    }

    @Test
    @DisplayName("an empty, overlong or unbounded notice is not valid")
    void noticeInvalid() {
        assertFalse(new ToolkitNotice("", 200).isValid());
        assertFalse(new ToolkitNotice(null, 200).isValid());
        assertFalse(new ToolkitNotice("a\nb", 200).isValid(), "a control character");
        assertFalse(new ToolkitNotice("a", 0).isValid());
        assertFalse(new ToolkitNotice("a", ToolkitNotice.MAX_TICKS + 1).isValid());
        assertTrue(new ToolkitNotice("a", ToolkitNotice.MAX_TICKS).isValid());
        char[] longText = new char[ToolkitNotice.MAX_TEXT + 1];
        java.util.Arrays.fill(longText, 't');
        assertFalse(new ToolkitNotice(new String(longText), 200).isValid());
        assertTrue(new ToolkitNotice(new String(longText, 0, ToolkitNotice.MAX_TEXT), 200).isValid());
    }

    @Test
    @DisplayName("the notice handler hands a valid notice to the installed receiver, and nothing without one")
    void noticeHandler() {
        final java.util.List<String> received = new java.util.ArrayList<String>();
        ToolkitNotice.Handler handler = new ToolkitNotice.Handler();
        handler.onMessage(new ToolkitNotice("before", 200), null);
        assertTrue(received.isEmpty());
        ToolkitChannel.setNoticeReceiver(new ToolkitChannel.NoticeReceiver() {
            @Override
            public void receive(ToolkitNotice notice) {
                received.add(notice.getText());
            }
        });
        handler.onMessage(new ToolkitNotice("shown", 200), null);
        handler.onMessage(new ToolkitNotice("", 200), null);
        assertEquals(java.util.Collections.singletonList("shown"), received);
    }

    @Test
    @DisplayName("notices are sent only to a client that said hello with 1.10.0 or later")
    void noticeGate() {
        assertTrue(ToolkitChannel.readsNoticesAndSendsReports("1.10.0"));
        assertTrue(ToolkitChannel.readsNoticesAndSendsReports("1.10.0-dev"));
        assertTrue(ToolkitChannel.readsNoticesAndSendsReports("2.0.0"));
        assertFalse(ToolkitChannel.readsNoticesAndSendsReports("1.9.0"), "a 1.9.0 client has no handler for a notice");
        assertFalse(ToolkitChannel.readsNoticesAndSendsReports("1.1.0"));
        assertFalse(ToolkitChannel.readsNoticesAndSendsReports(null), "no hello");
        assertFalse(ToolkitChannel.readsNoticesAndSendsReports("x"));
        // The 1.9.0 messages still reach a 1.9.0 client; the new gate does not move the old one.
        assertTrue(ToolkitChannel.readsNewMessages("1.9.0"));
    }

    @Test
    @DisplayName("the version the new messages need reads as 1.10.0")
    void version() {
        assertEquals("1.10.0", ToolkitChannel.versionText(ToolkitChannel.VERSION_1_10_0));
        assertArrayEquals(new int[] {1, 10, 0}, ToolkitChannel.VERSION_1_10_0);
        assertTrue(ToolkitChannel.atLeast("1.10.0", ToolkitChannel.VERSION_1_10_0));
        assertFalse(ToolkitChannel.atLeast("1.9.0", ToolkitChannel.VERSION_1_10_0));
    }
}
