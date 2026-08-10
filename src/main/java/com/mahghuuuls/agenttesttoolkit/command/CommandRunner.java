package com.mahghuuuls.agenttesttoolkit.command;

import java.util.Arrays;
import java.util.List;

import com.mahghuuuls.agenttesttoolkit.bundle.CommandOutcome;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;

import net.minecraft.command.CommandException;
import net.minecraft.command.CommandResultStats;
import net.minecraft.command.EntitySelector;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.PlayerNotFoundException;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;

/**
 * Runs one command as a given sender and reports what happened.
 *
 * <h2>Why this does not call the command manager</h2>
 *
 * <p>{@code CommandHandler.executeCommand} never throws. It catches the failure, renders it into
 * a chat component, and sends that to the sender, returning only a success count. To tell a real
 * error from a selector that matched nothing, the toolkit needs the translation key, and the
 * only place the manager exposes it is that rendered message.
 *
 * <p>An earlier design read the key by substituting a wrapper for the sender and intercepting
 * the message. That wrapper was not an {@code EntityPlayerMP}, so every vanilla command that
 * checks its sender's concrete type failed. {@code CommandBase.getCommandSenderAsPlayer} tests
 * {@code sender instanceof EntityPlayerMP} and throws otherwise; no amount of delegation makes
 * an object a member of a class it does not extend. Eight commands were affected whenever their
 * player argument was omitted, and the wrapper produced three separate defects before this.
 *
 * <p>So the dispatch happens here instead, with the caller's own sender object, and the key is
 * taken from the {@link CommandException} that carries it. That exception is where the key
 * exists before vanilla renders it, which makes this the more direct route as well as the one
 * that preserves sender identity.
 *
 * <h2>What this owes vanilla</h2>
 *
 * <p>Replacing a dispatcher means inheriting its obligations. Four behaviours were previously
 * free and are now deliberate, and none of them is visible in the outcome of a bundle that
 * passes:
 *
 * <ol>
 *   <li>{@link CommandEvent} is posted, and its cancellation and rewritten parameters honoured,
 *       so mods that hook commands still see commands a bundle runs.</li>
 *   <li>The sender still receives the same red message vanilla would have sent. A failure that
 *       is recorded but invisible in chat is worse than the defect this replaced.</li>
 *   <li>A username-index argument is expanded the same way, including that matching nothing
 *       raises {@code commands.generic.selector.notFound}, which the tolerated-key rule needs.</li>
 *   <li>A {@link Throwable} that is not a command error is contained, so a misbehaving command
 *       from any mod cannot escape into the server tick.</li>
 * </ol>
 */
public final class CommandRunner {

    private final MinecraftServer server;

    public CommandRunner(MinecraftServer server) {
        if (server == null) {
            throw new IllegalArgumentException("server must not be null");
        }
        this.server = server;
    }

    /**
     * Runs one command as {@code sender}.
     *
     * @param sender the caller's own object, never a substitute
     * @return the outcome, classified by {@link CommandOutcomes}
     */
    public CommandOutcome run(ICommandSender sender, String rawCommand) {
        String trimmed = rawCommand.trim();
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }

