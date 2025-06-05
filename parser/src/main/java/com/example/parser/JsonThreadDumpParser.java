package com.example.parser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.example.model.ThreadDump;
import com.example.model.ThreadInfo;
import com.example.model.StackFrame;
import com.example.model.LockInfo;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class JsonThreadDumpParser implements ThreadDumpParser {
    @Override
    public ThreadDump parse(InputStream in) throws IOException {
        JSONParser parser = new JSONParser();
        try {
            JSONObject root = (JSONObject) parser.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
            Instant ts = Instant.now();
            Object tsVal = root.get("timestamp");
            if (tsVal instanceof String) {
                try {
                    ts = Instant.parse((String) tsVal);
                } catch (Exception e) {
                    // ignore and use current time
                }
            }

            List<ThreadInfo> threads = new ArrayList<>();
            Object arrObj = root.get("threads");
            if (arrObj instanceof JSONArray) {
                JSONArray arr = (JSONArray) arrObj;
                for (Object o : arr) {
                    if (!(o instanceof JSONObject)) continue;
                    JSONObject t = (JSONObject) o;
                    long id = -1;
                    Object idObj = t.get("id");
                    if (idObj instanceof Number) {
                        id = ((Number) idObj).longValue();
                    }
                    String name = String.valueOf(t.getOrDefault("name", "unknown"));
                    String stateStr = String.valueOf(t.getOrDefault("state", "RUNNABLE"));
                    Thread.State state;
                    try {
                        state = Thread.State.valueOf(stateStr);
                    } catch (IllegalArgumentException ex) {
                        state = Thread.State.RUNNABLE;
                    }

                    List<StackFrame> stack = new ArrayList<>();
                    Object stackObj = t.get("stack");
                    if (stackObj instanceof JSONArray) {
                        for (Object fo : (JSONArray) stackObj) {
                            if (!(fo instanceof JSONObject)) continue;
                            JSONObject f = (JSONObject) fo;
                            String cls = String.valueOf(f.getOrDefault("className", "?"));
                            String method = String.valueOf(f.getOrDefault("methodName", "?"));
                            String file = String.valueOf(f.getOrDefault("fileName", "?"));
                            int line = -1;
                            Object lineObj = f.get("lineNumber");
                            if (lineObj instanceof Number) {
                                line = ((Number) lineObj).intValue();
                            }
                            stack.add(new StackFrame(cls, method, file, line));
                        }
                    }

                    List<LockInfo> locked = new ArrayList<>();
                    Object lockedObj = t.get("lockedMonitors");
                    if (lockedObj instanceof JSONArray) {
                        for (Object lo : (JSONArray) lockedObj) {
                            if (!(lo instanceof JSONObject)) continue;
                            JSONObject l = (JSONObject) lo;
                            String cls = String.valueOf(l.getOrDefault("className", "?"));
                            String ident = String.valueOf(l.getOrDefault("identity", "?"));
                            locked.add(new LockInfo(cls, ident));
                        }
                    }

                    LockInfo waiting = null;
                    Object waitObj = t.get("waitingOn");
                    if (waitObj instanceof JSONObject) {
                        JSONObject w = (JSONObject) waitObj;
                        String cls = String.valueOf(w.getOrDefault("className", "?"));
                        String ident = String.valueOf(w.getOrDefault("identity", "?"));
                        waiting = new LockInfo(cls, ident);
                    }

                    threads.add(new ThreadInfo(id, name, state, stack, locked, waiting));
                }
            }

            return new ThreadDump(ts, threads);
        } catch (ParseException e) {
            throw new IOException("Invalid JSON", e);
        }
    }
}
