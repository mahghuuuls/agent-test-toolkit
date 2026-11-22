package com.mahghuuuls.agenttesttoolkit.wait;

import com.mahghuuuls.agenttesttoolkit.bundle.BundleExecution;
import com.mahghuuuls.agenttesttoolkit.net.ClientActionReport;
import com.mahghuuuls.agenttesttoolkit.net.ClientCapabilities;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What the server remembers of the client steps and pictures a bundle run asked for, and what the
 * clients answered, so a step can wait on its answer without the server thread ever stopping.
 *
 * <p>Per player: the steps a run waits on or may wait on later (every hold, for
 * {@code client await}), by their token, and the latest token of each held input per run. Per
 * run: the latest picture asked under each label. Nothing outside a bundle run is kept, so a step
 * typed by a person costs nothing here.
 *
 * <p>Forgotten when the run ends, and a player's steps when that player logs out: a client that
 * comes back is a new connection and never answers a token from before, so a wait on one is
 * cancelled rather than left to time out. Server thread only; the methods are synchronized so a
 * stray call from elsewhere cannot corrupt the maps.
 */
public final class ClientReports {

    /** At most this many steps are remembered per player; the oldest are forgotten first. */
    public static final int MAX_STEPS = 64;

    /** One answer from the client: its result, its reason, and its fields. */
    static final class Report {
        final String result;
        final String reason;
        final Map<String, String> fields;

        Report(String result, String reason, Map<String, String> fields) {
            this.result = result;
            this.reason = reason == null || reason.isEmpty() ? null : reason;
            this.fields = fields == null ? Collections.<String, String>emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<String, String>(fields));
        }

