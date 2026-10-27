package com.mahghuuuls.agenttesttoolkit.inbox;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The inbox folder: which requests are waiting, and claiming one.
 *
 * <p>The agent writes {@code <id>.tmp} and renames it to {@code <id>.req} when complete, so a
 * half-written request never has the name the toolkit reads. The toolkit claims a request by
 * renaming it to {@code <id>.claimed} before reading it, so it runs at most once even if two
 * polls see it. A rename that fails, for example because another program holds the file, is
 * left for the next poll.
 */
public final class InboxFiles {

    public static final String REQUEST_SUFFIX = ".req";
    public static final String CLAIMED_SUFFIX = ".claimed";

    /** Largest request file read. Anything bigger is read as malformed, never as a command. */
    static final int MAX_FILE_BYTES = 4096;

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final File directory;

    public InboxFiles(File directory) {
        this.directory = directory;
    }

    public File getDirectory() {
        return directory;
    }

    /** Creates the folder when missing. Called on open, never while closed. */
    public void ensureDirectory() {
        if (directory != null && !directory.isDirectory()) {
            directory.mkdirs();
        }
    }

    /** Ids of waiting requests, in file-name order. Files with another name are ignored. */
    public List<String> pendingIds() {
        List<String> ids = new ArrayList<String>();
        if (directory == null) {
            return ids;
        }
        File[] files = directory.listFiles();
        if (files == null) {
            return ids;
        }
        for (File file : files) {
            String name = file.getName();
            if (!name.endsWith(REQUEST_SUFFIX) || Files.isSymbolicLink(file.toPath())
                    || !file.isFile()) {
                // A link is never followed: a request is a file the agent wrote here.
                continue;
            }
            String id = name.substring(0, name.length() - REQUEST_SUFFIX.length());
            if (InboxRequest.isValidId(id)) {
                ids.add(id);
            }
        }
        Collections.sort(ids);
        return ids;
    }

    /** A claimed request: its id and its content, or null content when unreadable. */
    public static final class Claim {
        private final String id;
        private final String content;

        Claim(String id, String content) {
            this.id = id;
            this.content = content;
        }

        public String getId() {
            return id;
        }

        /** @return the file's text, or null when it was too large or could not be read */
        public String getContent() {
            return content;
        }
    }

    /**
     * Claims one request by renaming it, then reads it.
     *
     * @return the claim, or null when the rename did not happen and the request stays waiting
     */
    public Claim claim(String id) {
        File request = new File(directory, id + REQUEST_SUFFIX);
        File claimed = new File(directory, id + CLAIMED_SUFFIX);
        if (claimed.exists() || !request.renameTo(claimed)) {
            return null;
        }
        try {
            return new Claim(id, readBounded(claimed));
        } catch (IOException e) {
            return new Claim(id, null);
        }
    }

    /** True when this id was already claimed, so a new request with it can never run. */
    public boolean isAlreadyClaimed(String id) {
        return new File(directory, id + CLAIMED_SUFFIX).exists();
    }

    /**
     * Reads a waiting request without claiming it, for the record of a request that cannot be
     * claimed. Null when it cannot be read or is over the cap.
     */
    public String peek(String id) {
        try {
            return readBounded(new File(directory, id + REQUEST_SUFFIX));
        } catch (IOException e) {
            return null;
        }
    }

    /** Reads at most one byte past the cap, so a file that grows while read is still bounded. */
    private static String readBounded(File file) throws IOException {
        java.io.InputStream in = new java.io.FileInputStream(file);
        try {
            byte[] buffer = new byte[MAX_FILE_BYTES + 1];
            int total = 0;
            int read;
            while (total < buffer.length && (read = in.read(buffer, total, buffer.length - total)) > 0) {
                total += read;
            }
            if (total > MAX_FILE_BYTES) {
                return null;
            }
            return new String(buffer, 0, total, UTF8);
        } finally {
            in.close();
        }
    }
}
