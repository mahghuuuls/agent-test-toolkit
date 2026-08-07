package com.mahghuuuls.agenttesttoolkit.command;

import com.mahghuuuls.agenttesttoolkit.command.sub.SessionSubCommand;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Commands that cannot do their work must throw rather than return.
 *
 * <p>These exist because of a family of defects that all looked harmless in isolation. A
 * command that reported its problem to the log and to the sender, then returned normally, was
 * counted by vanilla's command manager as a <b>success</b>: {@code executeCommand} returns a
 * count, and a method that does not throw contributes to it.
 *
 * <p>Inside a bundle that is not cosmetic. It was observed in game: a bundle with
 * {@code stopOnFailure} enabled ran straight past a mistyped subcommand and reported
 * {@code failed=0}, because the command had "succeeded". A setup routine with a typo therefore
 * builds half an environment and says nothing is wrong, which is the exact failure the toolkit
 * exists to make visible.
 *
 * <p>Ten such sites were found and fixed in the subcommands. These three were missed and found
 * later by running the mod: two in the dispatcher that routes to those subcommands, and one in
 * {@code session}, which had no {@code return} at all and simply fell out of an if-else chain.
 *
 * <h2>What these cover</h2>
 *
 * <p>Only the paths reachable without a world. Every case here is refused before the command
 * touches a server, which is why a stub sender is enough. The successful paths need Minecraft's
 * bootstrap and are covered in game.
 */
class FalseSuccessTest {

    /** A sender that speaks for no entity: a console, or a command block. */
    private static final class StubSender implements ICommandSender {
        @Override
        public String getName() {
            return "stub";
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
        public Entity getCommandSenderEntity() {
            return null;
        }

        @Override
        public net.minecraft.server.MinecraftServer getServer() {
            return null;
        }
    }

    private static ICommandSender console() {
        return new StubSender();
    }

    @Test
    @DisplayName("an unknown subcommand throws instead of returning quietly")
    void unknownSubcommandThrows() {
        // The defect this pins: returning here let a bundle count the typo as a success and
        // carry on, defeating stopOnFailure entirely.
        assertThrows(CommandException.class,
                () -> new DevToolCommand().execute(null, console(), new String[]{"notasubcommand"}));
    }

    @Test
    @DisplayName("the unknown-subcommand message names the offending word")
    void unknownSubcommandIsIdentifiable() throws Exception {
        CommandException e = assertThrows(CommandException.class,
                () -> new DevToolCommand().execute(null, console(), new String[]{"notasubcommand"}));
        // An agent reading the failure has to be able to tell which command was wrong without
        // re-running anything.
        org.junit.jupiter.api.Assertions.assertTrue(
                e.getMessage().contains("notasubcommand"), e.getMessage());
    }

    @Test
    @DisplayName("a subcommand needing a player throws when the sender is not one")
    void requiresPlayerThrows() {
        // 'entities' anchors its radius to the invoking player, so there is no sensible console
        // behaviour. Returning here reported success for a listing that never happened.
        assertThrows(CommandException.class,
                () -> new DevToolCommand().execute(null, console(),
                        new String[]{"entities", "nearby", "8"}));
    }

    @Test
    @DisplayName("an unknown session action throws instead of falling out of the chain")
    void unknownSessionActionThrows() {
        // This one had no 'return' to find. It was the last branch of an if-else chain and
        // simply ended, which reads as deliberate and behaves identically to the others.
        assertThrows(CommandException.class,
                () -> new SessionSubCommand().execute(null, console(),
                        new String[]{"notanaction"}));
    }

    @Test
    @DisplayName("the bare command is help, not an error")
    void bareCommandIsHelp() {
        // The opposite guarantee, asserted so a later fix for the cases above cannot turn the
        // bare command into a failure. Someone typing the root name should be shown the way
        // forward rather than a usage error.
        assertDoesNotThrow(
                () -> new DevToolCommand().execute(null, console(), new String[0]));
    }
}
