package com.mahghuuuls.agenttesttoolkit.command;

import net.minecraft.command.CommandResultStats;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

/**
 * Delegates everything to the real sender while noting the last translation key sent to it.
 *
 * <h2>Why this exists</h2>
 *
 * <p>{@code CommandHandler.executeCommand} never throws. It catches {@link
 * net.minecraft.command.CommandException} internally, sends the message to the sender, and
 * returns a success count. A return of zero therefore covers unknown command, missing
 * permission, bad syntax, and a selector that matched nothing, with no way to tell them apart
 * from the return value.
 *
 * <p>That last case must not count as a failure. Failure means a raised command error; a
 * command that runs and affects nothing has still run, and a bundle ending in
 * {@code kill @e[name=...]} would otherwise halt on its second run under the default
 * {@code stopOnFailure}. Reading the key vanilla already sends is the only way to
 * separate the two, so this wrapper captures it.
 *
 * <h2>Why this does not change permissions</h2>
 *
 * <p>A sender with different permissions must never be substituted. Every method here
 * delegates, including {@link #canUseCommand}, so the command manager sees exactly the
 * permissions, position, world and identity of the original caller. The wrapper observes; it
 * decides nothing. A command a caller could not type still fails when a bundle runs it.
 */
final class ObservingSender implements ICommandSender {

    private final ICommandSender delegate;

    private String lastTranslationKey;

    ObservingSender(ICommandSender delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        this.delegate = delegate;
    }

    /** Clears the noted key, so one command's outcome cannot be read as the next one's. */
    void reset() {
        lastTranslationKey = null;
    }

    /** @return the translation key of the last message sent, or null if none carried one. */
    String getLastTranslationKey() {
        return lastTranslationKey;
    }

    @Override
    public void sendMessage(ITextComponent component) {
        // Only the key is read, never a rendered string, so this does not depend on the
        // language the server happens to be running.
        if (component instanceof TextComponentTranslation) {
            lastTranslationKey = ((TextComponentTranslation) component).getKey();
        }
        delegate.sendMessage(component);
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public ITextComponent getDisplayName() {
        return delegate.getDisplayName();
    }

    @Override
    public boolean canUseCommand(int permLevel, String commandName) {
        return delegate.canUseCommand(permLevel, commandName);
    }

    @Override
    public BlockPos getPosition() {
        return delegate.getPosition();
    }

    @Override
    public Vec3d getPositionVector() {
        return delegate.getPositionVector();
    }

    @Override
    public World getEntityWorld() {
        return delegate.getEntityWorld();
    }

    @Override
    public Entity getCommandSenderEntity() {
        return delegate.getCommandSenderEntity();
    }

    @Override
    public boolean sendCommandFeedback() {
        return delegate.sendCommandFeedback();
    }

    @Override
    public void setCommandStat(CommandResultStats.Type type, int amount) {
        delegate.setCommandStat(type, amount);
    }

    @Override
    public MinecraftServer getServer() {
        return delegate.getServer();
    }
}
