package com.example.parser;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;

import com.example.model.ThreadDump;

import org.junit.jupiter.api.Test;

public class AndroidArtParserTest {
    @Test
    public void parsesSampleAndroidDump() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/android_art.txt")) {
            assertNotNull(in);
            ThreadDumpParser parser = new AndroidArtParser();
            ThreadDump dump = parser.parse(in);
            assertEquals(2, dump.getThreads().size());
            assertEquals("main", dump.getThreads().get(0).getName());
            assertEquals(Thread.State.RUNNABLE, dump.getThreads().get(0).getState());
        }
    }
}
