package com.example.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ThreadDump {
    private final Instant timestamp;
    private final List<ThreadInfo> threads;

    public ThreadDump(Instant timestamp, List<ThreadInfo> threads) {
        this.timestamp = timestamp;
        this.threads = threads == null ? new ArrayList<>() : new ArrayList<>(threads);
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public List<ThreadInfo> getThreads() {
        return new ArrayList<>(threads);
    }
}
