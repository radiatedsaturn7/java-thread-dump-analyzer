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

/**
 * Minimal parser for Android ART thread dumps produced via kill -3 or debugger.
 * Only thread name, id, state and stack frames are extracted.
 */
public class AndroidArtParser implements ThreadDumpParser {
    private static final Pattern THREAD_HEADER =
            Pattern.compile("^\"([^\"]+)\".*?tid=(\\d+)\\s+(\\S+)");
    private static final Pattern AT_LINE =
            Pattern.compile("^\\s*at\\s+([\\w.$]+)\\.([\\w$<>]+)\\(([^:]+)(?::(\\d+))?\\)");

    @Override
    public ThreadDump parse(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        List<ThreadInfo> threads = new ArrayList<>();
        String line;
        String name = null;
        long id = -1;
        Thread.State state = Thread.State.RUNNABLE;
        List<StackFrame> stack = new ArrayList<>();
        while ((line = reader.readLine()) != null) {
            Matcher m = THREAD_HEADER.matcher(line);
            if (m.find()) {
                if (name != null) {
                    threads.add(new ThreadInfo(id, name, state, stack, new ArrayList<>(), null, -1, false));
                    stack = new ArrayList<>();
                }
                name = m.group(1);
                try {
                    id = Long.parseLong(m.group(2));
                } catch (NumberFormatException e) {
                    id = -1;
                }
                String st = m.group(3).toUpperCase();
                if (st.contains("RUNNABLE") || st.equals("R")) {
                    state = Thread.State.RUNNABLE;
                } else if (st.contains("BLOCK")) {
                    state = Thread.State.BLOCKED;
                } else if (st.contains("WAIT")) {
                    state = Thread.State.WAITING;
                } else if (st.contains("SLEEP")) {
                    state = Thread.State.TIMED_WAITING;
                } else {
                    state = Thread.State.RUNNABLE;
                }
                continue;
            }
            if (name == null) {
                continue;
            }
            Matcher at = AT_LINE.matcher(line.trim());
            if (at.find()) {
                String cls = at.group(1);
                String method = at.group(2);
                String file = at.group(3);
                int ln = -1;
                if (at.group(4) != null) {
                    try {
                        ln = Integer.parseInt(at.group(4));
                    } catch (NumberFormatException e) {
                        ln = -1;
                    }
                }
                stack.add(new StackFrame(cls, method, file, ln));
            }
        }
        if (name != null) {
            threads.add(new ThreadInfo(id, name, state, stack, new ArrayList<>(), null, -1, false));
        }
        return new ThreadDump(Instant.now(), threads, null, null, -1);
    }
}
