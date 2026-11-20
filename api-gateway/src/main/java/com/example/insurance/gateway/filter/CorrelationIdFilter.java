package com.example.insurance.gateway.filter;

import com.example.insurance.common.web.CorrelationIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gives every request a correlation id (PLAN 10, 15). The gateway is reactive, so the id is not
 * kept in a thread-local across the request. It is stored on the exchange, and the access line is
 * written inside an explicit MDC scope. The same id is forwarded to services and echoed to the
 * client.
 */
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

  static final String ATTRIBUTE = "gateway.correlationId";
  private static final Logger ACCESS = LoggerFactory.getLogger("access");

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String id =
        CorrelationIds.resolve(exchange.getRequest().getHeaders().getFirst(CorrelationIds.HEADER));
    exchange.getAttributes().put(ATTRIBUTE, id);
    exchange.getResponse().getHeaders().set(CorrelationIds.HEADER, id);
    ServerWebExchange mutated =
        exchange
            .mutate()
            .request(
                exchange
                    .getRequest()
                    .mutate()
                    .headers(h -> h.set(CorrelationIds.HEADER, id))
                    .build())
            .build();
    long started = System.nanoTime();
    return chain.filter(mutated).doFinally(signal -> writeAccessLine(mutated, id, started));
  }

  private static void writeAccessLine(ServerWebExchange exchange, String id, long started) {
    HttpStatusCode code = exchange.getResponse().getStatusCode();
    Integer status = code == null ? null : code.value();
    long millis = (System.nanoTime() - started) / 1_000_000;
    try (MDC.MDCCloseable ignored = MDC.putCloseable(CorrelationIds.MDC_KEY, id)) {
      // Method and path only: no query string, headers or body, so no tokens or personal data reach
      // the log.
      ACCESS.info(
          "request method={} path={} status={} durationMs={}",
          exchange.getRequest().getMethod(),
          exchange.getRequest().getPath().value(),
          status,
          millis);
    }
  }
}
