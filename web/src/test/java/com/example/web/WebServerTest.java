package com.example.web;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import javax.servlet.MultipartConfigElement;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class WebServerTest {
    private Server server;
    private int port;

    @BeforeEach
    public void startServer() throws Exception {
        server = new Server(new InetSocketAddress("localhost", 0));
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        context.addServlet(new ServletHolder(new WebServer.UploadFormServlet()), "/");
        ServletHolder uploadHolder = new ServletHolder(new WebServer.UploadServlet());
        uploadHolder.getRegistration().setMultipartConfig(new MultipartConfigElement("/tmp"));
        context.addServlet(uploadHolder, "/upload");
        context.addServlet(new ServletHolder(new WebServer.ClearCacheServlet()), "/clear");
        server.setHandler(context);
        server.start();
        ServerConnector connector = (ServerConnector) server.getConnectors()[0];
        port = connector.getLocalPort();
    }

    @AfterEach
    public void stopServer() throws Exception {
        if (server != null) {
            server.stop();
            server.join();
        }
    }

    @Test
    public void uploadSingleDumpShowsCounts() throws Exception {
        URL url = new URL("http://localhost:" + port + "/upload");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        String boundary = "----testBoundary";
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        File dump = new File(getClass().getResource("/hotspot.txt").toURI());
        try (OutputStream out = conn.getOutputStream()) {
            writeFilePart(out, dump, boundary);
        }

        int code = conn.getResponseCode();
        ByteArrayOutputStream resp = new ByteArrayOutputStream();
        try (InputStream in = conn.getInputStream()) {
            in.transferTo(resp);
        }
        String body = resp.toString();
        assertEquals(200, code);
        assertTrue(body.contains("RUNNABLE"));
    }

    @Test
    public void recentFilesListedOnForm() throws Exception {
        // upload first
        URL url = new URL("http://localhost:" + port + "/upload");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        String boundary = "----testBoundary";
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        File dump = new File(getClass().getResource("/hotspot.txt").toURI());
        try (OutputStream out = conn.getOutputStream()) {
            writeFilePart(out, dump, boundary);
        }
        conn.getResponseCode();
        // fetch form
        HttpURLConnection form = (HttpURLConnection) new URL("http://localhost:" + port + "/").openConnection();
        ByteArrayOutputStream resp = new ByteArrayOutputStream();
        try (InputStream in = form.getInputStream()) {
            in.transferTo(resp);
        }
        String body = resp.toString();
        assertTrue(body.contains("hotspot.txt"));
    }

    @Test
    public void highCpuWarningDisplayed() throws Exception {
        URL url = new URL("http://localhost:" + port + "/upload");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        String boundary = "----testBoundary";
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        File before = new File(getClass().getResource("/diff_before.txt").toURI());
        File after = new File(getClass().getResource("/diff_after.txt").toURI());
        try (OutputStream out = conn.getOutputStream()) {
            writeMultiPart(out, before, boundary, false);
            writeMultiPart(out, after, boundary, true);
        }

        int code = conn.getResponseCode();
        ByteArrayOutputStream resp = new ByteArrayOutputStream();
        try (InputStream in = conn.getInputStream()) {
            in.transferTo(resp);
        }
        String body = resp.toString();
        assertEquals(200, code);
        assertTrue(body.contains("High CPU thread candidates"));
        assertTrue(body.contains("worker-2"));
    }

    @Test
    public void clearCacheEndpointClearsCache() throws Exception {
        File dump = new File(getClass().getResource("/hotspot.txt").toURI());
        com.example.model.ThreadDump first = com.example.analysis.DumpCache.load(dump.toPath());
        com.example.model.ThreadDump second = com.example.analysis.DumpCache.load(dump.toPath());
        assertSame(first, second);

        HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + port + "/clear").openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        int code = conn.getResponseCode();
        assertEquals(200, code);

        com.example.model.ThreadDump third = com.example.analysis.DumpCache.load(dump.toPath());
        assertNotSame(first, third);
    }

    private static void writeFilePart(OutputStream out, File file, String boundary) throws Exception {
        String name = file.getName();
        out.write(("--" + boundary + "\r\n").getBytes());
        out.write(("Content-Disposition: form-data; name=\"dump\"; filename=\"" + name + "\"\r\n").getBytes());
        out.write("Content-Type: text/plain\r\n\r\n".getBytes());
        Files.copy(file.toPath(), out);
        out.write("\r\n--".getBytes());
        out.write(boundary.getBytes());
        out.write("--\r\n".getBytes());
    }

    private static void writeMultiPart(OutputStream out, File file, String boundary, boolean last) throws Exception {
        String name = file.getName();
        out.write(("--" + boundary + "\r\n").getBytes());
        out.write(("Content-Disposition: form-data; name=\"dump\"; filename=\"" + name + "\"\r\n").getBytes());
        out.write("Content-Type: text/plain\r\n\r\n".getBytes());
        Files.copy(file.toPath(), out);
        out.write("\r\n".getBytes());
        out.write(("--" + boundary + (last ? "--\r\n" : "\r\n")).getBytes());
    }
}
