package com.mahghuuuls.agenttesttoolkit.command;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Resolves the player behind a command sender.
 *
 * <h2>Why this exists</h2>
 *
 * <p>A sender is not always the player, and it is not always an entity. The toolkit's own
 * subcommands need one answer to "is there a player here, and who", covering a player sending
 * directly, an entity-backed sender, the server console, and a command block. Asking
 * {@code getCommandSenderEntity()} rather than testing a type covers all four, and gives null
 * rather than throwing for the two that have no player.
 *
 * <h2>What this is no longer for</h2>
 *
 * <p>This class was previously also load-bearing for a defect: bundle commands were dispatched
 * through a wrapper that was not an {@code EntityPlayer}, so {@code instanceof} was false for
 * every command a bundle ran, and routing the question through here was what hid that. The
 * wrapper is gone. Bundle commands now run as the caller's own object, so a direct type test
 * would work too.
 *
 * <p>It is kept because the question is still worth having one answer to, not because anything
 * is being worked around. <b>Do not reintroduce a substitute sender to make this necessary
 * again.</b> See ARC-008.
 */
public final class Senders {

    private Senders() {
    }

    /**
     * @return the player behind this sender, or null when there is none. Never throws.
     */
    public static EntityPlayer asPlayer(ICommandSender sender) {
        if (sender == null) {
            return null;
        }
        if (sender instanceof EntityPlayer) {
            return (EntityPlayer) sender;
        }
        Entity entity = sender.getCommandSenderEntity();
        return entity instanceof EntityPlayer ? (EntityPlayer) entity : null;
    }

    /** @return the server-side player behind this sender, or null. */
    public static EntityPlayerMP asServerPlayer(ICommandSender sender) {
        EntityPlayer player = asPlayer(sender);
        return player instanceof EntityPlayerMP ? (EntityPlayerMP) player : null;
    }

    /** @return true when a player is behind this sender, wrapped or direct. */
    public static boolean isPlayer(ICommandSender sender) {
        return asPlayer(sender) != null;
    }
}
