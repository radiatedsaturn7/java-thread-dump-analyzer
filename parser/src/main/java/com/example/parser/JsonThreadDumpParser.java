package com.example.parser;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Collections;

import com.example.model.ThreadDump;

public class JsonThreadDumpParser implements ThreadDumpParser {
    @Override
    public ThreadDump parse(InputStream in) throws IOException {
        // TODO: implement real parsing logic
        return new ThreadDump(Instant.now(), Collections.emptyList());
    }
}
