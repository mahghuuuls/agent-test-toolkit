package com.mahghuuuls.agenttesttoolkit.world;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.List;

/**
 * Saves player data and every loaded world, the way vanilla {@code save-all} does.
 *
 * <p>Exists because vanilla registers {@code save-all} on a dedicated server only, so a
 * single-player bundle had no way to place a save boundary. The steps are vanilla's own:
 * player data first, then each world with its saving-disabled flag lifted for the save and put
 * back afterwards, so a {@code save-off} typed earlier still holds. Unlike vanilla, the flag is
 * put back even when a world's save throws, so a failed save never turns saving back on.
 *
 * <p>Not flushed to disk synchronously, matching {@code save-all} without arguments.
 */
public final class WorldSave {

    /** One world's save, reduced to what the routine needs, so it can be tested without one. */
    public interface Target {
        int getDimension();

        boolean isSavingDisabled();

        void setSavingDisabled(boolean disabled);

        void save() throws MinecraftException;
    }

    private WorldSave() {
    }

    /**
     * Saves the players, then each target, restoring each target's flag.
     *
     * @param players run first; may be null when there is no player list
     * @return the dimension ids of the worlds saved, in save order. Forge unloads an empty
     *         Nether or End, so a quiet single-player world often saves only the overworld.
     * @throws MinecraftException from the first world whose save failed; later worlds are not
     *                            saved, as in vanilla
     */
    public static List<Integer> saveAll(Runnable players, List<Target> worlds) throws MinecraftException {
        if (players != null) {
            players.run();
        }
        List<Integer> saved = new ArrayList<Integer>();
        for (Target world : worlds) {
            boolean disabled = world.isSavingDisabled();
            world.setSavingDisabled(false);
            try {
                world.save();
            } finally {
                world.setSavingDisabled(disabled);
            }
            saved.add(world.getDimension());
        }
        return saved;
    }

    /** {@link #saveAll} over a running server's player list and loaded worlds. */
    public static List<Integer> saveAll(final MinecraftServer server) throws MinecraftException {
        Runnable players = server.getPlayerList() == null ? null : new Runnable() {
            @Override
            public void run() {
                server.getPlayerList().saveAllPlayerData();
            }
        };
        List<Target> worlds = new ArrayList<Target>();
        for (final WorldServer world : server.worlds) {
            if (world == null) {
                continue;
            }
            worlds.add(new Target() {
                @Override
                public int getDimension() {
                    return world.provider.getDimension();
                }

                @Override
                public boolean isSavingDisabled() {
                    return world.disableLevelSaving;
                }

                @Override
                public void setSavingDisabled(boolean disabled) {
                    world.disableLevelSaving = disabled;
                }

                @Override
                public void save() throws MinecraftException {
                    world.saveAllChunks(true, null);
                }
            });
        }
        return saveAll(players, worlds);
    }
}
