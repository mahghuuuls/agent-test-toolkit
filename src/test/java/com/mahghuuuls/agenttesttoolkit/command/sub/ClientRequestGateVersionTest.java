package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.command.sub.ClientRequestGate.Facts;
import com.mahghuuuls.agenttesttoolkit.command.sub.ClientRequestGate.Refusal;
import com.mahghuuuls.agenttesttoolkit.command.sub.ClientRequestGate.Step;
import com.mahghuuuls.agenttesttoolkit.net.ToolkitChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The 1.9.0 gate of the input and screen steps, and the versions a version refusal names. */
class ClientRequestGateVersionTest {

    private static final String[] NEW_STEPS = {"key", "keys", "use", "attack", "look", "face", "view", "focus",
            "screen", "click", "scroll", "tooltip"};

    private static final Step LOOK = Step.newStep("look");

    private static Facts client(String version, Boolean allowed) {
        return new Facts(false, false, true, allowed, version);
    }

    private static Refusal newStep(String step, Facts facts) {
        return ClientRequestGate.refusal(Step.newStep(step), facts);
    }

    @Test
    @DisplayName("a new step goes only to a client that said hello with 1.9.0; 1.7.0 and 1.8.0 are too old whatever they allow")
    void newStepsNeed190() {
        for (String step : NEW_STEPS) {
            assertNull(newStep(step, client("1.9.0", Boolean.TRUE)), step);
            assertNull(newStep(step, client("1.10.0", Boolean.TRUE)), step);
            assertEquals(Refusal.CLIENT_TOO_OLD, newStep(step, client("1.8.0", Boolean.TRUE)), step);
            assertEquals(Refusal.CLIENT_TOO_OLD, newStep(step, client("1.7.0", Boolean.TRUE)), step);
            assertEquals(Refusal.CLIENT_TOO_OLD, newStep(step, client("1.8.0", Boolean.FALSE)), step);
            assertEquals(Refusal.NOT_ALLOWED, newStep(step, client("1.9.0", Boolean.FALSE)), step);
            assertEquals(Refusal.NOT_ALLOWED, newStep(step, client(null, null)), "no hello yet: " + step);
            assertEquals(Refusal.NO_CLIENT, newStep(step, new Facts(false, false, false, null, null)), step);
            assertEquals(Refusal.BUSY, newStep(step, new Facts(false, true, true, Boolean.TRUE, "1.9.0")), step);
        }
    }

    @Test
    @DisplayName("a dead player is refused every new step but screen, keys and view")
    void dead() {
        Facts dead = new Facts(true, false, true, Boolean.TRUE, "1.9.0");
        for (String step : NEW_STEPS) {
            boolean allowed = "screen".equals(step) || "keys".equals(step) || "view".equals(step);
            assertEquals(allowed ? null : Refusal.DEAD, newStep(step, dead), step);
        }
        assertEquals(Refusal.CLIENT_TOO_OLD, newStep("screen", new Facts(true, false, true, Boolean.TRUE, "1.8.0")),
                "the version before the death");
    }

    @Test
    @DisplayName("the gate lets every 1.8.0 step through to a 1.8.0 client")
    void oldStepsUnchanged() {
        Facts eight = client("1.8.0", Boolean.TRUE);
        for (String step : new String[] {"select", "use", "attack", "hold", "drop", "inventory", "tooltip"}) {
            assertNull(ClientRequestGate.refusal(Step.of(step, false, false), eight), step);
            assertNull(ClientRequestGate.refusal(Step.of(step, false, true), eight), "with screenshotHide: " + step);
            assertNull(ClientRequestGate.refusal(Step.of(step, false, false), client("1.7.0", Boolean.TRUE)), step);
        }
        assertNull(ClientRequestGate.refusal(Step.of("reconnect", true, false), eight));
        assertNull(ClientRequestGate.refusal(Step.of("quit", false, false), eight));
        assertNull(ClientRequestGate.refusal(Step.of("close", false, false), eight));
    }

