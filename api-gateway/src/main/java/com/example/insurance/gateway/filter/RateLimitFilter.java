package com.example.insurance.gateway.filter;

import static com.example.insurance.gateway.filter.ClientIpFilter.CLIENT_IP_ATTRIBUTE;

import com.example.insurance.gateway.exception.GatewayProblems;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Token bucket per client IP, stored in Redis (ADR-009). A limited request gets RFC 7807 code
 * RATE_LIMITED and the X-RateLimit headers. If Redis is unreachable, Spring's limiter allows the
 * request (fail open), so logins still work during a Redis outage. Account lockout in auth-service
 * still limits password guessing then.
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

  static final String ROUTE_ID = "gateway";

  private final RateLimiter<?> limiter;

  public RateLimitFilter(RateLimiter<?> limiter) {
    this.limiter = limiter;
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 2;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String clientIp = exchange.getAttribute(CLIENT_IP_ATTRIBUTE);
    return limiter
        .isAllowed(ROUTE_ID, clientIp == null ? "unknown" : clientIp)
        .flatMap(response -> {
          response
              .getHeaders()
              .forEach((name, value) -> exchange.getResponse().getHeaders().set(name, value));
          if (response.isAllowed()) {
            return chain.filter(exchange);
          }
          return GatewayProblems.write(
              exchange,
              HttpStatus.TOO_MANY_REQUESTS,
              "RATE_LIMITED",
              "Too many requests from this address. Wait a moment and try again.");
        });
  }
}
