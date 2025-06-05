package com.example.web;

import org.eclipse.jetty.server.Server;

public class WebServer {
    public static void main(String[] args) throws Exception {
        Server server = new Server(8080);
        server.start();
        System.out.println("Server started at http://localhost:8080");
        server.join();
    }
}
