package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.logging.filter.FollowRadiusFilter;
import com.mahghuuuls.agenttesttoolkit.state.SessionStamp;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerList;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.UUID;

/**
 * The live player lookup a following radius filter reads: the players of the server running at
 * the moment of each check.
 *
 * <p>Nothing of a server is kept. Filters outlive the server they were set on: a single-player
 * reconnect starts a new integrated server while the filter stays, and a filter holding the old
 * server would ask a player list nobody joins any more, so it would stay paused, or report a
 * position frozen at the leave, and keep the stopped server from being collected. The running
 * server is asked for at each check, as the arena lookup asks for its world at each check.
 */
final class CurrentPlayerPlaces implements FollowRadiusFilter.PlayerPlaces {

    /** The players of one server, found at a check. */
    interface Players {
        /** The player's place, or null when not online on that server. */
        FollowRadiusFilter.Place placeOf(UUID player);
    }

    /** Gives the players of the server running now; null when none is running. */
    interface Running {
        Players players();
    }

    private final Running running;

    CurrentPlayerPlaces(Running running) {
        this.running = running;
    }

    /** Through Forge's handle on the server running now. */
    static CurrentPlayerPlaces ofRunningServer() {
        return new CurrentPlayerPlaces(new Running() {
            @Override
            public Players players() {
                MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
                final PlayerList list = server == null ? null : server.getPlayerList();
                if (list == null) {
                    return null;
                }
                return new Players() {
                    @Override
                    public FollowRadiusFilter.Place placeOf(UUID player) {
                        EntityPlayerMP online = list.getPlayerByUUID(player);
                        return online == null ? null
                                : new FollowRadiusFilter.Place(online.dimension, online.posX, online.posY, online.posZ);
                    }
                };
            }
        });
    }

    @Override
    public FollowRadiusFilter.Place placeOf(UUID player) {
        Players players = running.players();
        return players == null ? null : players.placeOf(player);
    }

    @Override
    public RecordContext.Snapshot contextOf(int dimension) {
        // DimensionManager rather than the server's getWorld, which would load an unloaded
        // dimension as a side effect of writing one log line.
        World world = net.minecraftforge.common.DimensionManager.getWorld(dimension);
        return world == null ? RecordContext.Snapshot.NONE : RecordContext.snapshot(world);
    }

    @Override
    public void write(LogRecord record) {
        SessionStamp.apply(record);
        ToolkitLog.write(record);
    }
}
