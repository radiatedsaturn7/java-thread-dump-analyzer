package com.example.web;

import java.net.InetSocketAddress;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Deque;
import java.util.ArrayDeque;

import com.example.analysis.DumpCache;

import javax.servlet.MultipartConfigElement;
import javax.servlet.http.Part;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import com.example.parser.ParserFactory;
import com.example.parser.ThreadDumpParser;
import com.example.model.ThreadDump;
import com.example.analysis.ThreadDumpAnalyzer;
import com.example.analysis.ThreadDelta;
import com.example.model.ThreadInfo;

public class WebServer {
    private static final int MAX_RECENT = 5;
    private static final Deque<String> RECENT_FILES = new ArrayDeque<>();
    public static void main(String[] args) throws Exception {
        int port = 8080;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        } else {
            String env = System.getenv("ANALYZER_PORT");
            if (env != null && !env.isEmpty()) {
                port = Integer.parseInt(env);
            }
        }

        Server server = new Server(new InetSocketAddress("localhost", port));

        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");

        context.addServlet(new ServletHolder(new UploadFormServlet()), "/");
        ServletHolder uploadHolder = new ServletHolder(new UploadServlet());
        uploadHolder.getRegistration().setMultipartConfig(new MultipartConfigElement("/tmp"));
        context.addServlet(uploadHolder, "/upload");
        context.addServlet(new ServletHolder(new ClearCacheServlet()), "/clear");

        server.setHandler(context);

