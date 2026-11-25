package com.example;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.apache.commons.lang3.StringUtils;

public final class App {

    private App() {
    }

    static String greeting(String name) {
        return "Hello, " + StringUtils.defaultIfBlank(name, "world") + " (maven-app)";
    }

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", exchange -> {
            byte[] body = greeting(System.getenv("GREETING_NAME")).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        System.out.println("maven-app listening on :8080");

        Integer blabla = null;
        System.println(blabla.toString());
    }
}
