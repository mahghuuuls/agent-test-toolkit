package com.mahghuuuls.agenttesttoolkit.bundle;

import com.mahghuuuls.agenttesttoolkit.config.ToolkitConfigLoader;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;

import java.io.File;

/**
 * The single bundle registry, and where its files live.
 *
 * <p>Bundles sit under the toolkit's configuration directory in a {@code bundles}
 * subdirectory, beside the configuration file rather than inside a world save. That placement
 * is the feature's whole point: definitions survive making a fresh disposable test world.
 */
public final class Bundles {

    public static final String BUNDLES_DIR_NAME = "bundles";

    private static final BundleRegistry REGISTRY = new BundleRegistry();
    private static final BundleScheduler SCHEDULER = new BundleScheduler();

    private Bundles() {
    }

    public static BundleRegistry registry() {
        return REGISTRY;
    }

    public static BundleScheduler scheduler() {
        return SCHEDULER;
    }

    /**
     * @return the bundles directory, creating it when absent; null when the toolkit
     * configuration directory is not yet known, or when the directory could not be created,
     * in which case the failure has already been reported
     */
    public static File directory() {
        File configDir = ToolkitConfigLoader.getConfigDirectory();
        if (configDir == null) {
            return null;
        }
        File bundlesDir = new File(configDir, BUNDLES_DIR_NAME);
        boolean firstRun = !bundlesDir.exists();
        if (firstRun && bundlesDir.mkdirs()) {
            // Seeded once, on a genuine first install, and never touched again. The
            // absence of the directory is the only signal used, so an operator who deletes
            // every example gets them back only by deleting the directory too, which is a
            // deliberate act rather than an accident.
            ExampleBundles.seed(bundlesDir);
            return bundlesDir;
        }
        if (!bundlesDir.exists() && !bundlesDir.mkdirs()) {
            // Reported rather than swallowed. Returning a directory that does not exist would
            // make a permissions failure indistinguishable from "no bundles configured yet",
            // and the toolkit would look healthy while silently loading nothing.
            ToolkitLog.error("Could not create bundles directory", bundlesDir.getAbsolutePath());
            return null;
        }
        return bundlesDir;
    }

    /** Reloads from disk. Returns the report so a caller can decide when to report problems. */
    public static BundleRegistry.LoadReport reload() {
        return REGISTRY.loadFrom(directory());
    }
}
