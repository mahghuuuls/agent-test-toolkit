package com.mahghuuuls.agenttesttoolkit.proxy;

/**
 * The server side of the sided proxy, and the reason the split exists.
 *
 * <p>REQ-147. Client only classes must never be reachable from common code, because a
 * dedicated server does not have them: touching one throws {@code NoClassDefFoundError} at
 * <b>load time</b>, before anything can catch it, and the server simply fails to start. It is
 * the most common way a Forge 1.12.2 mod breaks a dedicated server.
 *
 * <p>This class does nothing on purpose. Everything client specific lives in
 * {@code ClientProxy}, which Forge instantiates only on a physical client, and which is named
 * as a string in the {@code @SidedProxy} annotation rather than referenced as a type. That
 * string is the boundary: nothing on the server path ever mentions the client class.
 *
 * <p>This project's dedicated server surface is its least verified, since external multiplayer
 * testing is under an accepted waiver. The split is therefore structural rather than something
 * to be careful about.
 */
public class CommonProxy {

    /** Called from mod initialization. Does nothing on a server. */
    public void applyClientDefaults() {
    }
}
