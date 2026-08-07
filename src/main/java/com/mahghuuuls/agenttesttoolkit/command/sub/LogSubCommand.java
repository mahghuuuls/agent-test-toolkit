package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.arena.ArenaBoundsSource;
import com.mahghuuuls.agenttesttoolkit.command.SubCommand;
import com.mahghuuuls.agenttesttoolkit.logging.filter.ArenaFilter;
import com.mahghuuuls.agenttesttoolkit.logging.filter.Filter;
import com.mahghuuuls.agenttesttoolkit.logging.filter.RadiusFilter;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.LoggingCategory;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.SessionStamp;
import com.mahghuuuls.agenttesttoolkit.state.ToolkitState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Enables, disables and reports generic event logging.
 *
 * <p>Status reports every enabled category together with any filter applied to it.
 *
 * <p>It exists because silence is ambiguous. An agent that enables a category, asks for an
 * action, and sees nothing needs to distinguish "the event did not occur" from "a filter
 * excluded it" from "the category was never actually on". Status answers the last two
 * directly, which leaves only the first.
 */
public final class LogSubCommand implements SubCommand {

    private static final String ACTION_ON = "on";
    private static final String ACTION_OFF = "off";
    private static final String TARGET_ALL = "all";

    @Override
    public String getName() {
        return "log";
    }

    @Override
    public String getDescription() {
        return "Enable, disable or report generic event logging";
    }

    @Override
    public String getUsage() {
        return "log <<category> on|off | all off | status>";
    }

    @Override
    public boolean requiresPlayer() {
        // Logging state is process scoped, not player scoped, so the console and command
        // blocks may manage it. That is what lets a setup bundle enable diagnostics.
        return false;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            throw new WrongUsageException("/devtool " + getUsage());
        }

        String first = args[0].toLowerCase(Locale.ROOT);

        if ("status".equals(first)) {
            recordState(sender, "status", null);
            status(sender);
            return;
        }

        if (TARGET_ALL.equals(first)) {
            if (args.length < 2 || !ACTION_OFF.equals(args[1].toLowerCase(Locale.ROOT))) {
                // Only 'all off' exists. There is deliberately no 'all on': enabling every
                // category at once is the fastest way to make the log unreadable, and the
                // signal-over-volume principle is a core project boundary.
                throw new WrongUsageException("/devtool log all off");
            }
            int disabled = ToolkitState.disableAll();
            recordState(sender, "disableAll", null);
            sender.sendMessage(new TextComponentString(
                    "[DevToolkit] Disabled " + disabled + " logging categor" + (disabled == 1 ? "y" : "ies") + "."));
            return;
        }

        LoggingCategory category = LoggingCategory.byName(first);
        if (category == null) {
            // Thrown so a bundle counts it. A mistyped category name is the likeliest error in
            // a real setup bundle, and returning normally would report failed=0 while the
            // category the test depends on was never enabled.
            ToolkitLog.error("Unknown logging category", first);
            throw new CommandException("Unknown logging category: " + first
                    + ". Try /devtool log status");
        }

        if (args.length < 2) {
            throw new WrongUsageException("/devtool log " + category.getCategoryName() + " <on|off>");
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if (ACTION_ON.equals(action)) {
            // The filter is resolved before the category is enabled, so a rejected filter
            // leaves the category exactly as it was. A failed arena filter must not change
            // filter state, and enabling first would also half-apply the operator's intent.
            Filter filter;
            try {
                filter = parseFilter(server, sender, args);
            } catch (FilterRejected rejected) {
                ToolkitLog.error("Filter not applied", rejected.getMessage());
                // Nothing changed, so this must not read as success. A bundle narrowing a
                // category to an arena that does not exist would otherwise record everything.
                throw new CommandException(rejected.getMessage() + " Nothing changed.");
            }

            boolean changed = ToolkitState.enable(category);
            if (args.length > 2) {
                ToolkitState.setFilter(category, filter);
            }
            Filter active = ToolkitState.getFilter(category);
            recordState(sender, "enable", category);
            sender.sendMessage(new TextComponentString("[DevToolkit] " + category.getCategoryName()
                    + (changed ? " enabled." : " was already enabled.")
                    + "  filter=" + (active == null ? "none" : active.describe())));
        } else if (ACTION_OFF.equals(action)) {
            boolean changed = ToolkitState.disable(category);
            recordState(sender, "disable", category);
            sender.sendMessage(new TextComponentString("[DevToolkit] " + category.getCategoryName()
                    + (changed ? " disabled." : " was already disabled.")));
        } else {
            // Thrown for the same reason as an unknown category: nothing was changed.
            ToolkitLog.error("Unknown logging action", action);
            throw new CommandException("Unknown action: " + action + ". Expected on or off.");
        }
    }

