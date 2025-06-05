package com.example.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Container for multiple {@link ThreadDump} instances.
 * Allows analysis of a series of dumps from the same JVM.
 */
public class AnalysisSession {
    private final List<ThreadDump> dumps = new ArrayList<>();

    /**
     * Add a thread dump to this session.
     *
     * @param dump thread dump to add
     */
    public void addThreadDump(ThreadDump dump) {
        if (dump != null) {
            dumps.add(dump);
        }
    }

    /**
     * Get an immutable view of all thread dumps.
     *
     * @return list of dumps in insertion order
     */
    public List<ThreadDump> getThreadDumps() {
        return Collections.unmodifiableList(dumps);
    }

    /**
     * Get the most recently added dump.
     *
     * @return latest dump if present
     */
    public Optional<ThreadDump> getLatestDump() {
        if (dumps.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(dumps.get(dumps.size() - 1));
    }
}
