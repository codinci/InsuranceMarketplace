package com.example.insurance.gateway.filter;

import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

/**
 * Writes {@code X-Forwarded-For} on the request the gateway sends to a service. Spring Cloud
 * Gateway removes incoming forwarded headers at order 0, so this runs after that and sets the real
 * client address (PLAN 12, ADR-009). {@code set} replaces anything the client sent, so a spoofed
 * value cannot survive.
 */
@Component
public class ClientIpHeaderFilter implements HttpHeadersFilter, Ordered {

  static final String X_FORWARDED_FOR = "X-Forwarded-For";

  @Override
  public int getOrder() {
    return 1;
  }

  @Override
  public boolean supports(Type type) {
    return type == Type.REQUEST;
  }

  @Override
  public HttpHeaders filter(HttpHeaders input, ServerWebExchange exchange) {
    HttpHeaders output = new HttpHeaders();
    output.putAll(input);
    String clientIp = exchange.getAttribute(ClientIpFilter.CLIENT_IP_ATTRIBUTE);
    output.set(X_FORWARDED_FOR, clientIp == null ? "unknown" : clientIp);
    return output;
  }
}
