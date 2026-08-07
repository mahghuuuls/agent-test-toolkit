package com.mahghuuuls.agenttesttoolkit.bundle;

import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Holds the loaded bundles and rebuilds them from disk on demand.
 *
 * <p>Bundles live in the toolkit's configuration directory, not inside a world save. That is
 * the concrete difference from vanilla functions and the reason the feature exists: a setup
 * routine stored in a world has to be recreated for every fresh test world, which is precisely
 * the repetitive work the toolkit is meant to remove.
 *
 * <p>Merging is separated from file access so the conflict and partial-loading rules can be
 * exercised without a filesystem.
 */
public final class BundleRegistry {

    /** The parsed contents of one file, ready to be merged. */
    public static final class SourceFile {
        private final String sourceName;
        private final Map<String, BundleDefinition> bundles;

        public SourceFile(String sourceName, Map<String, BundleDefinition> bundles) {
            this.sourceName = sourceName;
            this.bundles = bundles;
        }

        public String getSourceName() {
            return sourceName;
        }
    }

    /** The outcome of a load: what is usable, and everything that went wrong. */
    public static final class LoadReport {
        private final Map<String, BundleDefinition> loaded;
        private final List<String> problems;

        LoadReport(Map<String, BundleDefinition> loaded, List<String> problems) {
            this.loaded = Collections.unmodifiableMap(loaded);
            this.problems = Collections.unmodifiableList(problems);
        }

        public Map<String, BundleDefinition> getLoaded() {
            return loaded;
        }

        /** Human readable problems, one per malformed file or conflicting name. */
        public List<String> getProblems() {
            return problems;
        }

        public boolean hasProblems() {
            return !problems.isEmpty();
        }
    }

    /**
     * The current contents, held as one immutable snapshot rather than a map field and a problem
     * list field.
     *
     * <p>Two volatile fields would be assigned separately, so a status command reading them
     * either side of a concurrent reload could report the new bundle count beside the old
     * problems. That is a small window and a harmless-looking one, but the whole point of the
     * status output is that an agent can trust it, and a self-inconsistent reading is worse
     * than a slightly stale one. Swapping a single reference makes a torn read impossible.
     */
    private volatile LoadReport current = new LoadReport(
            new LinkedHashMap<String, BundleDefinition>(), Collections.<String>emptyList());

    /**
     * Merges parsed files into a single namespace.
     *
     * <p>A name defined in more than one file loads from <b>neither</b>. Picking a
     * winner by file order would be deterministic but wrong, because the two definitions differ
     * and running either is a coin flip on which the author meant. Refusing both makes the
     * mistake visible at the point of use rather than producing surprising behaviour later.
     *
     * <p>Pure and package-visible so the rule can be tested directly.
     */
    static LoadReport merge(List<SourceFile> sources) {
        Map<String, List<String>> sourcesByName = new LinkedHashMap<String, List<String>>();
        for (SourceFile source : sources) {
            for (String name : source.bundles.keySet()) {
                List<String> owners = sourcesByName.get(name);
                if (owners == null) {
                    owners = new ArrayList<String>(2);
                    sourcesByName.put(name, owners);
                }
                owners.add(source.sourceName);
            }
        }

        Set<String> conflicting = new LinkedHashSet<String>();
        List<String> problems = new ArrayList<String>();
        for (Map.Entry<String, List<String>> entry : sourcesByName.entrySet()) {
            if (entry.getValue().size() > 1) {
                conflicting.add(entry.getKey());
                problems.add("bundle name '" + entry.getKey() + "' is defined in "
                        + entry.getValue().size() + " files and was not loaded from any of them: "
                        + join(entry.getValue()));
            }
        }

        Map<String, BundleDefinition> loaded = new LinkedHashMap<String, BundleDefinition>();
        for (SourceFile source : sources) {
            for (Map.Entry<String, BundleDefinition> entry : source.bundles.entrySet()) {
                if (!conflicting.contains(entry.getKey())) {
                    loaded.put(entry.getKey(), entry.getValue());
                }
            }
        }

        return new LoadReport(loaded, problems);
    }

