package com.example.insurance.gateway.filter;

import com.example.insurance.gateway.exception.GatewayProblems;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * {@code /internal/**} is never published (PLAN 12). This runs before security and before any
 * route, so a valid token or a SERVICE role cannot reach it through the gateway. Services call each
 * other directly on the internal network.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalPathBlockFilter implements WebFilter {

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String path = exchange.getRequest().getPath().value();
    if (path.equals("/internal") || path.startsWith("/internal/")) {
      return GatewayProblems.write(
          exchange, HttpStatus.NOT_FOUND, "NOT_FOUND", "No such endpoint.");
    }
    return chain.filter(exchange);
  }
}
