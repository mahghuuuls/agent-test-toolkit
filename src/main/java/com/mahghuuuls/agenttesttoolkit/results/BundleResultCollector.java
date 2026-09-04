package com.mahghuuuls.agenttesttoolkit.results;

import com.mahghuuuls.agenttesttoolkit.bundle.BundleExecution;
import com.mahghuuuls.agenttesttoolkit.bundle.ExecutionSink;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Keeps every record one execution produced, then hands them to the writer.
 *
 * <p>Nothing is interpreted here. The collector is a list with a lifetime, and the lifetime
 * is what matters: attached before the start record, detached by the execution finishing.
 * Interpretation happens in the writer, from the records alone, which is what lets a result
 * file be checked against the log line by line.
 */
public final class BundleResultCollector implements ExecutionSink {

    private final File directory;
    private final String operatorAtStart;
    private final List<LogRecord> records = new ArrayList<LogRecord>();
    private boolean finished;

    /** @param directory where the result file goes; null disables writing but keeps collecting */
    public BundleResultCollector(File directory) {
        this(directory, null);
    }

    /**
     * @param operatorAtStart the operator bound when the run began, or null. A run that never
     *                        binds or unbinds an operator produces no operator record of its
     *                        own, and would otherwise leave the field out
     */
    public BundleResultCollector(File directory, String operatorAtStart) {
        this.directory = directory;
        this.operatorAtStart = operatorAtStart;
    }

    @Override
    public void accept(LogRecord record) {
        if (!finished && record != null) {
            records.add(record);
        }
    }

    @Override
    public void finished(BundleExecution execution) {
        if (finished) {
            return;
        }
        finished = true;
        if (directory != null) {
            BundleResultWriter.write(directory, execution, records, operatorAtStart);
        }
    }

    /** The records seen so far, in order. For the writer and for tests. */
    public List<LogRecord> getRecords() {
        return Collections.unmodifiableList(records);
    }
}
