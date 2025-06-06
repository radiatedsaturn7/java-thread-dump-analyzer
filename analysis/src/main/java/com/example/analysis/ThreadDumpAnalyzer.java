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
import java.util.Comparator;
import java.util.function.Function;

import com.example.model.StackFrame;

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
     * Compute thread state counts for each dump in the provided list.
     * The resulting list is in the same order as the input dumps.
     *
     * @param dumps list of thread dumps
     * @return list of state count maps
     */
    public List<Map<Thread.State, Long>> computeStateTimeline(List<ThreadDump> dumps) {
        List<Map<Thread.State, Long>> timeline = new ArrayList<>();
        for (ThreadDump dump : dumps) {
            timeline.add(computeStateCounts(dump));
        }
        return timeline;
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

    /**
     * Compute the most common stack frames across all threads in a dump.
     * Frames are counted individually and returned in descending order
     * of occurrence.
     *
     * @param dump thread dump to analyze
     * @param limit maximum number of frames to return
     * @return ordered map of StackFrame to count
     */
    public Map<StackFrame, Long> computeStackHotspots(ThreadDump dump, int limit) {
        Map<StackFrame, Long> counts = new HashMap<>();
        for (ThreadInfo t : dump.getThreads()) {
            for (StackFrame f : t.getStack()) {
                counts.merge(f, 1L, Long::sum);
            }
        }

        return counts.entrySet().stream()
                .sorted(Map.Entry.<StackFrame, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    /**
     * Convenience method returning the top 5 stack frames.
     */
    public Map<StackFrame, Long> computeStackHotspots(ThreadDump dump) {
        return computeStackHotspots(dump, 5);
    }

    /**
     * Group threads that share an identical stack trace and similar name pattern.
     * Similar names are detected by stripping a trailing numeric suffix such as
     * "-1" or "_2". Threads with the same normalized name and stack trace are
     * placed in the same group.
     *
     * @param dump thread dump to analyze
     * @return map of group key to list of threads in that group
     */
    public Map<String, List<ThreadInfo>> groupSimilarThreads(ThreadDump dump) {
        Map<String, List<ThreadInfo>> groups = new HashMap<>();
        for (ThreadInfo t : dump.getThreads()) {
            String name = t.getName().replaceAll("[-_]?\\d+$", "");
            String stackSig = t.getStack().stream()
                    .map(StackFrame::toString)
                    .collect(Collectors.joining(";"));
            String key = name + "::" + stackSig;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
        }
        return groups;
    }

    /**
     * Compare two thread dumps and identify newly created threads and
     * threads that disappeared.
     *
     * @param previous earlier dump
     * @param current later dump
     * @return object describing the differences
     */
    public ThreadDelta diff(ThreadDump previous, ThreadDump current) {
        Map<Long, ThreadInfo> prevById = previous.getThreads().stream()
                .collect(Collectors.toMap(ThreadInfo::getId, Function.identity()));

        Map<Long, ThreadInfo> currById = current.getThreads().stream()
                .collect(Collectors.toMap(ThreadInfo::getId, Function.identity()));

        List<ThreadInfo> newThreads = currById.values().stream()
                .filter(t -> !prevById.containsKey(t.getId()))
                .collect(Collectors.toList());

        List<ThreadInfo> disappeared = prevById.values().stream()
                .filter(t -> !currById.containsKey(t.getId()))
                .collect(Collectors.toList());

        return new ThreadDelta(newThreads, disappeared);
    }

    /**
     * Find threads that are RUNNABLE in every provided dump. Such threads may
     * be candidates for high CPU usage if they remain runnable across multiple
     * snapshots.
     *
     * @param dumps list of thread dumps in chronological order
     * @return list of ThreadInfo objects present and runnable in all dumps
     */
    public List<ThreadInfo> findHighCpuThreads(List<ThreadDump> dumps) {
        if (dumps == null || dumps.size() < 2) {
            return List.of();
        }

        Map<Long, ThreadInfo> candidates = dumps.get(0).getThreads().stream()
                .filter(t -> t.getState() == Thread.State.RUNNABLE)
                .collect(Collectors.toMap(ThreadInfo::getId, Function.identity()));

        for (int i = 1; i < dumps.size(); i++) {
            Set<Long> runnableIds = dumps.get(i).getThreads().stream()
                    .filter(t -> t.getState() == Thread.State.RUNNABLE)
                    .map(ThreadInfo::getId)
                    .collect(Collectors.toSet());
            candidates.entrySet().removeIf(e -> !runnableIds.contains(e.getKey()));
            if (candidates.isEmpty()) {
                break;
            }
        }

        return new ArrayList<>(candidates.values());
    }
}
