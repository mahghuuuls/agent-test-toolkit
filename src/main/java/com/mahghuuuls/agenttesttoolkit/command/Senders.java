package com.mahghuuuls.agenttesttoolkit.command;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Resolves the player behind a command sender, wrapped or not.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Bundle commands are dispatched through {@link ObservingSender}, a wrapper that reads the
 * translation key vanilla sends so a selector matching nothing can be told apart from a real
 * failure. The wrapper delegates everything faithfully, including permissions.
 *
 * <p>But it is not an {@code EntityPlayer}, and <b>{@code sender instanceof EntityPlayer} is
 * therefore false for every command a bundle runs.</b> That silently broke `arena create`,
 * `arena reset`, `inspect player`, `inspect inventory`, `entities nearby` and `nbt held` when
 * called from a bundle, which is where they are most useful.
 *
 * <p>Worse, it broke them <i>quietly</i>. The affected commands report the problem and return
 * normally rather than throwing, and Forge counts a normal return as success, so a bundle
 * reported {@code executed=11 failed=0} while doing nothing. The error was in the log the whole
 * time; the bundle's own summary contradicted it.
 *
 * <p>{@code getCommandSenderEntity()} is the right question to ask. An entity returns itself,
 * and the wrapper passes the call through, so this works for a direct sender and a wrapped one
 * alike. Nothing else needs to know the wrapper exists.
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
