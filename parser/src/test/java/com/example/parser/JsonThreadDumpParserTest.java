package com.example.parser;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;

import com.example.model.ThreadDump;

import org.junit.jupiter.api.Test;

public class JsonThreadDumpParserTest {
    @Test
    public void parsesSampleJson() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/sample.json")) {
            assertNotNull(in);
            ThreadDumpParser parser = new JsonThreadDumpParser();
            ThreadDump dump = parser.parse(in);
            assertEquals(2, dump.getThreads().size());
            assertEquals("main", dump.getThreads().get(0).getName());
            assertEquals(Thread.State.RUNNABLE, dump.getThreads().get(0).getState());
        }
    }
}
