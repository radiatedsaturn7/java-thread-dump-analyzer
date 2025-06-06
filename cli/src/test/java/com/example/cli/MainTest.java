package com.example.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import picocli.CommandLine;

public class MainTest {
    private PrintStream originalOut;
    private ByteArrayOutputStream out;

    @BeforeEach
    void setup() {
        originalOut = System.out;
        out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));
    }

    @AfterEach
    void cleanup() {
        System.setOut(originalOut);
    }

    @Test
    public void filterStateRunnable() {
        String path = getClass().getResource("/hotspot.txt").getPath();
        int code = new CommandLine(new Main()).execute("--filter-state", "RUNNABLE", path);
        assertEquals(0, code);
        String output = out.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("Threads in state RUNNABLE"));
    }

    @Test
    public void showDeadlocks() {
        String path = getClass().getResource("/deadlock.txt").getPath();
        int code = new CommandLine(new Main()).execute("--show-deadlocks-only", path);
        assertEquals(0, code);
        String output = out.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("Deadlock 1"));
    }

    @Test
    public void diffTwoDumps() {
        String before = getClass().getResource("/diff_before.txt").getPath();
        String after = getClass().getResource("/diff_after.txt").getPath();
        int code = new CommandLine(new Main()).execute("--diff", before, after);
        assertEquals(0, code);
        String output = out.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("New threads: 1"));
        assertTrue(output.contains("worker-3"));
        assertTrue(output.contains("Disappeared threads: 1"));
        assertTrue(output.contains("worker-1"));
    }

    @Test
    public void timelineCounts() {
        String dump1 = getClass().getResource("/hotspot.txt").getPath();
        String dump2 = getClass().getResource("/deadlock.txt").getPath();
        int code = new CommandLine(new Main()).execute("--timeline", dump1, dump2);
        assertEquals(0, code);
        String output = out.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("Dump 1"));
        assertTrue(output.contains("RUNNABLE"));
        assertTrue(output.contains("Dump 2"));
        assertTrue(output.contains("BLOCKED"));
    }

    @Test
    public void highCpuDetection() {
        String before = getClass().getResource("/diff_before.txt").getPath();
        String after = getClass().getResource("/diff_after.txt").getPath();
        int code = new CommandLine(new Main()).execute("--highcpu", before, after);
        assertEquals(0, code);
        String output = out.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("High CPU thread candidates"));
        assertTrue(output.contains("main"));
        assertTrue(output.contains("worker-2"));
    }
}
