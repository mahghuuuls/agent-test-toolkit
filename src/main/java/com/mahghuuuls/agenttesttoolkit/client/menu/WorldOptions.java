package com.mahghuuuls.agenttesttoolkit.client.menu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The options of {@code create}, checked before anything happens (REQ-217, ARC-028).
 *
 * <p>Pure, for tests (rule 18). The game falls back silently on a bad value: an unknown game
 * mode becomes survival, an unknown world type gives nothing, a flat string with one bad block,
 * meta or biome becomes something else (FQ39). So every value is checked here, and anything
 * unknown refuses the request. Blocks and biomes are checked by predicates the caller gives,
 * from the game's registries.
 */
public final class WorldOptions {

    /** The world types Create New World offers that a test may want; not customized or debug. */
    public static final List<String> TYPES = Arrays.asList("default", "flat", "largeBiomes", "amplified");
    public static final List<String> MODES = Arrays.asList("survival", "creative", "adventure", "spectator");
    static final Set<String> NAMES = new HashSet<String>(
            Arrays.asList("seed", "type", "flat", "structures", "cheats", "mode", "bonusChest"));

    /** The flat-world features that are structures, which {@code structures=false} removes. */
    static final Set<String> STRUCTURE_FEATURES = new HashSet<String>(
            Arrays.asList("village", "biome_1", "mineshaft", "stronghold", "oceanmonument"));
    /** The other flat-world features of 1.12.2. */
    static final Set<String> OTHER_FEATURES = new HashSet<String>(
            Arrays.asList("lake", "lava_lake", "dungeon", "decoration"));

    /** The vanilla flat world, without its trailing feature part. */
    static final String FLAT_LAYERS = "3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass;1;";
    /** The vanilla flat world's features. */
    static final String FLAT_STRUCTURES = "village";

    private static final Pattern LAYER = Pattern.compile("(?:([0-9]{1,3})\\*)?([a-z0-9_]+:[a-z0-9_]+)(?::([0-9]{1,2}))?");
    private static final Pattern BIOME = Pattern.compile("[0-9]{1,3}");
    /** One feature, with at most one parameter: the request line cannot hold the space between two. */
    private static final Pattern FEATURE = Pattern.compile("([a-z_0-9]+)(?:\\(([a-z_]+=[0-9]+)\\))?");

    private final String refusal;
    private final String seedText;
    private final String type;
    private final String flat;
    private final boolean structures;
    private final boolean cheats;
    private final String mode;
    private final boolean bonusChest;

    private WorldOptions(String refusal, String seedText, String type, String flat, boolean structures,
                         boolean cheats, String mode, boolean bonusChest) {
        this.refusal = refusal;
        this.seedText = seedText;
        this.type = type;
        this.flat = flat;
        this.structures = structures;
        this.cheats = cheats;
        this.mode = mode;
        this.bonusChest = bonusChest;
    }

    private static WorldOptions refused(String detail) {
        return new WorldOptions(detail, null, null, null, false, false, null, false);
    }

    /** Parses {@code name=value} words; the flat blocks and biome are checked by {@link #checkRegistry}. */
    public static WorldOptions parse(List<String> words) {
        String seed = null;
        String type = "default";
        String flat = null;
        Boolean structures = null;
        boolean cheats = true;
        String mode = "creative";
        boolean bonusChest = false;
        Set<String> seen = new HashSet<String>();
        for (String word : words) {
            int eq = word.indexOf('=');
            if (eq <= 0 || eq == word.length() - 1) {
                return refused("notNameValue:" + word);
            }
            String name = word.substring(0, eq);
            String value = word.substring(eq + 1);
            if (!NAMES.contains(name)) {
                return refused("unknownOption:" + name);
            }
            if (!seen.add(name)) {
                return refused("repeatedOption:" + name);
            }
            if ("seed".equals(name)) {
                if (value.length() > 32) {
                    return refused("seedTooLong");
                }
                seed = value;
            } else if ("type".equals(name)) {
                if (!TYPES.contains(value)) {
                    return refused("unknownType:" + value);
                }
                type = value;
            } else if ("flat".equals(name)) {
                flat = value;
            } else if ("mode".equals(name)) {
                if (!MODES.contains(value)) {
                    return refused("unknownMode:" + value);
                }
                mode = value;
            } else {
                Boolean flag = bool(value);
                if (flag == null) {
                    return refused("notBoolean:" + name);
                }
                if ("structures".equals(name)) {
                    structures = flag;
                } else if ("cheats".equals(name)) {
                    cheats = flag.booleanValue();
                } else {
                    bonusChest = flag.booleanValue();
                }
            }
        }
        if (flat != null && !"flat".equals(type)) {
            return refused("flatWithoutTypeFlat");
        }
        boolean withStructures = structures == null || structures.booleanValue();
        String generator = null;
        if ("flat".equals(type)) {
            String layers = flat == null ? FLAT_LAYERS + (withStructures ? FLAT_STRUCTURES : "") : flat;
            String problem = shapeProblem(layers);
            if (problem != null) {
                return refused(problem);
            }
            generator = withStructures ? layers : withoutStructures(layers);
        }
        return new WorldOptions(null, seed, type, generator, withStructures, cheats, mode, bonusChest);
    }

