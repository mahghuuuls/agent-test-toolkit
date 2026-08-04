package com.mahghuuuls.agenttesttoolkit.command;

import com.mahghuuuls.agenttesttoolkit.command.sub.BundleSubCommand;
import com.mahghuuuls.agenttesttoolkit.command.sub.HelpSubCommand;
import com.mahghuuuls.agenttesttoolkit.command.sub.InspectSubCommand;
import com.mahghuuuls.agenttesttoolkit.command.sub.LogSubCommand;
import com.mahghuuuls.agenttesttoolkit.command.sub.MarkSubCommand;
import com.mahghuuuls.agenttesttoolkit.command.sub.ReloadSubCommand;
import com.mahghuuuls.agenttesttoolkit.command.sub.RunSubCommand;
import com.mahghuuuls.agenttesttoolkit.command.sub.SessionSubCommand;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The toolkit's single root command.
 *
 * <p>REQ-001 fixes one root command plus one unique alias. The alias is insurance: in a pack
 * of several hundred mods a generic name like {@code devtool} could plausibly be claimed by
 * another mod, and a tester who cannot reach the root command has no way to diagnose why.
 * Whether the alias actually survives such a collision is still open, tracked as IMP-019.
 *
 * <p>Every subcommand requires operator permission and there is exactly one tier, per REQ-003.
 * Permission level 2 is used, matching vanilla gameplay commands such as {@code /gamemode}
 * and {@code /summon}, which is the level a tester already holds.
 */
public final class DevToolCommand extends CommandBase {

    public static final String NAME = "devtool";
    public static final String ALIAS = "att";

    /** Permission level 2, matching vanilla operator gameplay commands. */
    private static final int PERMISSION_LEVEL = 2;

    private final Map<String, SubCommand> subCommands = new LinkedHashMap<String, SubCommand>();

    public DevToolCommand() {
        register(new MarkSubCommand());
        register(new SessionSubCommand());
        register(new InspectSubCommand());
        register(new LogSubCommand());
        register(new BundleSubCommand());
        register(new RunSubCommand());
        register(new ReloadSubCommand());
        // Help receives the live map view rather than a copy, so it lists every registered
        // subcommand including itself, and stays correct as later issues add more.
        register(new HelpSubCommand(subCommands.values()));
    }

    private void register(SubCommand sub) {
        subCommands.put(sub.getName(), sub);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList(ALIAS);
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/devtool <subcommand>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return PERMISSION_LEVEL;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        // REQ-002: the bare command behaves as help rather than as an error. An agent or a
        // human who types the root name should be shown the way forward, not a usage failure.
        String name = (args.length == 0) ? "help" : args[0].toLowerCase(java.util.Locale.ROOT);

        SubCommand sub = subCommands.get(name);
        if (sub == null) {
            // REQ-110: never silent. The error reaches the log with enough context to identify
            // the cause, and the sender gets a short message.
            ToolkitLog.error("Unknown subcommand", name);
            sender.sendMessage(new TextComponentString(
                    "[DevToolkit] Unknown subcommand: " + name + ". Try /devtool help"));
            return;
        }

        // REQ-005: fail explicitly rather than obscurely when a player sender is required.
        if (sub.requiresPlayer() && !(sender instanceof EntityPlayer)) {
            ToolkitLog.error("Subcommand requires a player sender", sub.getName());
            sender.sendMessage(new TextComponentString(
                    "[DevToolkit] /devtool " + sub.getName() + " requires a player sender."));
            return;
        }

        String[] subArgs = (args.length <= 1)
                ? new String[0]
                : Arrays.copyOfRange(args, 1, args.length);

        sub.execute(server, sender, subArgs);
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                          String[] args, @Nullable BlockPos targetPos) {
        if (args.length <= 1) {
            return getListOfStringsMatchingLastWord(args, subCommands.keySet());
        }
        SubCommand sub = subCommands.get(args[0].toLowerCase(java.util.Locale.ROOT));
        if (sub == null) {
            return Collections.emptyList();
        }
        return sub.getTabCompletions(server, sender, Arrays.copyOfRange(args, 1, args.length));
    }
}
