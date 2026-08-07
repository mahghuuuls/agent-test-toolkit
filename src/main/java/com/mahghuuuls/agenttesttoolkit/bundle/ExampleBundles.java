package com.mahghuuuls.agenttesttoolkit.bundle;

import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;

/**
 * Writes example bundles the first time the toolkit runs, and never again.
 *
 * <p>Two hazards shape this, both found by reasoning about how it would interact with
 * decisions already made rather than by testing it.
 *
 * <h2>Names are prefixed because of our own conflict rule</h2>
 *
 * <p>A name defined in two files loads from <b>neither</b>. A shipped bundle called
 * {@code teardown} would therefore not lose to an operator's own {@code teardown}, and would not
 * win: both would vanish, and the reported conflict would name a file they never wrote. Every
 * shipped name carries {@code example_} so a natural choice can never collide.
 *
 * <h2>Seeded once, never managed</h2>
 *
 * <p>Written only when the bundles directory does not exist, which is a genuine first install.
 * Rewriting on launch would destroy edits; updating on version change would destroy them
 * silently, which is worse. After the first run these files belong to the operator.
 *
 * <p>Nothing shipped is wired to run automatically. Installing the toolkit must still change
 * nothing until it is asked to act, which is why the join automation bundle name defaults to
 * empty rather than to one of these.
 */
public final class ExampleBundles {

    private static final String FILE_NAME = "examples.json";

    private ExampleBundles() {
    }

    /**
     * Writes the examples into a newly created bundles directory.
     *
     * <p>Called only when the directory was absent and has just been made. Failure is reported
     * and otherwise ignored: an operator without example bundles has a working toolkit, so this
     * must never be the reason loading fails.
     */
    static void seed(File bundlesDirectory) {
        File target = new File(bundlesDirectory, FILE_NAME);
        if (target.exists()) {
            return;
        }
        try {
            Files.write(target.toPath(), CONTENT.getBytes(Charset.forName("UTF-8")));
        } catch (IOException e) {
            ToolkitLog.error("Could not write example bundles", e.getMessage());
        } catch (RuntimeException e) {
            ToolkitLog.error("Could not write example bundles",
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * The shipped content.
     *
     * <p>The portal examples earn their place twice over. They are genuinely repeated across
     * testing sessions, and they are the concrete demonstration that a capability which looks
     * like it needs a command is a dozen {@code setblock} lines. The public documentation
     * positions the toolkit honestly against what vanilla already does, and this is that
     * argument in runnable form.
     */
    private static final String CONTENT =
            // No comment key at the top level. JSON has none, and every top-level entry here
            // must be a bundle: a "_comment" string fails the whole file, which the shipped
            // examples test caught before this ever reached anyone. Guidance lives in each
            // bundle's own description instead, where the format already has a place for it.
            "{\n"
            + "  \"example_test_ready\": {\n"
            + "    \"description\": \"Shipped example, written once on first run and never "
            + "overwritten. Edit freely; it is yours now. Pins the world for a reproducible "
            + "test: no mob spawning, no day cycle, clear weather, keep inventory.\",\n"
            + "    \"commands\": [\n"
            + "      \"gamerule keepInventory true\",\n"
            + "      \"gamerule doMobSpawning false\",\n"
            + "      \"gamerule doDaylightCycle false\",\n"
            + "      \"weather clear\",\n"
            + "      \"time set day\",\n"
            + "      \"devtool mark TEST_READY\"\n"
            + "    ]\n"
            + "  },\n"
            + "\n"
            + "  \"example_teardown\": {\n"
            + "    \"description\": \"Stop diagnostics and clear the area. Safe to run twice: a "
            + "kill that matches nothing counts as success.\",\n"
            + "    \"commands\": [\n"
            + "      \"devtool session stop\",\n"
            + "      \"devtool log all off\",\n"
            + "      \"kill @e[type=!player,r=30]\"\n"
            + "    ]\n"
            + "  },\n"
            + "\n"
            + "  \"example_nether_portal\": {\n"
            + "    \"description\": \"A lit nether portal, built two blocks away so it does not "
            + "bury you. No command needed for this: an obsidian frame and one fire block is "
            + "all a portal is.\",\n"
            + "    \"commands\": [\n"
            + "      \"fill ~3 ~ ~ ~4 ~ ~ minecraft:obsidian\",\n"
            + "      \"fill ~3 ~4 ~ ~4 ~4 ~ minecraft:obsidian\",\n"
            + "      \"fill ~2 ~ ~ ~2 ~4 ~ minecraft:obsidian\",\n"
            + "      \"fill ~5 ~ ~ ~5 ~4 ~ minecraft:obsidian\",\n"
            + "      \"setblock ~3 ~1 ~ minecraft:fire\",\n"
            + "      \"devtool mark NETHER_PORTAL_READY\"\n"
            + "    ]\n"
            + "  },\n"
            + "\n"
            + "  \"example_end_portal\": {\n"
            + "    \"description\": \"An end portal frame with every eye already placed, built "
            + "clear of where you stand so you do not step straight into it. Metadata carries "
            + "both the facing and the eye bit, which is why each side is filled separately.\",\n"
            + "    \"commands\": [\n"
            + "      \"fill ~4 ~ ~ ~6 ~ ~ minecraft:end_portal_frame 4\",\n"
            + "      \"fill ~4 ~ ~4 ~6 ~ ~4 minecraft:end_portal_frame 6\",\n"
            + "      \"fill ~3 ~ ~1 ~3 ~ ~3 minecraft:end_portal_frame 5\",\n"
            + "      \"fill ~7 ~ ~1 ~7 ~ ~3 minecraft:end_portal_frame 7\",\n"
            + "      \"fill ~4 ~ ~1 ~6 ~ ~3 minecraft:end_portal\",\n"
            + "      \"devtool mark END_PORTAL_READY\"\n"
            + "    ]\n"
            + "  },\n"
            + "\n"
            + "  \"example_damage_test\": {\n"
            + "    \"description\": \"Prepare a named, immobile target and start recording "
            + "damage against it. The delay lets the summon settle before the mark.\",\n"
            + "    \"commands\": [\n"
            + "      \"devtool arena reset\",\n"
            + "      \"summon minecraft:zombie ~3 ~ ~ {CustomName:\\\"damage_target\\\","
            + "NoAI:1,PersistenceRequired:1}\",\n"
            + "      \"devtool log entity_damage on arena\",\n"
            + "      \"devtool session start damage_test\",\n"
            + "      { \"command\": \"devtool mark DAMAGE_TEST_READY\", \"delayTicks\": 20 }\n"
            + "    ]\n"
            + "  }\n"
            + "}\n";
}
