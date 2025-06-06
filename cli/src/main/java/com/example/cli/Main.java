package com.example.cli;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.nio.file.Path;

import com.example.analysis.ThreadDumpAnalyzer;
import com.example.analysis.DeadlockInfo;
import com.example.analysis.ThreadDelta;
import com.example.analysis.DumpCache;
import com.example.model.ThreadDump;
import com.example.model.ThreadInfo;
import com.example.model.StackFrame;
import com.example.parser.ParserFactory;
import com.example.parser.ThreadDumpParser;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "analyzer", mixinStandardHelpOptions = true,
        versionProvider = Main.VersionProvider.class,
        description = "Analyze thread dump files")
public class Main implements Runnable {

    static class VersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            String v = Main.class.getPackage().getImplementationVersion();
            if (v == null) {
                v = "0.1.0-SNAPSHOT";
            }
            return new String[] { "Thread Dump Analyzer " + v };
        }
    }

    enum OutputFormat { text, json }

    @Parameters(arity = "1..*", paramLabel = "FILE", description = "Thread dump files")
    private List<String> files = new ArrayList<>();

    @Option(names = {"-o", "--out"}, description = "Output file (currently ignored)")
    private String out;

    @Option(names = "--output-json", description = "Export analysis results in JSON format")
    private boolean outputJson = false;

    @Option(names = "--open", description = "Open generated HTML report in the default browser")
    private boolean open = false;

    @Option(names = "--filter-state", description = "Only show threads in the given state")
    private Thread.State filterState;

    @Option(names = "--hotspots", paramLabel = "N", description = "Show top N stack trace hotspots")
    private int hotspotLimit = 0;

    @Option(names = "--show-deadlocks-only", description = "Only display detected deadlocks")
    private boolean showDeadlocksOnly = false;

    @Option(names = "--format", description = "Output format: text or json", defaultValue = "text")
    private OutputFormat format = OutputFormat.text;

    @Option(names = "--features", split = ",", description = "Comma-separated list of features to display: counts,deadlocks,hotspots", defaultValue = "counts,deadlocks,hotspots")
    private Set<String> features = new HashSet<>(Arrays.asList("counts", "deadlocks", "hotspots"));

    @Option(names = "--diff", description = "Compare two dumps and show new and disappeared threads")
    private boolean diff = false;

    @Option(names = "--timeline", description = "Show thread state counts for each dump")
    private boolean timeline = false;

    @Option(names = "--highcpu", description = "Show threads runnable in all provided dumps")
    private boolean highCpu = false;

    @Option(names = "--starvation", description = "Detect thread pool starvation across dumps")
    private boolean starvation = false;

    @Option(names = "--label", paramLabel = "NAME", description = "Custom label for each dump (can be repeated)")
    private List<String> labels = new ArrayList<>();

    @Option(names = "--clear-cache", description = "Clear cached dumps before running")
    private boolean clearCache = false;

    @Option(names = "--list-parsers", description = "List supported dump formats")
    private boolean listParsers = false;

    public static void main(String[] args) {
        System.exit(new CommandLine(new Main()).execute(args));
    }

    @Override
    public void run() {
        ThreadDumpAnalyzer analyzer = new ThreadDumpAnalyzer();

        if (clearCache) {
            DumpCache.clear();
        }

        if (outputJson) {
            format = OutputFormat.json;
        }

        if (listParsers) {
            ParserFactory.getSupportedFormats().forEach(System.out::println);
            return;
        }

        if (timeline) {
            if (files.size() < 2) {
                System.err.println("--timeline requires at least two FILE arguments");
                return;
            }
            try {
                List<Map<Thread.State, Long>> timelineData = new ArrayList<>();
                for (String path : files) {
                    ThreadDump dump = DumpCache.load(Path.of(path));
                    timelineData.add(analyzer.computeStateCounts(dump));
                }

                if (format == OutputFormat.text) {
                    for (int i = 0; i < files.size(); i++) {
                        System.out.println("Dump " + (i + 1) + " (" + getLabel(i, files.get(i)) + "):");
                        Map<Thread.State, Long> counts = timelineData.get(i);
                        for (var e : counts.entrySet()) {
                            System.out.printf("  %s: %d%n", e.getKey(), e.getValue());
                        }
                    }
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append('[');
                    for (int i = 0; i < files.size(); i++) {
                        if (i > 0) sb.append(',');
                        sb.append('{');
                        sb.append("\"file\": \"").append(getLabel(i, files.get(i)).replace("\"", "\\\"")).append("\", \"counts\": {");
                        boolean first = true;
                        for (var e : timelineData.get(i).entrySet()) {
                            if (!first) sb.append(',');
                            sb.append('"').append(e.getKey()).append('"').append(':').append(e.getValue());
                            first = false;
                        }
                        sb.append("}}");
                    }
                    sb.append(']');
                    System.out.println(sb.toString());
                }
            } catch (Exception e) {
                System.err.println("Failed to parse dumps: " + e.getMessage());
            }
            return;
        }

        if (highCpu) {
            if (files.size() < 2) {
                System.err.println("--highcpu requires at least two FILE arguments");
                return;
            }
            try {
                List<ThreadDump> dumps = new ArrayList<>();
                for (String path : files) {
                    dumps.add(DumpCache.load(Path.of(path)));
                }
                List<ThreadInfo> high = analyzer.findHighCpuThreads(dumps);
                if (format == OutputFormat.text) {
                    System.out.println("High CPU thread candidates: " + high.size());
                    for (ThreadInfo t : high) {
                        System.out.printf("  [%d] %s%n", t.getId(), t.getName());
                    }
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append('{').append("\"threads\": [");
                    for (int i = 0; i < high.size(); i++) {
                        ThreadInfo t = high.get(i);
                        if (i > 0) sb.append(',');
                        sb.append('{').append("\"id\": ").append(t.getId())
                          .append(", \"name\": \"").append(t.getName().replace("\"", "\\\"")).append("\"}");
                    }
                    sb.append("]}");
                    System.out.println(sb.toString());
                }
            } catch (Exception e) {
                System.err.println("Failed to parse dumps: " + e.getMessage());
            }
            return;
        }

        if (starvation) {
            try {
                List<ThreadDump> dumps = new ArrayList<>();
                for (String path : files) {
                    dumps.add(DumpCache.load(Path.of(path)));
                }
                List<String> pools = analyzer.detectThreadPoolStarvation(dumps);
                if (format == OutputFormat.text) {
                    if (pools.isEmpty()) {
                        System.out.println("No thread pool starvation detected.");
                    } else {
                        System.out.println("Potential thread pool starvation detected:");
                        for (String p : pools) {
                            System.out.println("  " + p);
                        }
                    }
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append('{').append("\"starvedPools\": [");
                    for (int i = 0; i < pools.size(); i++) {
                        if (i > 0) sb.append(',');
                        sb.append('"').append(pools.get(i).replace("\"", "\\\"")).append('"');
                    }
                    sb.append("]}");
                    System.out.println(sb.toString());
                }
            } catch (Exception e) {
                System.err.println("Failed to parse dumps: " + e.getMessage());
            }
            return;
        }

        if (diff) {
            if (files.size() != 2) {
                System.err.println("--diff requires exactly two FILE arguments");
                return;
            }
            try {
                ThreadDump d1 = DumpCache.load(Path.of(files.get(0)));
                ThreadDump d2 = DumpCache.load(Path.of(files.get(1)));

                ThreadDelta delta = analyzer.diff(d1, d2);
                Map<ThreadInfo, Thread.State> changes = analyzer.findStateChanges(d1, d2);
                if (format == OutputFormat.text) {
                    System.out.println("New threads: " + delta.getNewThreads().size());
                    for (ThreadInfo t : delta.getNewThreads()) {
                        System.out.printf("  [%d] %s%n", t.getId(), t.getName());
                    }
                    System.out.println("Disappeared threads: " + delta.getDisappearedThreads().size());
                    for (ThreadInfo t : delta.getDisappearedThreads()) {
                        System.out.printf("  [%d] %s%n", t.getId(), t.getName());
                    }
                    if (!changes.isEmpty()) {
                        System.out.println("Threads with state changes: " + changes.size());
                        for (Map.Entry<ThreadInfo, Thread.State> e : changes.entrySet()) {
                            ThreadInfo t = e.getKey();
                            System.out.printf("  [%d] %s: %s -> %s%n", t.getId(), t.getName(), e.getValue(), t.getState());
                        }
                    }
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append('{');
                    sb.append("\"file1\": \"").append(getLabel(0, files.get(0)).replace("\"", "\\\"")).append("\",");
                    sb.append(" \"file2\": \"").append(getLabel(1, files.get(1)).replace("\"", "\\\"")).append("\",");
                    sb.append(" \"newThreads\": [");
                    for (int i = 0; i < delta.getNewThreads().size(); i++) {
                        ThreadInfo t = delta.getNewThreads().get(i);
                        if (i > 0) sb.append(',');
                        sb.append('{').append("\"id\": ").append(t.getId())
                          .append(", \"name\": \"").append(t.getName().replace("\"", "\\\"")).append("\"}");
                    }
                    sb.append("], \"disappearedThreads\": [");
                    for (int i = 0; i < delta.getDisappearedThreads().size(); i++) {
                        ThreadInfo t = delta.getDisappearedThreads().get(i);
                        if (i > 0) sb.append(',');
                        sb.append('{').append("\"id\": ").append(t.getId())
                          .append(", \"name\": \"").append(t.getName().replace("\"", "\\\"")).append("\"}");
                    }
                    sb.append("], \"stateChanges\": [");
                    int j = 0;
                    for (Map.Entry<ThreadInfo, Thread.State> e : changes.entrySet()) {
                        if (j++ > 0) sb.append(',');
                        ThreadInfo t = e.getKey();
                        sb.append('{').append("\"id\": ").append(t.getId())
                          .append(", \"name\": \"").append(t.getName().replace("\"", "\\\"") )
                          .append("\", \"from\": \"").append(e.getValue())
                          .append("\", \"to\": \"").append(t.getState())
                          .append("\"}");
                    }
                    sb.append(']').append('}');
                    System.out.println(sb.toString());
                }
            } catch (Exception e) {
                System.err.println("Failed to parse dumps: " + e.getMessage());
            }
            return;
        }

        for (int fi = 0; fi < files.size(); fi++) {
            String path = files.get(fi);
            try {
                ThreadDump dump = DumpCache.load(Path.of(path));
                dump = new ThreadDump(dump.getTimestamp(), dump.getThreads(), getLabel(fi, path));

                if (format == OutputFormat.text) {
                    System.out.println("File: " + getLabel(fi, path));
                }

                Map<Thread.State, Long> counts = null;
                if (features.contains("counts") && !showDeadlocksOnly && filterState == null) {
                    counts = analyzer.computeStateCounts(dump);
                }
                List<DeadlockInfo> deadlocks =
                        (features.contains("deadlocks") || showDeadlocksOnly)
                        ? analyzer.detectDeadlocks(dump)
                        : List.of();
                Map<StackFrame, Long> hotspots = null;
                if (features.contains("hotspots") && hotspotLimit > 0 && !showDeadlocksOnly) {
                    hotspots = analyzer.computeStackHotspots(dump, hotspotLimit);
                }

                if (format == OutputFormat.text) {
                    if (showDeadlocksOnly) {
                        if (deadlocks.isEmpty()) {
                            System.out.println("No deadlocks detected");
                        } else {
                            int idx = 1;
                            for (DeadlockInfo dl : deadlocks) {
                                System.out.println("Deadlock " + idx++ + ":");
                                for (ThreadInfo t : dl.getThreads()) {
                                    System.out.printf("  [%d] %s%n", t.getId(), t.getName());
                                }
                            }
                        }
                    } else if (filterState != null) {
                        List<ThreadInfo> matches = dump.getThreads().stream()
                                .filter(t -> t.getState() == filterState)
                                .collect(Collectors.toList());
                        System.out.println("Threads in state " + filterState + ": " + matches.size());
                        for (ThreadInfo t : matches) {
                            System.out.printf("  [%d] %s%n", t.getId(), t.getName());
                        }
                    } else {
                        if (counts != null) {
                            for (Map.Entry<Thread.State, Long> e : counts.entrySet()) {
                                System.out.printf("  %s: %d%n", e.getKey(), e.getValue());
                            }
                        }
                        if (deadlocks != null && !deadlocks.isEmpty()) {
                            int idx = 1;
                            for (DeadlockInfo dl : deadlocks) {
                                System.out.println("Deadlock " + idx++ + ":");
                                for (ThreadInfo t : dl.getThreads()) {
                                    System.out.printf("  [%d] %s%n", t.getId(), t.getName());
                                }
                            }
                        }
                    }

                    if (hotspots != null) {
                        System.out.println("Top " + hotspotLimit + " stack frames:");
                        for (Map.Entry<StackFrame, Long> e : hotspots.entrySet()) {
                            StackFrame f = e.getKey();
                            System.out.printf("  %s.%s(%s:%d) - %d%n",
                                    f.getClassName(), f.getMethodName(),
                                    f.getFileName(), f.getLineNumber(), e.getValue());
                        }
                    }
                } else { // json output
                    StringBuilder sb = new StringBuilder();
                    sb.append('{');
                    sb.append("\"file\": \"").append(getLabel(fi, path).replace("\"", "\\\"")).append("\"");
                    if (counts != null) {
                        sb.append(", \"counts\": {");
                        boolean first = true;
                        for (var e : counts.entrySet()) {
                            if (!first) sb.append(',');
                            sb.append('"').append(e.getKey()).append('"').append(':').append(e.getValue());
                            first = false;
                        }
                        sb.append('}');
                    }
                    if (deadlocks != null && !deadlocks.isEmpty()) {
                        sb.append(", \"deadlocks\": [");
                        for (int i = 0; i < deadlocks.size(); i++) {
                            DeadlockInfo dl = deadlocks.get(i);
                            if (i > 0) sb.append(',');
                            sb.append('{');
                            sb.append("\"threads\": [");
                            for (int j = 0; j < dl.getThreads().size(); j++) {
                                ThreadInfo t = dl.getThreads().get(j);
                                if (j > 0) sb.append(',');
                                sb.append('{').append("\"id\": ").append(t.getId())
                                      .append(", \"name\": \"").append(t.getName().replace("\"", "\\\"")).append("\"}");
                            }
                            sb.append(']');
                            sb.append('}');
                        }
                        sb.append(']');
                    }
                    if (hotspots != null) {
                        sb.append(", \"hotspots\": [");
                        boolean first = true;
                        for (var e : hotspots.entrySet()) {
                            if (!first) sb.append(',');
                            StackFrame f = e.getKey();
                            sb.append('{');
                            sb.append("\"frame\": \"").append(f.toString().replace("\"", "\\\""))
                              .append("\", \"count\": ").append(e.getValue()).append('}');
                            first = false;
                        }
                        sb.append(']');
                    }
                    sb.append('}');
                    System.out.println(sb.toString());
                }
            } catch (Exception e) {
                System.err.println("Failed to parse " + path + ": " + e.getMessage());
            }
        }

        if (open && out != null) {
            try {
                if (java.awt.Desktop.isDesktopSupported()) {
                    java.awt.Desktop.getDesktop().browse(new java.io.File(out).toURI());
                } else {
                    System.err.println("--open is not supported on this platform");
                }
            } catch (Exception e) {
                System.err.println("Failed to open " + out + ": " + e.getMessage());
            }
        }
    }

    private String getLabel(int index, String path) {
        if (index < labels.size()) {
            return labels.get(index);
        }
        return path;
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
