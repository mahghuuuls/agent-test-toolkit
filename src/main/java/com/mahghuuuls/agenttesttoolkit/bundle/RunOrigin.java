package com.mahghuuuls.agenttesttoolkit.bundle;

/**
 * Who started a bundle run, through which channel, and at what permission level.
 *
 * <p>Given by whoever submits the execution and written on {@code BUNDLE_START}, so a run's
 * authority is read from the evidence rather than inferred from whether it worked. Nothing in
 * execution reads it. Held as plain strings so this class needs no Minecraft type;
 * the command layer decides the channel.
 */
public final class RunOrigin {

    /** Typed in chat by a player. */
    public static final String CHAT = "chat";
    /** Typed at the server console. RCON cannot start a run: it calls from its own thread. */
    public static final String CONSOLE = "console";
    /** Run by a command block or a command block minecart. */
    public static final String COMMAND_BLOCK = "commandBlock";
    /** Started by join automation. */
    public static final String JOIN = "join";
    /** Requested through the command inbox. */
    public static final String INBOX = "inbox";
    /** Run by another bundle. */
    public static final String NESTED = "nested";
    /** A sender none of the above describes. Said plainly rather than guessed at. */
    public static final String OTHER = "other";

    private final String caller;
    private final String channel;
    private final int permissionLevel;
    private final String requestId;

    public RunOrigin(String caller, String channel, int permissionLevel) {
        this(caller, channel, permissionLevel, null);
    }

    /**
     * @param requestId the id of the inbox request that started the run, or of the run it is
     *                  nested in; null when no inbox request started it
     */
    public RunOrigin(String caller, String channel, int permissionLevel, String requestId) {
        if (channel == null || channel.isEmpty()) {
            throw new IllegalArgumentException("a run origin must name its channel");
        }
        this.caller = caller;
        this.channel = channel;
        this.permissionLevel = permissionLevel;
        this.requestId = requestId;
    }

    public String getCaller() {
        return caller;
    }

    public String getChannel() {
        return channel;
    }

    public int getPermissionLevel() {
        return permissionLevel;
    }

    /** The inbox request id this run answers to, or null. */
    public String getRequestId() {
        return requestId;
    }

    /**
     * The request id for a new run. A top-level run takes the id of the inbox request being
     * dispatched; a nested run takes its parent's, since it starts in a later tick, after that
     * dispatch has ended.
     */
    public static String requestIdFor(boolean nested, RunOrigin parentOrigin, String dispatchingRequestId) {
        if (!nested) {
            return dispatchingRequestId;
        }
        return parentOrigin == null ? null : parentOrigin.getRequestId();
    }
}