        server.start();
        System.out.println("Server started at http://localhost:" + port);
        server.join();
    }

    static class UploadFormServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws javax.servlet.ServletException, java.io.IOException {
            resp.setContentType("text/html");
            PrintWriter w = resp.getWriter();
            w.println("<html><body>");
            w.println("<h1>Upload Thread Dumps</h1>");
            synchronized (RECENT_FILES) {
                if (!RECENT_FILES.isEmpty()) {
                    w.println("<h2>Recently analyzed</h2><ul>");
                    for (String name : RECENT_FILES) {
                        w.println("<li>" + name + "</li>");
                    }
                    w.println("</ul>");
                }
            }
            w.println("<form method='POST' action='/upload' enctype='multipart/form-data'>");
            w.println("<input type='file' name='dump' multiple/><br/>");
            w.println("<p>Or paste a thread dump:</p>");
            w.println("<textarea name='textdump' rows='15' cols='80'></textarea><br/>");
            w.println("<input type='submit' value='Analyze'/>");
            w.println("</form>");
            w.println("<form method='POST' action='/clear'>");
            w.println("<input type='submit' value='Clear Cache'/>");
            w.println("</form>");
            w.println("</body></html>");
        }
    }

    static class UploadServlet extends HttpServlet {

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws javax.servlet.ServletException, java.io.IOException {
            resp.setContentType("text/html");
            PrintWriter w = resp.getWriter();
            w.println("<html><body>");
            w.println("<h1>Thread State Counts</h1>");
            java.util.List<ThreadDump> parsed = new java.util.ArrayList<>();
            java.util.List<String> names = new java.util.ArrayList<>();
            for (Part part : req.getParts()) {
                if ("textdump".equals(part.getName()) && part.getSize() > 0) {
                    byte[] bytes = part.getInputStream().readAllBytes();
                    parsed.add(handleDump(bytes, "pasted dump", w));
                    names.add("pasted dump");
                } else if ("dump".equals(part.getName()) && part.getSize() > 0) {
                    byte[] bytes = part.getInputStream().readAllBytes();
                    parsed.add(handleFileDump(bytes, part.getSubmittedFileName(), w));
                    names.add(part.getSubmittedFileName());
                }
            }
            if (parsed.isEmpty()) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "No dump provided");
                return;
            }
            if (parsed.size() == 2) {
                writeDiff(parsed.get(0), parsed.get(1), w);
            }
            if (parsed.size() >= 2) {
                ThreadDumpAnalyzer analyzer = new ThreadDumpAnalyzer();
                java.util.List<com.example.model.ThreadInfo> high =
                        analyzer.findHighCpuThreads(parsed);
                if (!high.isEmpty()) {
                    w.println("<h2>High CPU thread candidates</h2>");
                    w.println("<ul>");
                    for (var t : high) {
                        w.println("<li>" + t.getName() + " (" + t.getId() + ")</li>");
                    }
                    w.println("</ul>");
                }
            }
            w.println("<a href='/'>Upload another file</a>");
            w.println("</body></html>");
        }

        private ThreadDump handleFileDump(byte[] bytes, String name, PrintWriter w) throws java.io.IOException {
            try {
                ThreadDump dump = getOrParse(bytes);
                writeCounts(name, dump, w);
                synchronized (RECENT_FILES) {
                    RECENT_FILES.remove(name);
                    RECENT_FILES.addFirst(name);
                    while (RECENT_FILES.size() > MAX_RECENT) {
                        RECENT_FILES.removeLast();
                    }
                }
                return dump;
            } catch (Exception e) {
                w.println("<p>Error: " + e.getMessage() + "</p>");
                return null;
            }
        }

        private ThreadDump handleDump(byte[] bytes, String displayName, PrintWriter w) throws java.io.IOException {
            try {
                ThreadDump dump = getOrParse(bytes);
                writeCounts(displayName, dump, w);
                return dump;
            } catch (Exception e) {
                w.println("<p>Error: " + e.getMessage() + "</p>");
                return null;
            }
        }

        private ThreadDump getOrParse(byte[] bytes) throws Exception {
            return DumpCache.load(bytes);
        }

        private void writeCounts(String title, ThreadDump dump, PrintWriter w) {
            ThreadDumpAnalyzer analyzer = new ThreadDumpAnalyzer();
            Map<Thread.State, Long> counts = analyzer.computeStateCounts(dump);
            w.println("<h2>" + title + "</h2>");
            w.println("<ul>");
            for (Map.Entry<Thread.State, Long> e : counts.entrySet()) {
                w.println("<li>" + e.getKey() + ": " + e.getValue() + "</li>");
            }
            w.println("</ul>");
        }

        private void writeDiff(ThreadDump before, ThreadDump after, PrintWriter w) {
            ThreadDumpAnalyzer analyzer = new ThreadDumpAnalyzer();
            ThreadDelta delta = analyzer.diff(before, after);
            Map<com.example.model.ThreadInfo, Thread.State> changes = analyzer.findStateChanges(before, after);
            w.println("<h2>Diff Results</h2>");
            w.println("<h3>New Threads</h3>");
            w.println("<ul>");
            for (com.example.model.ThreadInfo t : delta.getNewThreads()) {
                w.println("<li>" + t.getName() + " (" + t.getId() + ")</li>");
            }
            w.println("</ul>");
            w.println("<h3>Disappeared Threads</h3>");
            w.println("<ul>");
            for (com.example.model.ThreadInfo t : delta.getDisappearedThreads()) {
                w.println("<li>" + t.getName() + " (" + t.getId() + ")</li>");
            }
            w.println("</ul>");
            if (!changes.isEmpty()) {
                w.println("<h3>State Changes</h3>");
                w.println("<ul>");
                for (var e : changes.entrySet()) {
                    com.example.model.ThreadInfo t = e.getKey();
                    w.println("<li>" + t.getName() + " (" + t.getId() + "): " + e.getValue() + " -> " + t.getState() + "</li>");
                }
                w.println("</ul>");
            }
        }
    }

    static class ClearCacheServlet extends HttpServlet {
        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp)
                throws javax.servlet.ServletException, java.io.IOException {
            DumpCache.clear();
            resp.setContentType("text/html");
            PrintWriter w = resp.getWriter();
            w.println("<html><body>");
            w.println("<p>Cache cleared.</p>");
            w.println("<a href='/'>Back</a>");
            w.println("</body></html>");
        }
    }
}
