package com.example.insurance.gateway.support;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A tiny stand-in for a downstream service, built on the JDK HTTP server so the gateway tests need
 * no running services. It answers every path with its name and the path it received, serves the
 * given JWKS on the well-known path, and remembers the last request headers so tests can see what
 * the gateway forwarded.
 */
public final class StubUpstream implements AutoCloseable {

  private final String name;
  private final HttpServer server;
  private final AtomicReference<com.sun.net.httpserver.Headers> lastHeaders =
      new AtomicReference<>();
  private volatile String jwks = "{\"keys\":[]}";

  private StubUpstream(String name, HttpServer server) {
    this.name = name;
    this.server = server;
    server.createContext(
        "/",
        exchange -> {
          lastHeaders.set(exchange.getRequestHeaders());
          String path = exchange.getRequestURI().getPath();
          String body =
              path.equals("/.well-known/jwks.json")
                  ? jwks
                  : "{\"service\":\"" + name + "\",\"path\":\"" + path + "\"}";
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
          }
        });
  }

  public static StubUpstream start(String name) {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.start();
      return new StubUpstream(name, server);
    } catch (IOException ex) {
      throw new IllegalStateException(ex);
    }
  }

  public StubUpstream withJwks(String jwksJson) {
    this.jwks = jwksJson;
    return this;
  }

  public String uri() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  public String lastHeader(String headerName) {
    com.sun.net.httpserver.Headers headers = lastHeaders.get();
    return headers == null ? null : headers.getFirst(headerName);
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
