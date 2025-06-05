package com.example.analysis;

import java.util.EnumMap;
import java.util.Map;

import com.example.model.ThreadDump;
import com.example.model.ThreadInfo;

public class ThreadDumpAnalyzer {

    /**
     * Compute the number of threads in each {@link Thread.State} for a thread dump.
     *
     * @param dump thread dump
     * @return map of state to count
     */
    public Map<Thread.State, Long> computeStateCounts(ThreadDump dump) {
        Map<Thread.State, Long> counts = new EnumMap<>(Thread.State.class);
        for (ThreadInfo info : dump.getThreads()) {
            counts.merge(info.getState(), 1L, Long::sum);
        }
        return counts;
    }
}
