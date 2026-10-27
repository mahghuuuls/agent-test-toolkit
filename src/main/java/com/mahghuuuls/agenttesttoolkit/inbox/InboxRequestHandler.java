package com.mahghuuuls.agenttesttoolkit.inbox;

import net.minecraft.server.MinecraftServer;

/**
 * Runs one accepted request. Implemented by the command layer, which owns command semantics;
 * this package owns only the gate, the folder, and the allow-list (ARC-014, rule 10).
 */
public interface InboxRequestHandler {

    /**
     * Runs an accepted request on the server thread.
     *
     * @param request always accepted; refused requests never reach a handler
     */
    void handle(MinecraftServer server, InboxRequest request,
                com.mahghuuuls.agenttesttoolkit.logging.RecordContext.Snapshot context);
}
