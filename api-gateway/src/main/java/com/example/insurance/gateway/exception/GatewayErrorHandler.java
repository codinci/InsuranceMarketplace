package com.example.insurance.gateway.exception;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Last-resort handler for errors the gateway raises (unknown route, upstream down, timeouts). Each
 * is answered as a problem with a stable code. An upstream that is not running (for example a
 * service not built yet) becomes 503 UPSTREAM_UNAVAILABLE.
 */
@Component
@Order(-2)
public class GatewayErrorHandler implements ErrorWebExceptionHandler {

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
    if (exchange.getResponse().isCommitted()) {
      return Mono.error(ex);
    }
    if (ex instanceof ResponseStatusException statusException
        && statusException.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
      return GatewayProblems.write(
          exchange, HttpStatus.NOT_FOUND, "NOT_FOUND", "No such endpoint.");
    }
    if (isUpstreamDown(ex)) {
      return GatewayProblems.write(
          exchange,
          HttpStatus.SERVICE_UNAVAILABLE,
          "UPSTREAM_UNAVAILABLE",
          "The service for this request is not available. Try again later.");
    }
    if (hasCause(ex, TimeoutException.class)) {
      return GatewayProblems.write(
          exchange,
          HttpStatus.GATEWAY_TIMEOUT,
          "UPSTREAM_TIMEOUT",
          "The service for this request took too long to answer.");
    }
    return GatewayProblems.write(
        exchange, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Something went wrong.");
  }

  private static boolean isUpstreamDown(Throwable ex) {
    return hasCause(ex, ConnectException.class)
        || hasCause(ex, UnknownHostException.class)
        || hasCause(ex, NoRouteToHostException.class);
  }

  private static boolean hasCause(Throwable ex, Class<? extends Throwable> type) {
    for (Throwable current = ex; current != null; current = current.getCause()) {
      if (type.isInstance(current)) {
        return true;
      }
      if (current.getCause() == current) {
        break;
      }
    }
    return false;
  }
}
