package com.example.model;

import java.util.ArrayList;
import java.util.List;

public class ThreadInfo {
    private final long id;
    private final String name;
    private final Thread.State state;
    private final List<StackFrame> stack;
    private final List<LockInfo> lockedMonitors;
    private final LockInfo waitingOn;

    public ThreadInfo(long id, String name, Thread.State state, List<StackFrame> stack,
                      List<LockInfo> lockedMonitors, LockInfo waitingOn) {
        this.id = id;
        this.name = name;
        this.state = state;
        this.stack = stack == null ? new ArrayList<>() : new ArrayList<>(stack);
        this.lockedMonitors = lockedMonitors == null ? new ArrayList<>() : new ArrayList<>(lockedMonitors);
        this.waitingOn = waitingOn;
    }

    public ThreadInfo(long id, String name, Thread.State state, List<StackFrame> stack, LockInfo waitingOn) {
        this(id, name, state, stack, new ArrayList<>(), waitingOn);
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

    public List<LockInfo> getLockedMonitors() {
        return new ArrayList<>(lockedMonitors);
    }

    public LockInfo getWaitingOn() {
        return waitingOn;
    }
}
