package com.example.insurance.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every HTTP request a correlation id (PLAN 10, 15).
 *
 * <p>The id comes from the incoming {@code X-Correlation-Id} header when it is well formed,
 * otherwise a new UUID is generated. It is put in the logging MDC so every log line written during
 * the request carries it, and it is echoed on the response. Downstream calls and events reuse the
 * same id, which is how one customer action can be traced across services.
 *
 * <p>Incoming values are validated so a client cannot inject arbitrary text into the logs.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-Correlation-Id";
  public static final String MDC_KEY = "correlationId";

  private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String correlationId = resolve(request.getHeader(HEADER));
    response.setHeader(HEADER, correlationId);
    MDC.put(MDC_KEY, correlationId);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }

  /** Returns the incoming id when it is valid, otherwise a new one. Package-visible for tests. */
  static String resolve(String incoming) {
    return Optional.ofNullable(incoming)
        .filter(value -> VALID_ID.matcher(value).matches())
        .orElseGet(() -> UUID.randomUUID().toString());
  }
}
