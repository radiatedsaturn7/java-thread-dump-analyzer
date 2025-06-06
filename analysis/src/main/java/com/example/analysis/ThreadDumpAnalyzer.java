package com.example.analysis;

import java.util.EnumMap;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.example.model.ThreadDump;
import com.example.model.ThreadInfo;
import com.example.model.LockInfo;

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

    /**
     * Detect deadlocks by building a wait-for graph and searching for cycles.
     *
     * @param dump thread dump to analyze
     * @return list of detected deadlocks, empty if none
     */
    public List<DeadlockInfo> detectDeadlocks(ThreadDump dump) {
        Map<String, ThreadInfo> lockOwners = new HashMap<>();
        for (ThreadInfo t : dump.getThreads()) {
            for (LockInfo l : t.getLockedMonitors()) {
                lockOwners.put(l.getIdentity(), t);
            }
        }

        Map<ThreadInfo, ThreadInfo> waitFor = new HashMap<>();
        Map<ThreadInfo, LockInfo> waitingLock = new HashMap<>();
        for (ThreadInfo t : dump.getThreads()) {
            LockInfo w = t.getWaitingOn();
            if (w != null) {
                ThreadInfo owner = lockOwners.get(w.getIdentity());
                if (owner != null && owner != t) {
                    waitFor.put(t, owner);
                    waitingLock.put(t, w);
                }
            }
        }

        List<DeadlockInfo> result = new ArrayList<>();
        Set<ThreadInfo> visited = new HashSet<>();
        for (ThreadInfo start : waitFor.keySet()) {
            if (visited.contains(start)) {
                continue;
            }
            Map<ThreadInfo, Integer> pathIndex = new LinkedHashMap<>();
            ThreadInfo cur = start;
            int idx = 0;
            while (cur != null && !visited.contains(cur) && !pathIndex.containsKey(cur)) {
                pathIndex.put(cur, idx++);
                cur = waitFor.get(cur);
            }
            visited.addAll(pathIndex.keySet());
            if (cur != null && pathIndex.containsKey(cur)) {
                int startIdx = pathIndex.get(cur);
                List<ThreadInfo> cycleThreads = new ArrayList<>();
                List<LockInfo> cycleLocks = new ArrayList<>();
                for (Map.Entry<ThreadInfo, Integer> e : pathIndex.entrySet()) {
                    if (e.getValue() >= startIdx) {
                        cycleThreads.add(e.getKey());
                        cycleLocks.add(waitingLock.get(e.getKey()));
                    }
                }
                result.add(new DeadlockInfo(cycleThreads, cycleLocks));
            }
        }
        return result;
    }

    /**
     * Identify locks that have multiple threads waiting on them.
     * A lock contention hotspot is defined as a lock with at least
     * {@code minWaiters} threads waiting to acquire it.
     *
     * @param dump thread dump to analyze
     * @param minWaiters minimum number of waiting threads to consider a hotspot
     * @return map of LockInfo to list of waiting threads
     */
    public Map<LockInfo, List<ThreadInfo>> findLockContentionHotspots(ThreadDump dump, int minWaiters) {
        Map<String, LockInfo> lockById = new HashMap<>();
        Map<String, List<ThreadInfo>> waiting = new HashMap<>();
        for (ThreadInfo t : dump.getThreads()) {
            LockInfo w = t.getWaitingOn();
            if (w != null) {
                lockById.putIfAbsent(w.getIdentity(), w);
                waiting.computeIfAbsent(w.getIdentity(), k -> new ArrayList<>()).add(t);
            }
        }

        return waiting.entrySet().stream()
                .filter(e -> e.getValue().size() >= minWaiters)
                .collect(Collectors.toMap(e -> lockById.get(e.getKey()), Map.Entry::getValue));
    }

    /**
     * Convenience method using a default threshold of 2 waiting threads.
     *
     * @param dump thread dump to analyze
     * @return map of LockInfo to list of waiting threads
     */
    public Map<LockInfo, List<ThreadInfo>> findLockContentionHotspots(ThreadDump dump) {
        return findLockContentionHotspots(dump, 2);
    }
}
