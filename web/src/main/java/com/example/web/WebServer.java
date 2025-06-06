package com.example.web;

import java.net.InetSocketAddress;

import org.eclipse.jetty.server.Server;

public class WebServer {
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
        server.start();
        System.out.println("Server started at http://localhost:" + port);
        server.join();
    }
}