        String field(String name) {
            String value = fields.get(name);
            return value == null || value.isEmpty() ? null : value;
        }
    }

    /** One step sent to a client in a run: what it was, and what the client has said about it. */
    static final class Step {
        final BundleExecution execution;
        final String step;
        final long requestTick;
        /** The input a hold keeps down, {@code use}, {@code attack} or a binding name; null otherwise. */
        final String input;
        /** The entity the step expects under the crosshair; null for no expectation. */
        final String expectUuid;
        Report first;
        Report last;
        /** The latest answer that named what the crosshair was on. */
        Report target;

        Step(BundleExecution execution, String step, long requestTick, String input, String expectUuid) {
            this.execution = execution;
            this.step = step;
            this.requestTick = requestTick;
            this.input = input;
            this.expectUuid = expectUuid;
        }
    }

    /** One picture asked in a run, and the client's report of it once it comes. */
    public static final class Picture {
        final UUID player;
        final String label;
        final long requestTick;
        /** Null until the client reports. */
        Boolean saved;
        String file;
        long frameClientTick;
        /** Why the client could not save it; null when saved or when it gave none. */
        String reason;
        /** The player the picture was asked of left before it was reported. */
        boolean playerLeft;

        Picture(UUID player, String label, long requestTick) {
            this.player = player;
            this.label = label;
            this.requestTick = requestTick;
        }

        /** The player whose client takes the picture. */
        public UUID getPlayer() {
            return player;
        }
    }

    private static final Map<UUID, LinkedHashMap<String, Step>> STEPS = new HashMap<UUID, LinkedHashMap<String, Step>>();
    /** Per player, per run, the latest token of each held input. */
    private static final Map<UUID, Map<BundleExecution, Map<String, String>>> HOLDS =
            new HashMap<UUID, Map<BundleExecution, Map<String, String>>>();
    private static final Map<BundleExecution, Map<String, Picture>> PICTURES =
            new IdentityHashMap<BundleExecution, Map<String, Picture>>();
    /** The runs already told to forget their entries when they end. */
    private static final Set<BundleExecution> WATCHED =
            Collections.newSetFromMap(new IdentityHashMap<BundleExecution, Boolean>());

    private ClientReports() {
    }

    /** Listens to the clients' reports. Called once at mod start. */
    public static void install() {
        ClientCapabilities.addReportListener(new ClientCapabilities.ReportListener() {
            @Override
            public void onReport(EntityPlayerMP player, ClientActionReport report) {
                reported(player.getUniqueID(), report.getToken(), report.getResult(), report.getReason(),
                        report.getFields());
            }
        });
    }

    /**
     * Remembers a step as it is sent in a run, so the run can wait on its answer now or, for a
     * hold, later with {@code client await}.
     *
     * @param input      the input a hold keeps down; null for any other step
     * @param expectUuid the entity the step expects under the crosshair; null for none
     */
    public static synchronized void sent(BundleExecution execution, UUID player, String token, String step,
                                         long requestTick, String input, String expectUuid) {
        watch(execution);
        LinkedHashMap<String, Step> steps = STEPS.get(player);
        if (steps == null) {
            steps = new LinkedHashMap<String, Step>();
            STEPS.put(player, steps);
        }
        steps.put(token, new Step(execution, step, requestTick, input, expectUuid));
        while (steps.size() > MAX_STEPS) {
            steps.remove(steps.keySet().iterator().next());
        }
        if (input != null) {
            Map<BundleExecution, Map<String, String>> runs = HOLDS.get(player);
            if (runs == null) {
                runs = new IdentityHashMap<BundleExecution, Map<String, String>>();
                HOLDS.put(player, runs);
            }
            Map<String, String> latest = runs.get(execution);
            if (latest == null) {
                latest = new HashMap<String, String>();
                runs.put(execution, latest);
            }
            latest.put(input, token);
        }
    }

    /** The token of the latest hold of this input the run started for this player; null when none. */
    public static synchronized String latestHold(BundleExecution execution, UUID player, String input) {
        Map<BundleExecution, Map<String, String>> runs = HOLDS.get(player);
        Map<String, String> latest = runs == null ? null : runs.get(execution);
        String token = latest == null ? null : latest.get(input);
        // A token forgotten as one of the oldest is no longer one a wait can read.
        return token != null && step(player, token) != null ? token : null;
    }

    /** The step sent with this token, or null when it was never kept or has been forgotten. */
    static synchronized Step step(UUID player, String token) {
        Map<String, Step> steps = STEPS.get(player);
        return steps == null || token == null ? null : steps.get(token);
    }

    /**
     * Takes one of the client's answers. An answer to a token nobody kept is ignored: the step was
     * not in a run, or the run has ended.
     */
    static synchronized void reported(UUID player, String token, String result, String reason,
                                      Map<String, String> fields) {
        Step step = step(player, token);
        if (step == null) {
            return;
        }
        Report report = new Report(result, reason, fields);
        if (step.first == null) {
            step.first = report;
        }
        step.last = report;
        if (report.field("target") != null) {
            step.target = report;
        }
    }

    /** Remembers a picture asked in a run, replacing an earlier one with the same label. */
    public static synchronized void requestedPicture(BundleExecution execution, UUID player, String label,
                                                     long requestTick) {
        watch(execution);
        Map<String, Picture> pictures = PICTURES.get(execution);
        if (pictures == null) {
            pictures = new HashMap<String, Picture>();
            PICTURES.put(execution, pictures);
        }
        pictures.put(label, new Picture(player, label, requestTick));
    }

    /** The latest picture this run asked under this label; null when it asked none. */
    public static synchronized Picture latestPicture(BundleExecution execution, String label) {
        Map<String, Picture> pictures = PICTURES.get(execution);
        return pictures == null ? null : pictures.get(label);
    }

    /**
     * Takes a client's report of a picture: it settles every kept request of that player, label and
     * request tick, so a report that arrives before the wait is asked still counts.
     */
    public static synchronized void pictureReported(UUID player, String label, long requestTick, boolean saved,
                                                    String file, long frameClientTick, String reason) {
        for (Map<String, Picture> pictures : PICTURES.values()) {
            Picture picture = pictures.get(label);
            if (picture != null && picture.player.equals(player) && picture.requestTick == requestTick
                    && picture.saved == null) {
                picture.saved = Boolean.valueOf(saved);
                picture.file = file;
                picture.frameClientTick = frameClientTick;
                picture.reason = reason == null || reason.isEmpty() ? null : reason;
            }
        }
    }

    /** Forgets a player's steps, and marks the pictures asked of them as never to be reported. */
    public static synchronized void loggedOut(UUID player) {
        STEPS.remove(player);
        HOLDS.remove(player);
        for (Map<String, Picture> pictures : PICTURES.values()) {
            for (Picture picture : pictures.values()) {
                if (picture.player.equals(player) && picture.saved == null) {
                    picture.playerLeft = true;
                }
            }
        }
    }

    /** Forgets everything a run kept. Called when the run ends, however it ends. */
    static synchronized void forget(BundleExecution execution) {
        WATCHED.remove(execution);
        PICTURES.remove(execution);
        for (Map<BundleExecution, Map<String, String>> runs : HOLDS.values()) {
            runs.remove(execution);
        }
        for (LinkedHashMap<String, Step> steps : STEPS.values()) {
            Iterator<Step> it = steps.values().iterator();
            while (it.hasNext()) {
                if (it.next().execution == execution) {
                    it.remove();
                }
            }
        }
    }

    private static void watch(final BundleExecution execution) {
        if (WATCHED.add(execution)) {
            execution.addCompletionListener(new BundleExecution.CompletionListener() {
                @Override
                public void completed(BundleExecution ended, boolean discardedOnServerStop) {
                    forget(ended);
                }
            });
        }
    }

    /** For tests. */
    static synchronized void resetForTesting() {
        STEPS.clear();
        HOLDS.clear();
        PICTURES.clear();
        WATCHED.clear();
    }

    /** Forgets a player's steps when they leave. Registered on the event bus at mod start. */
    public static final class LogoutListener {
        @SubscribeEvent
        public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.player != null) {
                loggedOut(event.player.getUniqueID());
            }
        }
    }
}
