package com.example.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.model.ThreadDump;
import com.example.model.ThreadInfo;
import com.example.model.StackFrame;
import com.example.model.LockInfo;

/**
 * Minimal parser for hs_err_pid.log crash files. It focuses on the
 * "All threads" section which resembles a standard HotSpot thread dump.
 */
public class HsErrParser implements ThreadDumpParser {
    private static final Pattern THREAD_HEADER =
            Pattern.compile("^\"([^\"]+)\".*");
    private static final Pattern PRIORITY = Pattern.compile("prio=(\\d+)");
    private static final Pattern NID = Pattern.compile("nid=0x([0-9a-fA-F]+)");
    private static final Pattern STATE_LINE =
            Pattern.compile("^\\s*java\\.lang\\.Thread.State: (\\S+)");
    private static final Pattern FRAME_LINE =
            Pattern.compile("^\\s*at ([^.(]+)\\.([^.(]+)\\(([^:]+)(?::(\\d+))?\\)");
    private static final Pattern WAITING_LINE =
            Pattern.compile("-\\s+waiting to lock <([^>]+)> \\(([^)]+)\\)");
    private static final Pattern PARKING_LINE =
            Pattern.compile("-\\s+parking to wait for\\s+<([^>]+)> \\(([^)]+)\\)");
    private static final Pattern LOCKED_LINE =
            Pattern.compile("-\\s+locked <([^>]+)> \\(([^)]+)\\)");

    @Override
    public ThreadDump parse(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        List<ThreadInfo> threads = new ArrayList<>();

        String line;
        String currentName = null;
        long currentId = -1;
        int currentPrio = -1;
        boolean currentDaemon = false;
        Thread.State currentState = Thread.State.NEW;
        List<StackFrame> currentStack = new ArrayList<>();
        List<LockInfo> currentLocked = new ArrayList<>();
        LockInfo waitingOn = null;
        boolean started = false;

        while ((line = reader.readLine()) != null) {
            Matcher header = THREAD_HEADER.matcher(line);
            if (header.find()) {
                started = true;
                if (currentName != null) {
                    threads.add(new ThreadInfo(currentId, currentName, currentState,
                            currentStack, currentLocked, waitingOn, currentPrio, currentDaemon));
                    currentStack = new ArrayList<>();
                    currentLocked = new ArrayList<>();
                    waitingOn = null;
                }
                currentName = header.group(1);
                Matcher nidM = NID.matcher(line);
                if (nidM.find()) {
                    try {
                        currentId = Long.parseLong(nidM.group(1), 16);
                    } catch (NumberFormatException e) {
                        currentId = -1;
                    }
                } else {
                    currentId = -1;
                }
                Matcher prioM = PRIORITY.matcher(line);
                if (prioM.find()) {
                    try {
                        currentPrio = Integer.parseInt(prioM.group(1));
                    } catch (NumberFormatException e) {
                        currentPrio = -1;
                    }
                } else {
                    currentPrio = -1;
                }
                currentDaemon = line.contains(" daemon ");
                currentState = Thread.State.NEW;
                continue;
            }

            if (!started || currentName == null) {
                continue;
            }

            Matcher state = STATE_LINE.matcher(line);
            if (state.find()) {
                try {
                    currentState = Thread.State.valueOf(state.group(1));
                } catch (IllegalArgumentException ex) {
                    currentState = Thread.State.RUNNABLE;
                }
                continue;
            }

            Matcher frame = FRAME_LINE.matcher(line.trim());
            if (frame.find()) {
                String cls = frame.group(1);
                String method = frame.group(2);
                String file = frame.group(3);
                int ln = -1;
                if (frame.group(4) != null) {
                    try {
                        ln = Integer.parseInt(frame.group(4));
                    } catch (NumberFormatException e) {
                        ln = -1;
                    }
                }
                currentStack.add(new StackFrame(cls, method, file, ln));
                continue;
            }

            Matcher wait = WAITING_LINE.matcher(line.trim());
            if (wait.find()) {
                waitingOn = new LockInfo(wait.group(2), wait.group(1));
                continue;
            }

            Matcher park = PARKING_LINE.matcher(line.trim());
            if (park.find()) {
                waitingOn = new LockInfo(park.group(2), park.group(1));
                continue;
            }

            Matcher locked = LOCKED_LINE.matcher(line.trim());
            if (locked.find()) {
                currentLocked.add(new LockInfo(locked.group(2), locked.group(1)));
                continue;
            }
        }

        if (currentName != null) {
            threads.add(new ThreadInfo(currentId, currentName, currentState, currentStack,
                    currentLocked, waitingOn, currentPrio, currentDaemon));
        }

        return new ThreadDump(Instant.now(), threads, null, null, -1);
    }
}
