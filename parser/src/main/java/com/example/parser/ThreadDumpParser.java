package com.example.parser;

import java.io.InputStream;
import java.io.IOException;

import com.example.model.ThreadDump;

public interface ThreadDumpParser {
    ThreadDump parse(InputStream in) throws IOException;
}
