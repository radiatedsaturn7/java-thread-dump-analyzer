package com.example.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ThreadDump {
    private final Instant timestamp;
    private final List<ThreadInfo> threads;
    private final String label;
    private final String jvmVersion;
    private final long uptimeMillis;

    public ThreadDump(Instant timestamp, List<ThreadInfo> threads) {
        this(timestamp, threads, null, null, -1);
    }

    public ThreadDump(Instant timestamp, List<ThreadInfo> threads, String label) {
        this(timestamp, threads, label, null, -1);
    }

    public ThreadDump(Instant timestamp, List<ThreadInfo> threads, String label,
                      String jvmVersion, long uptimeMillis) {
        this.timestamp = timestamp;
        this.threads = threads == null ? new ArrayList<>() : new ArrayList<>(threads);
        this.label = label;
        this.jvmVersion = jvmVersion;
        this.uptimeMillis = uptimeMillis;
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

    public String getJvmVersion() {
        return jvmVersion;
    }

    public long getUptimeMillis() {
        return uptimeMillis;
    }
}
