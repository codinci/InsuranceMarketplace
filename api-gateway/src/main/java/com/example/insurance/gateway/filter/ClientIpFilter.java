package com.example.insurance.gateway.filter;

import java.net.InetSocketAddress;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Records the client address from the real connection, for the rate limiter and for the {@code
 * X-Forwarded-For} header that {@link ClientIpHeaderFilter} writes on the outgoing request (PLAN
 * 12, ADR-009).
 */
@Component
public class ClientIpFilter implements GlobalFilter, Ordered {

  public static final String CLIENT_IP_ATTRIBUTE = "gateway.clientIp";

  // Runs right after the correlation filter, before the rate limiter and before routing.
  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 1;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    exchange.getAttributes().put(CLIENT_IP_ATTRIBUTE, clientIp(exchange.getRequest()));
    return chain.filter(exchange);
  }

  static String clientIp(ServerHttpRequest request) {
    InetSocketAddress remote = request.getRemoteAddress();
    if (remote == null || remote.getAddress() == null) {
      return "unknown";
    }
    return remote.getAddress().getHostAddress();
  }
}
