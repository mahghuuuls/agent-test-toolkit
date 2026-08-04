package com.mahghuuuls.agenttesttoolkit.command;

import com.mahghuuuls.agenttesttoolkit.bundle.ContextSource;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import net.minecraft.command.ICommandSender;

/**
 * Reads the current side and world tick from the sender that started the bundle.
 *
 * <p>Read afresh each time rather than captured once, so a record written when the bundle ends
 * carries the tick it actually ended on. See {@link ContextSource}.
 */
public final class SenderContextSource implements ContextSource {

    private final ICommandSender sender;

    public SenderContextSource(ICommandSender sender) {
        this.sender = sender;
    }

    @Override
    public RecordContext.Snapshot snapshot() {
        return RecordContext.snapshot(sender);
    }
}
