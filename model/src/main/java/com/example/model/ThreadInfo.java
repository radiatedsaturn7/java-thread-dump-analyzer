package com.example.model;

import java.util.ArrayList;
import java.util.List;

public class ThreadInfo {
    private final long id;
    private final String name;
    private final Thread.State state;
    private final List<StackFrame> stack;
    private final LockInfo lockInfo;

    public ThreadInfo(long id, String name, Thread.State state, List<StackFrame> stack, LockInfo lockInfo) {
        this.id = id;
        this.name = name;
        this.state = state;
        this.stack = stack == null ? new ArrayList<>() : new ArrayList<>(stack);
        this.lockInfo = lockInfo;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Thread.State getState() {
        return state;
    }

    public List<StackFrame> getStack() {
        return new ArrayList<>(stack);
    }

    public LockInfo getLockInfo() {
        return lockInfo;
    }
}