    private static Boolean bool(String value) {
        String v = value.toLowerCase(Locale.ROOT);
        if ("true".equals(v)) {
            return Boolean.TRUE;
        }
        if ("false".equals(v)) {
            return Boolean.FALSE;
        }
        return null;
    }

    /**
     * Why a flat string is not of the 1.12.2 shape {@code 3;layers;biome;features}, or null.
     * Blocks and the biome are checked against the registries by {@link #checkRegistry}.
     */
    static String shapeProblem(String flat) {
        String[] parts = flat.split(";", -1);
        if (parts.length != 4 || !"3".equals(parts[0])) {
            return "flatShape";
        }
        if (parts[1].isEmpty()) {
            return "flatNoLayers";
        }
        int height = 0;
        for (String layer : parts[1].split(",", -1)) {
            Matcher m = LAYER.matcher(layer);
            if (!m.matches()) {
                return "flatLayer:" + layer;
            }
            int count = m.group(1) == null ? 1 : Integer.parseInt(m.group(1));
            if (count < 1 || (m.group(3) != null && Integer.parseInt(m.group(3)) > 15)) {
                return "flatLayer:" + layer;
            }
            height += count;
        }
        if (height > 256) {
            return "flatTooHigh";
        }
        if (!BIOME.matcher(parts[2]).matches() || Integer.parseInt(parts[2]) > 255) {
            return "flatBiome:" + parts[2];
        }
        if (!parts[3].isEmpty()) {
            for (String feature : parts[3].split(",", -1)) {
                Matcher m = FEATURE.matcher(feature);
                if (!m.matches() || !(STRUCTURE_FEATURES.contains(m.group(1)) || OTHER_FEATURES.contains(m.group(1)))) {
                    return "flatFeature:" + feature;
                }
            }
        }
        return null;
    }

    /** The flat string with only its structure features removed; lakes, dungeons and decoration stay. */
    static String withoutStructures(String flat) {
        String[] parts = flat.split(";", -1);
        List<String> kept = new ArrayList<String>();
        if (!parts[3].isEmpty()) {
            for (String feature : parts[3].split(",")) {
                Matcher m = FEATURE.matcher(feature);
                if (m.matches() && !STRUCTURE_FEATURES.contains(m.group(1))) {
                    kept.add(feature);
                }
            }
        }
        StringBuilder out = new StringBuilder(parts[0]).append(';').append(parts[1]).append(';').append(parts[2]).append(';');
        for (int i = 0; i < kept.size(); i++) {
            out.append(i == 0 ? "" : ",").append(kept.get(i));
        }
        return out.toString();
    }

    /**
     * What in the flat string the registries do not know, as a refusal detail, or null when all
     * is known: {@code unknownBlock:<id>} or {@code unknownBiome:<id>}.
     */
    public String checkRegistry(Predicate<String> isKnownBlock, IntPredicate isKnownBiome) {
        if (flat == null) {
            return null;
        }
        String[] parts = flat.split(";", -1);
        for (String layer : parts[1].split(",")) {
            Matcher m = LAYER.matcher(layer);
            if (m.matches() && !isKnownBlock.test(m.group(2))) {
                return "unknownBlock:" + m.group(2);
            }
        }
        if (!isKnownBiome.test(Integer.parseInt(parts[2]))) {
            return "unknownBiome:" + parts[2];
        }
        return null;
    }

    public boolean isValid() {
        return refusal == null;
    }

    /** What was wrong, when not valid. */
    public String getRefusal() {
        return refusal;
    }

    /**
     * The seed as Create New World makes it: a number other than zero as given, other text by its
     * hash, none (a random seed) when absent or zero.
     */
    public Long seed() {
        if (seedText == null) {
            return null;
        }
        try {
            long value = Long.parseLong(seedText);
            return value == 0L ? null : Long.valueOf(value);
        } catch (NumberFormatException notANumber) {
            return Long.valueOf(seedText.hashCode());
        }
    }

    public String getType() {
        return type;
    }

    /** The flat generator string, only for {@code type=flat}. */
    public String getFlat() {
        return flat;
    }

    public boolean isStructures() {
        return structures;
    }

    public boolean isCheats() {
        return cheats;
    }

    public String getMode() {
        return mode;
    }

    public boolean isBonusChest() {
        return bonusChest;
    }
}
