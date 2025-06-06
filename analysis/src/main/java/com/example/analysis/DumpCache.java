package com.example.analysis;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HexFormat;

import com.example.model.ThreadDump;
import com.example.parser.ParserFactory;
import com.example.parser.ThreadDumpParser;

/**
 * Simple in-memory cache for parsed thread dumps.
 */
public final class DumpCache {
    private DumpCache() {}

    private static final int MAX_ENTRIES = 10;
    private static final Map<String, ThreadDump> CACHE =
        Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, ThreadDump> e) {
                return size() > MAX_ENTRIES;
            }
        });

    private static String digest(byte[] bytes) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(bytes);
        return HexFormat.of().formatHex(hash);
    }

    public static ThreadDump load(Path path) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        String key = digest(bytes);
        ThreadDump dump = CACHE.get(key);
        if (dump == null) {
            try (InputStream in = openInput(path)) {
                ThreadDumpParser parser = ParserFactory.detect(in);
                dump = parser.parse(in);
            }
            CACHE.put(key, dump);
        }
        return dump;
    }

    public static ThreadDump load(byte[] bytes) throws Exception {
        String key = digest(bytes);
        ThreadDump dump = CACHE.get(key);
        if (dump == null) {
            try (InputStream detect = new ByteArrayInputStream(bytes)) {
                ThreadDumpParser parser = ParserFactory.detect(detect);
                dump = parser.parse(new ByteArrayInputStream(bytes));
            }
            CACHE.put(key, dump);
        }
        return dump;
    }

    private static InputStream openInput(Path path) throws Exception {
        InputStream base = Files.newInputStream(path);
        BufferedInputStream buffered = new BufferedInputStream(base);
        buffered.mark(2);
        int b1 = buffered.read();
        int b2 = buffered.read();
        buffered.reset();
        if (b1 == 0x1f && b2 == 0x8b) {
            return new BufferedInputStream(new GZIPInputStream(buffered));
        }
        return buffered;
    }

    public static void clear() {
        CACHE.clear();
    }
}
