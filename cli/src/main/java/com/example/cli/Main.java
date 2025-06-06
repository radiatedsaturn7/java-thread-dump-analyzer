package com.example.cli;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.example.analysis.ThreadDumpAnalyzer;
import com.example.model.ThreadDump;
import com.example.model.ThreadInfo;
import com.example.parser.ParserFactory;
import com.example.parser.ThreadDumpParser;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "analyzer", mixinStandardHelpOptions = true,
        description = "Analyze thread dump files")
public class Main implements Runnable {

    @Parameters(arity = "1..*", paramLabel = "FILE", description = "Thread dump files")
    private List<String> files = new ArrayList<>();

    @Option(names = {"-o", "--out"}, description = "Output file (currently ignored)")
    private String out;

    @Option(names = "--filter-state", description = "Only show threads in the given state")
    private Thread.State filterState;

    public static void main(String[] args) {
        System.exit(new CommandLine(new Main()).execute(args));
    }

    @Override
    public void run() {
        ThreadDumpAnalyzer analyzer = new ThreadDumpAnalyzer();
        for (String path : files) {
            try (InputStream in = openInput(path)) {
                ThreadDumpParser parser = ParserFactory.detect(in);
                ThreadDump dump = parser.parse(in);
                System.out.println("File: " + path);

                if (filterState != null) {
                    List<ThreadInfo> matches = dump.getThreads().stream()
                            .filter(t -> t.getState() == filterState)
                            .collect(Collectors.toList());
                    System.out.println("Threads in state " + filterState + ": " + matches.size());
                    for (ThreadInfo t : matches) {
                        System.out.printf("  [%d] %s%n", t.getId(), t.getName());
                    }
                } else {
                    Map<Thread.State, Long> counts = analyzer.computeStateCounts(dump);
                    for (Map.Entry<Thread.State, Long> e : counts.entrySet()) {
                        System.out.printf("  %s: %d%n", e.getKey(), e.getValue());
                    }
                }
            } catch (IOException e) {
                System.err.println("Failed to parse " + path + ": " + e.getMessage());
            }
        }
    }

    private InputStream openInput(String path) throws IOException {
        InputStream base = new FileInputStream(path);
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
}
