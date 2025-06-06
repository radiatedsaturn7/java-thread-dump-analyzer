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
}
