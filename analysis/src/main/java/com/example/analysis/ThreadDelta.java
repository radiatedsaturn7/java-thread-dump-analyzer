package com.example.analysis;

import java.util.List;
import com.example.model.ThreadInfo;

/**
 * Difference between two thread dumps.
 * Lists new threads that appear in the second dump and
 * threads that disappeared since the first dump.
 */
public class ThreadDelta {
    private final List<ThreadInfo> newThreads;
    private final List<ThreadInfo> disappearedThreads;

    public ThreadDelta(List<ThreadInfo> newThreads, List<ThreadInfo> disappearedThreads) {
        this.newThreads = newThreads;
        this.disappearedThreads = disappearedThreads;
    }

    /** Return threads present only in the second dump. */
    public List<ThreadInfo> getNewThreads() {
        return newThreads;
    }

    /** Return threads missing from the second dump but present in the first. */
    public List<ThreadInfo> getDisappearedThreads() {
        return disappearedThreads;
    }
}
