package com.mahghuuuls.agenttesttoolkit.env;

import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.state.SessionStamp;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import java.util.ArrayList;
import java.util.List;

/**
 * Reports what the toolkit is running inside.
 *
 * <p>The mod list is deliberately <b>not</b> part of the environment summary: the runtimes
 * this toolkit targets carry 350 to 389 mods, and folding that into one record would make the
 * summary unreadable for the case it exists to serve. It gets its own command.
 *
 * <p>The startup log is never dumped either. This reports named values only.
 */
public final class Environment {

    private Environment() {
    }

    public static LogRecord environment(MinecraftServer server, ICommandSender sender) {
        // Stamped here rather than by the caller so side and worldTick lead the record. Field
        // order is insertion order, and adding them afterwards would put them at the end.
        LogRecord record = RecordContext.stamp(LogRecord.of(EventType.ENVIRONMENT), sender);
        SessionStamp.apply(record);
        record.add("minecraftVersion", ForgeVersion.mcVersion)
                .add("forgeVersion", ForgeVersion.getVersion());

        if (server != null) {
            // "Context" in the requirement: an integrated server behaves differently from a
            // dedicated one in ways that matter when reading a log, so it is stated rather
            // than left to be inferred from which fields happen to be present.
            record.add("serverContext", server.isDedicatedServer() ? "DEDICATED" : "INTEGRATED");
        }

        World world = sender == null ? null : sender.getEntityWorld();
        if (world != null) {
            record.add("dimension", world.provider.getDimension());
            record.add("difficulty", world.getDifficulty().name());
        }

        Entity entity = sender == null ? null : sender.getCommandSenderEntity();
        if (entity != null) {
            record.addEntityPos("pos", entity.posX, entity.posY, entity.posZ);
        }
        return record;
    }

    /**
     * One record per loaded mod.
     *
     * <p>One line each rather than a single joined field, because with several hundred mods a
     * joined value would be a single enormous log line that no tail or grep window shows
     * usefully. Each record carries the total so a truncated read is still self-describing.
     */
    public static List<LogRecord> modList(ICommandSender sender) {
        List<ModContainer> mods = Loader.instance().getActiveModList();
        List<LogRecord> records = new ArrayList<LogRecord>();
        int total = mods == null ? 0 : mods.size();

        if (total == 0) {
            // Cannot happen in a real runtime, since Forge and Minecraft are themselves mod
            // containers, but an empty list must still say so rather than emit nothing and be
            // mistaken for a command that did not run.
            records.add(stamped(sender).add("modCount", 0));
            return records;
        }

        for (ModContainer mod : mods) {
            records.add(stamped(sender)
                    .add("modId", mod.getModId())
                    .add("modVersion", mod.getVersion())
                    .add("modName", mod.getName())
                    .add("modCount", total));
        }
        return records;
    }

    /** Side and world tick lead every record. */
    private static LogRecord stamped(ICommandSender sender) {
        LogRecord record = RecordContext.stamp(LogRecord.of(EventType.CAPABILITIES), sender);
        SessionStamp.apply(record);
        return record;
    }
}
