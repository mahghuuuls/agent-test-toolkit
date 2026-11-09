package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.command.SubCommand;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.SessionStamp;
import com.mahghuuuls.agenttesttoolkit.world.WorldSave;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.MinecraftException;

/**
 * Saves player data and every loaded world on either server type.
 *
 * <p>Vanilla's {@code save-all} exists on a dedicated server only, so a single-player bundle
 * that needed a save boundary before a relog had none. This one runs anywhere, respects a
 * {@code save-off} typed earlier, and writes a {@code SAVE} record a reader can find.
 */
public final class SaveSubCommand implements SubCommand {

    /** Failure key for a world whose save threw. Tolerable by declaration. */
    public static final String KEY_FAILED = "devtool.save.failed";

    @Override
    public String getName() {
        return "save";
    }

    @Override
    public String getDescription() {
        return "Save player data and every loaded world";
    }

    @Override
    public String getUsage() {
        return "save";
    }

    @Override
    public boolean requiresPlayer() {
        return false;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        long start = System.nanoTime();
        java.util.List<Integer> saved;
        try {
            saved = WorldSave.saveAll(server);
        } catch (MinecraftException e) {
            ToolkitLog.error("Save failed", e.getMessage());
            throw new CommandException(KEY_FAILED, e.getMessage());
        }
        long durationMs = (System.nanoTime() - start) / 1000000L;

        LogRecord record = RecordContext.stamp(LogRecord.of(EventType.SAVE), sender);
        SessionStamp.apply(record);
        int worlds = saved.size();
        StringBuilder dimensions = new StringBuilder();
        for (Integer dimension : saved) {
            dimensions.append(dimensions.length() == 0 ? "" : ",").append(dimension);
        }
        record.add("worlds", worlds);
        // Which worlds, since Forge unloads an empty Nether or End and the count varies.
        record.add("dimensions", dimensions.toString());
        record.add("durationMs", durationMs);
        if (sender instanceof net.minecraft.entity.player.EntityPlayerMP) {
            addPlayerFile(server, (net.minecraft.entity.player.EntityPlayerMP) sender, record);
        }
        ToolkitLog.write(record);
        sender.sendMessage(new TextComponentString(
                "[DevToolkit] Saved " + worlds + " world" + (worlds == 1 ? "" : "s") + "."));
    }

    /**
     * Names the file the save wrote for the player who asked: {@code playerdata/<uuid>.dat} under
     * the world folder, with its size and time, so a test can read or copy it at once. In single
     * player the host's data is also in {@code level.dat}, but only when the host's name equals
     * the owner's exactly, with case: the rule the game itself reads by. A missing file adds
     * nothing.
     */
    private static void addPlayerFile(MinecraftServer server, net.minecraft.entity.player.EntityPlayerMP player,
                                      LogRecord record) {
        net.minecraft.world.WorldServer overworld = server.getWorld(0);
        if (overworld == null) {
            return;
        }
        java.io.File folder = overworld.getSaveHandler().getWorldDirectory();
        java.io.File file = new java.io.File(new java.io.File(folder, "playerdata"), player.getUniqueID() + ".dat");
        if (!file.isFile()) {
            return;
        }
        record.add("playerFile", "playerdata/" + file.getName());
        record.add("playerFileSize", file.length());
        record.add("playerFileModifiedMillis", file.lastModified());
        if (!server.isDedicatedServer() && player.getName().equals(server.getServerOwner())) {
            record.add("hostDataInLevel", true);
        }
    }
}