        String[] parts = trimmed.split(" ");
        String name = parts[0];
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);

        ICommand command = server.getCommandManager().getCommands().get(name);
        if (command == null) {
            return refuse(sender, CommandOutcomes.KEY_COMMAND_NOT_FOUND);
        }
        if (!command.checkPermission(server, sender)) {
            return refuse(sender, CommandOutcomes.KEY_NO_PERMISSION);
        }

        // Posted before anything runs, matching vanilla's ordering. A mod that cancels here has
        // handled the command itself, which vanilla counts as a success rather than a failure.
        CommandEvent event = new CommandEvent(command, sender, args);
        if (MinecraftForge.EVENT_BUS.post(event)) {
            if (event.getException() != null) {
                return CommandOutcome.failure(describeThrowable(event.getException()));
            }
            return CommandOutcome.success("handled by another mod");
        }
        if (event.getParameters() != null) {
            args = event.getParameters();
        }

        try {
            return executeAndCount(sender, command, args, trimmed);
        } catch (CommandException e) {
            // Selector expansion failing lands here. Sending the message keeps the caller
            // informed; the key is read from the exception rather than from what was sent.
            send(sender, e.getMessage(), e.getErrorObjects());
            return CommandOutcomes.classify(0, e.getMessage());
        }
    }

    /** Expands a username-index argument when present, then runs the command once per match. */
    private CommandOutcome executeAndCount(ICommandSender sender, ICommand command,
                                           String[] args, String input) throws CommandException {
        int usernameIndex = usernameIndex(command, args);
        int successes = 0;
        String lastFailureKey = null;

        if (usernameIndex > -1) {
            List<Entity> matched =
                    EntitySelector.matchEntities(sender, args[usernameIndex], Entity.class);
            sender.setCommandStat(CommandResultStats.Type.AFFECTED_ENTITIES, matched.size());

            // Thrown, not returned. This is the key the tolerated set is built around, and it
            // must reach the classifier as an exception exactly as vanilla raises it.
            if (matched.isEmpty()) {
                throw new PlayerNotFoundException(CommandOutcomes.KEY_SELECTOR_NOT_FOUND,
                        new Object[] {args[usernameIndex]});
            }

            String original = args[usernameIndex];
            for (Entity entity : matched) {
                args[usernameIndex] = entity.getCachedUniqueIdString();
                String key = tryExecute(sender, command, args, input);
                if (key == null) {
                    successes++;
                } else {
                    lastFailureKey = key;
                }
            }
            args[usernameIndex] = original;
        } else {
            sender.setCommandStat(CommandResultStats.Type.AFFECTED_ENTITIES, 1);
            String key = tryExecute(sender, command, args, input);
            if (key == null) {
                successes++;
            } else {
                lastFailureKey = key;
            }
        }

        sender.setCommandStat(CommandResultStats.Type.SUCCESS_COUNT, successes);
        return CommandOutcomes.classify(successes, lastFailureKey);
    }

    /**
     * Runs the command once.
     *
     * @return null when it succeeded, otherwise the translation key describing the failure
     */
    private String tryExecute(ICommandSender sender, ICommand command, String[] args,
                              String input) {
        try {
            command.execute(server, sender, args);
            return null;
        } catch (WrongUsageException e) {
            // Vanilla nests the usage message inside a generic wrapper. Reproduced so the
            // caller sees the same text, while the key returned is the specific one.
            TextComponentTranslation usage =
                    new TextComponentTranslation(e.getMessage(), e.getErrorObjects());
            send(sender, "commands.generic.usage", new Object[] {usage});
            return "commands.generic.usage";
        } catch (CommandException e) {
            send(sender, e.getMessage(), e.getErrorObjects());
            return e.getMessage();
        } catch (Throwable t) {
            // Deliberately Throwable. A command from any mod can throw anything, and letting it
            // escape would take the server tick down over a diagnostic run.
            send(sender, "commands.generic.exception", new Object[0]);
            ToolkitLog.error("Command threw", input + ": " + describeThrowable(t));
            return "commands.generic.exception";
        }
    }

    /** Sends the message and classifies in one step, for the two pre-execution refusals. */
    private CommandOutcome refuse(ICommandSender sender, String key) {
        send(sender, key, new Object[0]);
        return CommandOutcomes.classify(0, key);
    }

    /** Reproduces vanilla's red translated message so a failure is still visible in chat. */
    private void send(ICommandSender sender, String key, Object[] errorObjects) {
        TextComponentTranslation message = new TextComponentTranslation(key, errorObjects);
        message.getStyle().setColor(TextFormatting.RED);
        sender.sendMessage(message);
    }

    private static String describeThrowable(Throwable t) {
        return t.getClass().getSimpleName()
                + (t.getMessage() == null ? "" : ": " + t.getMessage());
    }

    /**
     * Vanilla's rule: the first argument the command calls a username index, and only when it
     * could name more than one entity. A literal name is left alone.
     *
     * <p>Throws for the same reason vanilla's own {@code getUsernameIndex} does:
     * {@code matchesMultiplePlayers} parses the selector and a malformed one is a command error,
     * not a reason to treat the argument as a literal name.
     */
    private static int usernameIndex(ICommand command, String[] args) throws CommandException {
        for (int i = 0; i < args.length; i++) {
            if (command.isUsernameIndex(args, i) && EntitySelector.matchesMultiplePlayers(args[i])) {
                return i;
            }
        }
        return -1;
    }
}
