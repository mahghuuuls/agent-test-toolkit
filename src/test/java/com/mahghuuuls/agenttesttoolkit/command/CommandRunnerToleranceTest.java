package com.mahghuuuls.agenttesttoolkit.command;

import com.mahghuuuls.agenttesttoolkit.bundle.CommandOutcome;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandManager;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.GameType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The runner hands the command's name and arguments to the classifier, on both of its paths:
 * a command that fails as it runs, and a failure raised while its selector is expanded. Driven
 * through {@link CommandRunner#run} with fake commands, so a runner that stopped passing either
 * would make a {@code kill} that matched nothing fail, or an {@code effect ... clear} with
 * nothing on fail.
 */
class CommandRunnerToleranceTest {

    private static final String NOT_FOUND = CommandOutcomes.KEY_SELECTOR_NOT_FOUND;

    /**
     * A server that offers only its command manager. It is made without running its
     * constructor, which would start the game's networking and save handling; the runner asks
     * it for nothing else.
     */
    abstract static class CommandsOnlyServer extends MinecraftServer {
        CommandsOnlyServer() {
            super(null, null, null, null, null, null, null);
        }
    }

    static final class FakeServer extends CommandsOnlyServer {
        ICommandManager manager;

        @Override
        public ICommandManager getCommandManager() {
            return manager;
        }

        @Override
        public boolean init() {
            return false;
        }

        @Override
        public boolean canStructuresSpawn() {
            return false;
        }

        @Override
        public GameType getGameType() {
            return GameType.CREATIVE;
        }

        @Override
        public EnumDifficulty getDifficulty() {
            return EnumDifficulty.PEACEFUL;
        }

        @Override
        public boolean isHardcore() {
            return false;
        }

        @Override
        public int getOpPermissionLevel() {
            return 4;
        }

        @Override
        public boolean shouldBroadcastRconToOps() {
            return false;
        }

        @Override
        public boolean shouldBroadcastConsoleToOps() {
            return false;
        }

        @Override
        public boolean isDedicatedServer() {
            return false;
        }

        @Override
        public boolean shouldUseNativeTransport() {
            return false;
        }

        @Override
        public boolean isCommandBlockEnabled() {
            return false;
        }

        @Override
        public String shareToLAN(GameType type, boolean allowCheats) {
            return null;
        }
    }

    /** Raises a checked exception from a method that does not declare it, as vanilla's selector parsing can. */
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void raise(Throwable t) throws T {
        throw (T) t;
    }

    /**
     * A command that fails with a key as it runs, or, when it reads its first argument as a
     * selector, while that selector is expanded: the runner's other path.
     */
    private static final class FailingCommand extends CommandBase {
        private final String name;
        private final String key;
        private final boolean failsInExpansion;

        FailingCommand(String name, String key, boolean failsInExpansion) {
            this.name = name;
            this.key = key;
            this.failsInExpansion = failsInExpansion;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return name;
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 0;
        }

        @Override
        public boolean isUsernameIndex(String[] args, int index) {
            if (failsInExpansion && index == 0) {
                CommandRunnerToleranceTest.<RuntimeException>raise(new CommandException(key, args[0]));
            }
            return false;
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            throw new CommandException(key, (Object[]) args);
        }
    }

    private static final class Manager implements ICommandManager {
        final Map<String, ICommand> commands = new HashMap<String, ICommand>();

        @Override
        public int executeCommand(ICommandSender sender, String rawCommand) {
            throw new UnsupportedOperationException("the runner dispatches itself");
        }

        @Override
        public List<String> getTabCompletions(ICommandSender sender, String input, BlockPos pos) {
            return Collections.emptyList();
        }

        @Override
        public List<ICommand> getPossibleCommands(ICommandSender sender) {
            return Collections.emptyList();
        }

        @Override
        public Map<String, ICommand> getCommands() {
            return commands;
        }
    }

    private static final class Sender implements ICommandSender {
        @Override
        public String getName() {
            return "tester";
        }

        @Override
        public boolean canUseCommand(int permLevel, String commandName) {
            return true;
        }

        @Override
        public net.minecraft.world.World getEntityWorld() {
            return null;
        }

        @Override
        public MinecraftServer getServer() {
            return null;
        }
    }

    private static CommandOutcome run(ICommand command, String text, Collection<String> declared) throws Exception {
        // The fake server is created without its constructor, so only its command list is set up.
        // If CommandRunner starts calling another server method, these tests fail inside that method,
        // not because of the tolerance rules; set up the missing part here first.
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        java.lang.reflect.Field field = unsafeType.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe = field.get(null);
        FakeServer server = (FakeServer) unsafeType.getMethod("allocateInstance", Class.class)
                .invoke(unsafe, FakeServer.class);
        Manager manager = new Manager();
        manager.commands.put(command.getName(), command);
        server.manager = manager;
        return new CommandRunner(server).run(new Sender(), text, declared);
    }

    private static CommandOutcome run(ICommand command, String text) throws Exception {
        return run(command, text, Collections.<String>emptySet());
    }

    @Test
    @DisplayName("a kill whose selector matched nothing is tolerated as built in; a testfor that did fails with the key")
    void nameReachesTheClassifier() throws Exception {
        CommandOutcome kill = run(new FailingCommand("kill", NOT_FOUND, false), "kill @e[tag=nothing_here]");
        assertTrue(kill.succeeded());
        assertEquals(NOT_FOUND, kill.getToleratedKey());
        assertEquals(CommandOutcome.SOURCE_BUILT_IN, kill.getToleratedSource());

        CommandOutcome testfor = run(new FailingCommand("testfor", NOT_FOUND, false), "testfor @e[tag=nothing_here]");
        assertFalse(testfor.succeeded());
        assertEquals(NOT_FOUND, testfor.getDetail());
        assertNull(testfor.getToleratedKey());
    }

    @Test
    @DisplayName("the arguments reach the classifier: effect with clear is tolerated, effect removing one effect is not")
    void argumentsReachTheClassifier() throws Exception {
        String notActive = CommandOutcomes.KEY_EFFECT_NOT_ACTIVE_ALL;
        assertEquals(CommandOutcome.SOURCE_BUILT_IN,
                run(new FailingCommand("effect", notActive, false), "/effect @s clear").getToleratedSource());
        assertFalse(run(new FailingCommand("effect", CommandOutcomes.KEY_EFFECT_NOT_ACTIVE, false),
                "effect @s minecraft:speed 0").succeeded());
    }

    @Test
    @DisplayName("a failure while the selector is expanded is classified with the name and arguments too")
    void expansionPathCarriesTheCommand() throws Exception {
        CommandOutcome kill = run(new FailingCommand("kill", NOT_FOUND, true), "kill @e[tag=nothing_here]");
        assertTrue(kill.succeeded());
        assertEquals(CommandOutcome.SOURCE_BUILT_IN, kill.getToleratedSource());
        assertEquals(CommandOutcome.SOURCE_BUILT_IN, run(new FailingCommand("effect",
                CommandOutcomes.KEY_EFFECT_NOT_ACTIVE_ALL, true), "effect @s clear").getToleratedSource());
        assertFalse(run(new FailingCommand("effect", CommandOutcomes.KEY_EFFECT_NOT_ACTIVE, true),
                "effect @s minecraft:speed 0").succeeded());
        assertFalse(run(new FailingCommand("tp", NOT_FOUND, true), "tp @e[tag=nothing_here] 0 64 0").succeeded());
        CommandOutcome declared = run(new FailingCommand("tp", NOT_FOUND, true), "tp @e[tag=nothing_here] 0 64 0",
                Collections.singleton(NOT_FOUND));
        assertEquals(CommandOutcome.SOURCE_DECLARED, declared.getToleratedSource());
    }
}
