package com.example.insurance.policyservice.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A stand-in for payment-service and auth-service's token endpoint over real HTTP, so tests cover
 * the client, its headers, retry and circuit breaker (M4b). Built on the JDK's own HTTP server: no
 * extra test dependency.
 */
public final class PaymentServiceStub {

  public static final String TOKEN = "stub-service-token";

  /** One request seen by {@code POST /internal/payments}. */
  public record Received(String authorization, String idempotencyKey, String body) {}

  private final HttpServer server;
  private final Map<String, UUID> paymentsByKey = new ConcurrentHashMap<>();
  private final List<Received> received = new CopyOnWriteArrayList<>();
  private final AtomicInteger tokenRequests = new AtomicInteger();
  private final AtomicInteger failuresLeft = new AtomicInteger();
  private volatile boolean down;

  private PaymentServiceStub(HttpServer server) {
    this.server = server;
  }

  public static PaymentServiceStub start() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      PaymentServiceStub stub = new PaymentServiceStub(server);
      server.createContext("/internal/service-token", stub::token);
      server.createContext("/internal/payments", stub::payments);
      server.start();
      return stub;
    } catch (IOException e) {
      throw new IllegalStateException("Could not start the payment-service stub", e);
    }
  }

  public void stop() {
    server.stop(0);
  }

  public String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  /** Every call answers 503 until {@link #up()}. */
  public void down() {
    down = true;
  }

  public void up() {
    down = false;
    failuresLeft.set(0);
  }

  /** The next {@code count} calls answer 503, then calls succeed again. */
  public void failNext(int count) {
    failuresLeft.set(count);
  }

  /** The payment id handed out for this idempotency key, or null. */
  public UUID paymentFor(String idempotencyKey) {
    return paymentsByKey.get(idempotencyKey);
  }

  public List<Received> received() {
    return List.copyOf(received);
  }

  public int tokenRequests() {
    return tokenRequests.get();
  }

  private void token(HttpExchange exchange) throws IOException {
    drain(exchange);
    tokenRequests.incrementAndGet();
    respond(
        exchange,
        200,
        "{\"accessToken\":\"" + TOKEN + "\",\"tokenType\":\"Bearer\",\"expiresIn\":300}");
  }

  private void payments(HttpExchange exchange) throws IOException {
    String body = drain(exchange);
    String key = exchange.getRequestHeaders().getFirst("Idempotency-Key");
    received.add(new Received(exchange.getRequestHeaders().getFirst("Authorization"), key, body));
    boolean injectedFailure = failuresLeft.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0;
    if (down || injectedFailure) {
      respond(exchange, 503, "{\"code\":\"UNAVAILABLE\"}");
      return;
    }
    UUID id = paymentsByKey.computeIfAbsent(key, k -> UUID.randomUUID());
    respond(exchange, 201, "{\"id\":\"" + id + "\",\"status\":\"PENDING\"}");
  }

  private static String drain(HttpExchange exchange) throws IOException {
    try (InputStream in = exchange.getRequestBody()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static void respond(HttpExchange exchange, int status, String json) throws IOException {
    byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }
}