    /**
     * Rebuilds from a directory, replacing whatever was loaded before.
     *
     * <p>One malformed file must not prevent the others from loading. Partial success
     * is deliberately preferred, because a toolkit that refuses to load anything over a single
     * typo is useless for diagnosing whatever was actually being tested.
     *
     * <p>This touches only bundle definitions. The active session and enabled logging
     * categories are untouched, so reloading mid-test does not silently discard diagnostic
     * state.
     */
    public LoadReport loadFrom(File bundlesDirectory) {
        List<SourceFile> sources = new ArrayList<SourceFile>();
        List<String> readProblems = new ArrayList<String>();

        if (bundlesDirectory != null && bundlesDirectory.isDirectory()) {
            List<File> files = new ArrayList<File>();
            collectJsonFiles(bundlesDirectory, files);
            // Sorted so a load is reproducible; directory order is not guaranteed and an
            // unstable order would make conflict reports differ between runs.
            Collections.sort(files);

            for (File file : files) {
                String relative = relativise(bundlesDirectory, file);
                try {
                    String json = new String(Files.readAllBytes(file.toPath()),
                            Charset.forName("UTF-8"));
                    sources.add(new SourceFile(relative, BundleParser.parse(json)));
                } catch (BundleParseException e) {
                    readProblems.add(relative + ": " + e.getMessage());
                } catch (IOException e) {
                    readProblems.add(relative + ": could not be read: " + e.getMessage());
                } catch (RuntimeException e) {
                    readProblems.add(relative + ": " + e.getClass().getSimpleName()
                            + ": " + e.getMessage());
                }
            }
        }

        LoadReport merged = merge(sources);

        List<String> allProblems = new ArrayList<String>(readProblems);
        allProblems.addAll(merged.getProblems());

        LoadReport report = new LoadReport(
                new LinkedHashMap<String, BundleDefinition>(merged.getLoaded()), allProblems);
        this.current = report;

        // Problems are returned, not logged here. The startup summary has to carry the bundle
        // count and to precede any error records, which is only possible if the caller decides
        // when to emit them. Callers must report; see reportProblems.
        return report;
    }

    /**
     * Writes each problem as its own record.
     *
     * <p>One record each, never summarised away. Separated from loading so a caller can
     * control ordering relative to other startup output.
     */
    public static void reportProblems(LoadReport report) {
        for (String problem : report.getProblems()) {
            ToolkitLog.error("Bundle load problem", problem);
        }
    }

    /** Recursive, so bundles can be organised into subdirectories. */
    private static void collectJsonFiles(File directory, List<File> into) {
        File[] entries = directory.listFiles();
        if (entries == null) {
            return;
        }
        for (File entry : entries) {
            if (entry.isDirectory()) {
                collectJsonFiles(entry, into);
            } else if (entry.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".json")) {
                into.add(entry);
            }
        }
    }

    private static String relativise(File root, File file) {
        String rootPath = root.getAbsolutePath();
        String filePath = file.getAbsolutePath();
        if (filePath.startsWith(rootPath) && filePath.length() > rootPath.length() + 1) {
            return filePath.substring(rootPath.length() + 1).replace('\\', '/');
        }
        return file.getName();
    }

    public BundleDefinition get(String name) {
        return name == null ? null : current.getLoaded().get(name);
    }

    public boolean contains(String name) {
        return name != null && current.getLoaded().containsKey(name);
    }

    /** Names of every successfully loaded bundle, in load order. */
    public Set<String> names() {
        return current.getLoaded().keySet();
    }

    public Collection<BundleDefinition> all() {
        return current.getLoaded().values();
    }

    public int size() {
        return current.getLoaded().size();
    }

    /** Problems from the most recent load, retained so status can report them. */
    public List<String> getProblems() {
        return current.getProblems();
    }

    private static String join(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(values.get(i));
        }
        return out.toString();
    }

    /** Test helper: build a source directly, without touching a filesystem. */
    static SourceFile source(String name, Map<String, BundleDefinition> bundles) {
        return new SourceFile(name, bundles);
    }

    /** Test helper: merge without file access. */
    static LoadReport mergeSources(SourceFile... sources) {
        return merge(Arrays.asList(sources));
    }
}
