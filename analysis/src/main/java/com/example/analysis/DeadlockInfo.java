package com.example.analysis;

import java.util.List;

import com.example.model.LockInfo;
import com.example.model.ThreadInfo;

/**
 * Represents a detected deadlock between a set of threads and locks.
 */
public class DeadlockInfo {
    private final List<ThreadInfo> threads;
    private final List<LockInfo> locks;

    public DeadlockInfo(List<ThreadInfo> threads, List<LockInfo> locks) {
        this.threads = threads;
        this.locks = locks;
    }

    public List<ThreadInfo> getThreads() {
        return threads;
    }

    public List<LockInfo> getLocks() {
        return locks;
    }
}
