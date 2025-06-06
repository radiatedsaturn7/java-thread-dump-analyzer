package com.example.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ThreadDump {
    private final Instant timestamp;
    private final List<ThreadInfo> threads;
    private final String label;

    public ThreadDump(Instant timestamp, List<ThreadInfo> threads) {
        this(timestamp, threads, null);
    }

    public ThreadDump(Instant timestamp, List<ThreadInfo> threads, String label) {
        this.timestamp = timestamp;
        this.threads = threads == null ? new ArrayList<>() : new ArrayList<>(threads);
        this.label = label;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public List<ThreadInfo> getThreads() {
        return new ArrayList<>(threads);
    }

    public String getLabel() {
        return label;
    }
}
