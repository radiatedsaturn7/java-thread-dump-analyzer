package com.example.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.example.model.ThreadDump;
import com.example.model.StackFrame;
import com.example.model.ThreadInfo;
import com.example.parser.ParserFactory;
import com.example.parser.ThreadDumpParser;

import org.junit.jupiter.api.Test;

public class ThreadDumpAnalyzerTest {
    private ThreadDump loadDump(String resource) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            ThreadDumpParser parser = ParserFactory.detect(in);
            return parser.parse(in);
        }
    }

    @Test
    public void parsesHotspotDump() throws Exception {
        ThreadDump dump = loadDump("/hotspot.txt");
        ThreadDumpAnalyzer analyzer = new ThreadDumpAnalyzer();
        Map<Thread.State, Long> counts = analyzer.computeStateCounts(dump);
        assertEquals(2, dump.getThreads().size());
        assertEquals(1L, counts.get(Thread.State.RUNNABLE));
        assertEquals(1L, counts.get(Thread.State.WAITING));
    }

    @Test
    public void parsesGzipDump() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(buffer);
             InputStream raw = getClass().getResourceAsStream("/hotspot.txt")) {
            raw.transferTo(out);
        }
        try (InputStream gz = new GZIPInputStream(new ByteArrayInputStream(buffer.toByteArray()))) {
            InputStream in = new java.io.BufferedInputStream(gz);
            ThreadDumpParser parser = ParserFactory.detect(in);
            ThreadDump dump = parser.parse(in);
            assertEquals(2, dump.getThreads().size());
        }
    }

    @Test
    public void computesStackHotspots() throws Exception {
        ThreadDump dump = loadDump("/hotspot.txt");
        ThreadDumpAnalyzer analyzer = new ThreadDumpAnalyzer();
        Map<StackFrame, Long> hot = analyzer.computeStackHotspots(dump, 3);
        assertEquals(3, hot.size());
        StackFrame main = new StackFrame("example.Main", "main", "Main.java", 1);
        assertTrue(hot.containsKey(main));
    }

    @Test
    public void detectsDeadlocks() throws Exception {
        ThreadDump dump = loadDump("/deadlock.txt");
        ThreadDumpAnalyzer analyzer = new ThreadDumpAnalyzer();
        var deadlocks = analyzer.detectDeadlocks(dump);
        assertEquals(1, deadlocks.size());
        assertEquals(2, deadlocks.get(0).getThreads().size());
    }

    @Test
    public void groupsSimilarThreads() throws Exception {
        ThreadDump dump = loadDump("/group.txt");
        ThreadDumpAnalyzer analyzer = new ThreadDumpAnalyzer();
        Map<String, List<ThreadInfo>> groups = analyzer.groupSimilarThreads(dump);
        assertEquals(2, groups.size());
        boolean hasPoolGroup = groups.values().stream().anyMatch(l -> l.size() == 2);
        assertTrue(hasPoolGroup);
    }

    @Test
    public void detectsNewAndDisappearedThreads() throws Exception {
        ThreadDump before = loadDump("/diff_before.txt");
        ThreadDump after = loadDump("/diff_after.txt");
        ThreadDumpAnalyzer analyzer = new ThreadDumpAnalyzer();
        ThreadDelta delta = analyzer.diff(before, after);
        assertEquals(1, delta.getNewThreads().size());
        assertEquals("worker-3", delta.getNewThreads().get(0).getName());
        assertEquals(1, delta.getDisappearedThreads().size());
        assertEquals("worker-1", delta.getDisappearedThreads().get(0).getName());
    }

    @Test
    public void computesStateTimeline() throws Exception {
        ThreadDump d1 = loadDump("/hotspot.txt");
        ThreadDump d2 = loadDump("/deadlock.txt");
        ThreadDumpAnalyzer analyzer = new ThreadDumpAnalyzer();
        List<Map<Thread.State, Long>> timeline = analyzer.computeStateTimeline(List.of(d1, d2));
        assertEquals(2, timeline.size());
        assertEquals(1L, timeline.get(0).get(Thread.State.WAITING));
        assertEquals(2L, timeline.get(1).get(Thread.State.BLOCKED));
    }

    @Test
    public void findsHighCpuThreads() throws Exception {
        ThreadDump before = loadDump("/diff_before.txt");
        ThreadDump after = loadDump("/diff_after.txt");
        ThreadDumpAnalyzer analyzer = new ThreadDumpAnalyzer();
        List<ThreadInfo> result = analyzer.findHighCpuThreads(List.of(before, after));
        assertEquals(2, result.size());
        List<String> names = result.stream().map(ThreadInfo::getName).toList();
        assertTrue(names.contains("main"));
        assertTrue(names.contains("worker-2"));
    }

    @Test
    public void detectsStateChangesBetweenDumps() {
        ThreadInfo beforeThread = new ThreadInfo(1, "worker", Thread.State.RUNNABLE, List.of(), null);
        ThreadInfo afterThread = new ThreadInfo(1, "worker", Thread.State.WAITING, List.of(), null);
        ThreadDump before = new ThreadDump(java.time.Instant.now(), List.of(beforeThread));
        ThreadDump after = new ThreadDump(java.time.Instant.now(), List.of(afterThread));
        ThreadDumpAnalyzer analyzer = new ThreadDumpAnalyzer();
        Map<ThreadInfo, Thread.State> changes = analyzer.findStateChanges(before, after);
        assertEquals(1, changes.size());
        assertEquals(Thread.State.RUNNABLE, changes.get(afterThread));
    }

    @Test
    public void detectsThreadPoolStarvation() {
        ThreadInfo t1 = new ThreadInfo(1, "pool-1-thread-1", Thread.State.WAITING, List.of(), null);
        ThreadInfo t2 = new ThreadInfo(2, "pool-1-thread-2", Thread.State.WAITING, List.of(), null);
        ThreadDump dump = new ThreadDump(java.time.Instant.now(), List.of(t1, t2));
        ThreadDumpAnalyzer analyzer = new ThreadDumpAnalyzer();
        List<String> pools = analyzer.detectThreadPoolStarvation(List.of(dump));
        assertEquals(1, pools.size());
        assertEquals("pool-1-thread", pools.get(0));
    }
}