    @Test
    @DisplayName("each step names the version it needs")
    void neededVersions() {
        assertArrayEquals(ToolkitChannel.VERSION_1_9_0, ClientRequestGate.neededVersion(Step.newStep("look")));
        assertArrayEquals(ToolkitChannel.VERSION_1_9_0, ClientRequestGate.neededVersion(Step.newStep("use")));
        assertArrayEquals(ToolkitChannel.VERSION_1_7_0, ClientRequestGate.neededVersion(Step.of("use", false, false)));
        assertArrayEquals(ToolkitChannel.VERSION_1_8_0, ClientRequestGate.neededVersion(Step.of("use", false, true)));
        assertArrayEquals(ToolkitChannel.VERSION_1_7_0, ClientRequestGate.neededVersion(Step.of("reconnect", true, false)));
        assertArrayEquals(ToolkitChannel.VERSION_1_5_0, ClientRequestGate.neededVersion(Step.of("quit", false, false)));
        assertArrayEquals(new int[] {1, 4, 0}, ClientRequestGate.neededVersion(Step.of("close", false, false)));
    }

    @Test
    @DisplayName("the gate's version check and the version a refusal names cannot disagree, for every kind of step")
    void gateAndWordingAgree() {
        Step[] steps = {Step.newStep("look"), Step.newStep("screen"), Step.newStep("use"), Step.of("use", false, false),
                Step.of("use", false, true), Step.of("select", false, false), Step.of("reconnect", true, false),
                Step.of("reconnect", false, false), Step.of("quit", false, false), Step.of("close", false, false)};
        String[] hellos = {"1.4.0", "1.5.0", "1.6.0", "1.7.0", "1.8.0", "1.9.0", "1.10.0"};
        for (Step step : steps) {
            int[] needed = ClientRequestGate.neededVersion(step);
            String neededText = ToolkitChannel.versionText(needed);
            for (String hello : hellos) {
                Refusal refusal = ClientRequestGate.refusal(step, client(hello, Boolean.TRUE));
                boolean older = !ToolkitChannel.atLeast(hello, needed);
                assertEquals(older, refusal == Refusal.CLIENT_TOO_OLD, step.name + " at " + hello);
                if (refusal == Refusal.CLIENT_TOO_OLD) {
                    Object[] arguments = ClientRequestGate.arguments(refusal, "Dev", hello, step);
                    assertEquals(hello, arguments[1]);
                    assertEquals(neededText, arguments[2], step.name + " names what the gate checked");
                }
            }
            assertNull(ClientRequestGate.refusal(step, client(neededText, Boolean.TRUE)),
                    step.name + " reaches a client at exactly the version it names");
        }
    }

    @Test
    @DisplayName("a version refusal folds both versions into the player argument an older client shows, then gives them apart")
    void refusalArguments() {
        Object[] tooOld = ClientRequestGate.arguments(Refusal.CLIENT_TOO_OLD, "Dev", "1.8.0", LOOK);
        assertEquals(3, tooOld.length);
        assertEquals("Dev (client toolkit 1.8.0, this step needs 1.9.0)", tooOld[0]);
        assertEquals("1.8.0", tooOld[1]);
        assertEquals("1.9.0", tooOld[2]);
        Object[] seven = ClientRequestGate.arguments(Refusal.CLIENT_TOO_OLD, "Dev", "1.7.0", LOOK);
        assertEquals("1.7.0", seven[1]);
        Object[] none = ClientRequestGate.arguments(Refusal.NO_CLIENT, "Dev", null, LOOK);
        assertEquals("Dev (client toolkit none, this step needs 1.9.0)", none[0]);
        assertEquals("none", none[1]);
        assertArrayEquals(new Object[] {"Dev"}, ClientRequestGate.arguments(Refusal.NOT_ALLOWED, "Dev", "1.9.0",
                LOOK));
        assertArrayEquals(new Object[] {"Dev"}, ClientRequestGate.arguments(Refusal.DEAD, "Dev", "1.9.0",
                LOOK));
        assertEquals("devtool.client.noClient", Refusal.CLIENT_TOO_OLD.key);
    }

    @Test
    @DisplayName("the shipped message has one placeholder, so an older client's copy shows the versions inside it")
    void langKeepsOnePlaceholder() throws java.io.IOException {
        String lang = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
                "src/main/resources/assets/agenttesttoolkit/lang/en_us.lang")), "UTF-8");
        String line = null;
        for (String each : lang.split("\r?\n")) {
            if (each.startsWith("devtool.client.noClient=")) {
                line = each;
            }
        }
        assertTrue(line != null, "the key is in the language file");
        assertEquals(1, line.split("%s", -1).length - 1, line);
        String shown = String.format(line.substring(line.indexOf('=') + 1),
                ClientRequestGate.arguments(Refusal.CLIENT_TOO_OLD, "Dev", "1.8.0", LOOK));
        assertTrue(shown.contains("1.8.0") && shown.contains("1.9.0"), shown);
    }
}
