package com.example.insurance.claimservice.support;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A stand-in for policy-service and auth-service on a local port: the token endpoint and the
 * internal policy read. Tests set which policies exist, or take the stub down to simulate an
 * outage.
 */
public final class PolicyServiceStub {

  public static final String TOKEN = "stub-service-token";

  private final HttpServer server;
  private final Map<UUID, String> policies = new ConcurrentHashMap<>();
  private final AtomicInteger lookups = new AtomicInteger();
  private volatile boolean down;

  private PolicyServiceStub(HttpServer server) {
    this.server = server;
  }

  public static PolicyServiceStub start() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      PolicyServiceStub stub = new PolicyServiceStub(server);
      server.createContext(
          "/internal/service-token",
          exchange -> {
            write(
                exchange,
                200,
                "{\"accessToken\":\"" + TOKEN + "\",\"tokenType\":\"Bearer\",\"expiresIn\":300}");
          });
      server.createContext("/internal/policies/", exchange -> stub.lookup(exchange));
      server.start();
      return stub;
    } catch (IOException e) {
      throw new IllegalStateException("Could not start the policy-service stub.", e);
    }
  }

  public String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  /** Makes {@code policyId} exist, answered with {@code json}. */
  public void givenPolicy(UUID policyId, String json) {
    policies.put(policyId, json);
  }

  /** While down, every lookup answers 503. */
  public void down(boolean isDown) {
    this.down = isDown;
  }

  public int lookups() {
    return lookups.get();
  }

  public void stop() {
    server.stop(0);
  }

  private void lookup(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
    lookups.incrementAndGet();
    if (down) {
      write(exchange, 503, "{\"code\":\"DOWN\"}");
      return;
    }
    String id = exchange.getRequestURI().getPath().substring("/internal/policies/".length());
    String json = policies.get(UUID.fromString(id));
    if (json == null) {
      write(exchange, 404, "{\"code\":\"POLICY_NOT_FOUND\"}");
    } else {
      write(exchange, 200, json);
    }
  }

  private static void write(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
      throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }
}
