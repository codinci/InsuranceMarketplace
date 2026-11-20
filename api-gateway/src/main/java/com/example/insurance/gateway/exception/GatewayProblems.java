package com.example.insurance.gateway.exception;

import java.nio.charset.StandardCharsets;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * RFC 7807 responses written by the gateway itself (PLAN 14). The detail texts are constants, so no
 * request data can reach the body. The codes match auth-service's codes where they overlap.
 */
public final class GatewayProblems {

  private GatewayProblems() {
    // Static helpers only.
  }

  public static ServerAuthenticationEntryPoint unauthenticated() {
    return (exchange, ex) ->
        write(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required.");
  }

  public static ServerAccessDeniedHandler forbidden() {
    return (exchange, ex) ->
        write(
            exchange,
            HttpStatus.FORBIDDEN,
            "FORBIDDEN",
            "You do not have permission for this action.");
  }

  /**
   * Writes a problem body. Status, code and detail are all constants chosen by this class and its
   * callers.
   */
  public static Mono<Void> write(
      ServerWebExchange exchange, HttpStatus status, String code, String detail) {
    exchange.getResponse().setStatusCode(status);
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    String body =
        "{\"type\":\"about:blank\",\"title\":\""
            + status.getReasonPhrase()
            + "\",\"status\":"
            + status.value()
            + ",\"detail\":\""
            + detail
            + "\",\"code\":\""
            + code
            + "\"}";
    DataBuffer buffer =
        exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
    return exchange.getResponse().writeWith(Mono.just(buffer));
  }
}
