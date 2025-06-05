package com.example.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public final class ParserFactory {
    private ParserFactory() {}

    public static ThreadDumpParser detect(InputStream in) throws IOException {
        in.mark(1024);
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            String line = reader.readLine();
            if (line == null) break;
            sb.append(line).append('\n');
        }
        in.reset();
        String header = sb.toString();
        if (header.contains("Full thread dump") || header.contains("Full Java thread dump")) {
            return new HotSpotParser();
        }
        if (header.contains("hs_err_pid")) {
            return new HsErrParser();
        }
        if (header.contains("1XMTHREADINFO")) {
            return new OpenJ9Parser();
        }
        if (header.trim().startsWith("{")) {
            return new JsonThreadDumpParser();
        }
        // default to HotSpot parser
        return new HotSpotParser();
    }
}
