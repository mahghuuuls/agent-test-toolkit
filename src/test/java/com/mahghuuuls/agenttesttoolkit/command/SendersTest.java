package com.mahghuuuls.agenttesttoolkit.command;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Resolving the player behind a command sender.
 *
 * <p>These exist because of a defect that reached a release candidate and was found by ordinary
 * use rather than by any check here.
 *
 * <p>Bundle commands are dispatched through a wrapper that observes the messages vanilla sends.
 * The wrapper delegates everything faithfully, but it is not an {@code EntityPlayer}, so
 * {@code sender instanceof EntityPlayer} was false for every command a bundle ran. That silently
 * disabled `arena create`, `arena reset`, `inspect player`, `inspect inventory`,
 * `entities nearby` and `nbt held` from inside bundles, which is where they are most useful.
 *
 * <p>It survived twenty-four issues of verification for a reason worth recording: every bundle
 * built to test bundle execution used `mark`, `log`, `session`, `summon`, `gamerule` and `time`.
 * None of those asks whether the sender is a player. The machinery was tested exhaustively and
 * the thing the machinery is for was never tested at all.
 *
 * <h2>What these tests cannot do</h2>
 *
 * <p><b>They cannot prove the fix.</b> Constructing any {@code Entity}, let alone an
 * {@code EntityPlayerMP}, requires Minecraft's bootstrap and a world, so the positive case
 * (a wrapped player resolving correctly) is not reachable from a plain unit test. An earlier
 * version of this file tried and failed with {@code ExceptionInInitializerError}.
 *
 * <p>What is covered here is the negative half: nothing resolves to a player that should not,
 * and nothing throws. **The positive half is covered only in game**, by running a bundle that
 * contains a player-requiring command. That check is now part of the verification routine,
 * because its absence is the whole reason this defect shipped.
 */
class SendersTest {

    /** A sender that speaks for no entity: a console, or a command block. */
    private static ICommandSender withoutEntity() {
        return new StubSender();
    }

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

    @Test
    @DisplayName("a sender speaking for no entity resolves to no player")
    void senderWithoutEntityHasNoPlayer() {
        assertNull(Senders.asPlayer(withoutEntity()));
        assertNull(Senders.asServerPlayer(withoutEntity()));
        assertFalse(Senders.isPlayer(withoutEntity()));
    }

    @Test
    @DisplayName("a null sender is handled rather than throwing")
    void nullSenderIsSafe() {
        // Reached when a command is dispatched from a context with no sender at all. Returning
        // null is correct; throwing here would turn a missing player into a stack trace in the
        // middle of a bundle.
        assertNull(Senders.asPlayer(null));
        assertNull(Senders.asServerPlayer(null));
        assertFalse(Senders.isPlayer(null));
    }
}