    /**
     * Writes the enabled set to the log after every change, and on request.
     *
     * <p>Without this the log cannot answer its own most important question. A reader who sees
     * no {@code BLOCK_PLACE} records has to distinguish "no block was placed" from "the
     * category was never on", and until this record existed the only thing that could tell them
     * apart was {@code log status}, which writes to chat. Chat is exactly what a log file
     * reader does not have.
     *
     * <p>Written on changes rather than only on request, so the whole timeline is recoverable
     * from the file. A status-only record would say what was true at the moments somebody
     * happened to ask, which is not the same thing.
     *
     * <p>The full enabled set is repeated on every record rather than just the category that
     * changed. It costs a short string and means no reader has to accumulate state across lines
     * to answer "what was on at this point".
     */
    private void recordState(ICommandSender sender, String action, LoggingCategory category) {
        LogRecord record = RecordContext.stamp(LogRecord.of(EventType.LOG_CONFIG), sender);
        SessionStamp.apply(record);
        record.add("action", action);
        if (category != null) {
            record.add("category", category.getCategoryName());
            Filter own = ToolkitState.getFilter(category);
            record.add("filter", own == null ? null : own.describe());
        }

        java.util.Set<LoggingCategory> enabled = ToolkitState.getEnabledCategories();
        record.add("enabledCount", enabled.size());

        StringBuilder names = new StringBuilder();
        StringBuilder filters = new StringBuilder();
        for (LoggingCategory each : enabled) {
            if (names.length() > 0) {
                names.append(',');
            }
            names.append(each.getCategoryName());

            Filter active = ToolkitState.getFilter(each);
            if (active != null) {
                if (filters.length() > 0) {
                    filters.append(',');
                }
                filters.append(each.getCategoryName()).append(':').append(active.describe());
            }
        }
        // Both omitted when empty rather than rendered as a placeholder, so an absent field
        // means "none" consistently with every other record.
        record.add("enabledCategories", names.toString());
        record.add("filters", filters.toString());
        ToolkitLog.write(record);
    }

    /** Raised when a filter cannot be applied, so the caller can leave state untouched. */
    private static final class FilterRejected extends Exception {
        private static final long serialVersionUID = 1L;

        FilterRejected(String message) {
            super(message);
        }
    }

    /**
     * Parses the optional filter after {@code on}.
     *
     * <p>{@code log <category> on} with nothing after it leaves any existing filter alone
     * rather than clearing it. Re-enabling a category the operator narrowed earlier should not
     * silently widen it back to everything.
     *
     * @return the new filter, or null when none was given
     */
    private Filter parseFilter(MinecraftServer server, ICommandSender sender, String[] args)
            throws FilterRejected {
        if (args.length <= 2) {
            return null;
        }
        String kind = args[2].toLowerCase(Locale.ROOT);
        int dimension = sender.getEntityWorld().provider.getDimension();

        if ("arena".equals(kind)) {
            ArenaBoundsSource source = new ArenaBoundsSource(server);
            if (source.boundsFor(dimension) == null) {
                // Applying a filter that admits nothing, silently, is the worst
                // possible outcome for a feature whose whole purpose is making absence
                // explicable.
                throw new FilterRejected("No arena in dimension " + dimension
                        + ", so an arena filter cannot be applied.");
            }
            return new ArenaFilter(dimension, source);
        }

        if ("radius".equals(kind)) {
            if (args.length < 4) {
                throw new FilterRejected("radius filter needs a distance, for example 'radius 20'.");
            }
            double radius;
            try {
                radius = Double.parseDouble(args[3]);
            } catch (NumberFormatException e) {
                throw new FilterRejected("'" + args[3] + "' is not a number.");
            }
            if (radius < 0) {
                throw new FilterRejected("radius must not be negative.");
            }
            net.minecraft.entity.Entity anchor = sender.getCommandSenderEntity();
            if (anchor == null) {
                // The radius anchors to the applying player; the console has no position and
                // defaulting to the world origin would filter somewhere nobody asked about.
                throw new FilterRejected("radius filter requires a player sender.");
            }
            return new RadiusFilter(dimension, anchor.posX, anchor.posY, anchor.posZ, radius);
        }

        throw new FilterRejected("Unknown filter '" + kind + "'. Expected arena or radius.");
    }

    private void status(ICommandSender sender) {
        java.util.Set<LoggingCategory> enabled = ToolkitState.getEnabledCategories();
        if (enabled.isEmpty()) {
            sender.sendMessage(new TextComponentString(
                    "[DevToolkit] No logging categories enabled."));
            return;
        }
        sender.sendMessage(new TextComponentString(
                "[DevToolkit] Enabled categories (" + enabled.size() + "):"));
        for (LoggingCategory category : enabled) {
            // "filter=none" is stated rather than omitted. An agent must be able to tell an
            // unfiltered category from one whose filter it forgot about, and an absent field
            // would leave that ambiguous. An excluded event and an event that never
            // happened look identical in the log, so this line is the only thing that can
            // distinguish them.
            Filter filter = ToolkitState.getFilter(category);
            sender.sendMessage(new TextComponentString("  " + category.getCategoryName()
                    + "  filter=" + (filter == null ? "none" : filter.describe())));
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length <= 1) {
            List<String> options = new ArrayList<String>();
            for (String name : LoggingCategory.allNames()) {
                options.add(name);
            }
            options.add("status");
            options.add(TARGET_ALL);
            return CommandBase.getListOfStringsMatchingLastWord(args, options);
        }
        if (args.length == 2) {
            if (TARGET_ALL.equals(args[0].toLowerCase(Locale.ROOT))) {
                return CommandBase.getListOfStringsMatchingLastWord(args, Collections.singletonList(ACTION_OFF));
            }
            if (LoggingCategory.byName(args[0]) != null) {
                return CommandBase.getListOfStringsMatchingLastWord(args, java.util.Arrays.asList(ACTION_ON, ACTION_OFF));
            }
        }
        return Collections.emptyList();
    }
}
