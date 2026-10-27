package com.mahghuuuls.agenttesttoolkit.integration;

import java.util.Map;

/**
 * The optional integrations present in this game (ARC-018).
 *
 * <p>Decided once, after every mod has loaded. The Baubles bridge's implementation is loaded
 * by name and only when Baubles is installed, so without Baubles no class that refers to it is
 * ever loaded.
 */
public final class Integrations {

    public static final String BAUBLES_MOD_ID = "baubles";
    static final String BAUBLES_IMPL = "com.mahghuuuls.agenttesttoolkit.integration.baubles.BaublesBridgeImpl";

    private static volatile BaublesBridge baubles;

    private Integrations() {
    }

    /**
     * Called once at post-initialisation.
     *
     * @param baublesLoaded {@code Loader.isModLoaded("baubles")}
     * @return null when ready, or why the bridge could not be made
     */
    public static String init(boolean baublesLoaded) {
        if (!baublesLoaded) {
            baubles = null;
            return null;
        }
        try {
            baubles = (BaublesBridge) Class.forName(BAUBLES_IMPL).newInstance();
            return null;
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            // A Baubles too different from the one compiled against: treated as absent, said once.
            baubles = null;
            return String.valueOf(e);
        }
    }

    /** The Baubles bridge, or null when Baubles is not installed. */
    public static BaublesBridge baubles() {
        return baubles;
    }

    /** For tests only. */
    public static void setBaublesForTesting(BaublesBridge bridge) {
        baubles = bridge;
    }

    /**
     * Names a bauble slot from the types that accept it: the first type other than
     * {@code trinket}, which accepts every slot; {@code trinket} when nothing else does.
     *
     * @param types type name, lower case, to its valid slots, in Baubles' declared order
     */
    public static String slotTypeName(int slot, Map<String, int[]> types) {
        String fallback = null;
        for (Map.Entry<String, int[]> type : types.entrySet()) {
            for (int valid : type.getValue()) {
                if (valid != slot) {
                    continue;
                }
                if (!"trinket".equals(type.getKey())) {
                    return type.getKey();
                }
                fallback = type.getKey();
            }
        }
        return fallback == null ? "unknown" : fallback;
    }
}
