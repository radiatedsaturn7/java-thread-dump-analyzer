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

public class OpenJ9Parser implements ThreadDumpParser {
    private static final Pattern THREAD_HEADER =
            Pattern.compile("^1XM\\w*INFO\\s+\"([^\"]+)\".*state:([A-Z]+)");
    private static final Pattern THREAD_ID =
            Pattern.compile("native thread ID:0x([0-9a-fA-F]+)");
    private static final Pattern STACK_LINE =
            Pattern.compile("^4XESTACKTRACE\\s+at ([^.(]+)\\.([^.(]+)\\(([^:]+)(?::(\\d+))?\\)");

    @Override
    public ThreadDump parse(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        List<ThreadInfo> threads = new ArrayList<>();

        String line;
        String currentName = null;
        long currentId = -1;
        Thread.State currentState = Thread.State.NEW;
        List<StackFrame> currentStack = new ArrayList<>();
        List<LockInfo> currentLocked = new ArrayList<>();
        LockInfo waitingOn = null;

        while ((line = reader.readLine()) != null) {
            Matcher header = THREAD_HEADER.matcher(line);
            if (header.find()) {
                if (currentName != null) {
                    threads.add(new ThreadInfo(currentId, currentName, currentState,
                            currentStack, currentLocked, waitingOn));
                    currentStack = new ArrayList<>();
                    currentLocked = new ArrayList<>();
                    waitingOn = null;
                }
                currentName = header.group(1);
                currentState = mapState(header.group(2));
                currentId = -1;
                continue;
            }

            if (currentName == null) {
                continue;
            }

            Matcher idMatcher = THREAD_ID.matcher(line);
            if (idMatcher.find()) {
                try {
                    currentId = Long.parseLong(idMatcher.group(1), 16);
                } catch (NumberFormatException e) {
                    currentId = -1;
                }
                continue;
            }

            Matcher frame = STACK_LINE.matcher(line.trim());
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
            }
        }

        if (currentName != null) {
            threads.add(new ThreadInfo(currentId, currentName, currentState, currentStack, currentLocked, waitingOn));
        }

        return new ThreadDump(Instant.now(), threads);
    }

    private Thread.State mapState(String code) {
        switch (code) {
            case "R":
                return Thread.State.RUNNABLE;
            case "CW":
            case "MW":
            case "P":
            case "S":
                return Thread.State.WAITING;
            case "B":
                return Thread.State.BLOCKED;
            default:
                return Thread.State.RUNNABLE;
        }
    }
}
