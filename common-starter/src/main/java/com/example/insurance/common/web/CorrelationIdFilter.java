package com.example.insurance.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every servlet (MVC) request a correlation id (PLAN 10, 15). The rules live in {@link
 * CorrelationIds}.
 *
 * <p>The id is put in the logging MDC so every log line written during the request carries it, and
 * it is echoed on the response. Downstream calls and events reuse the same id. Only registered in
 * servlet web applications (see {@link CommonWebAutoConfiguration}).
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

  public static final String HEADER = CorrelationIds.HEADER;
  public static final String MDC_KEY = CorrelationIds.MDC_KEY;

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
      // The finally matters: the thread goes back to the pool, and a leftover id would show up in
      // another request's logs.
      MDC.remove(MDC_KEY);
    }
  }

  /** Package-visible for tests. */
  static String resolve(String incoming) {
    return CorrelationIds.resolve(incoming);
  }
}
