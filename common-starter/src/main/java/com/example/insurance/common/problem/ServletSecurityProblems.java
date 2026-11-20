package com.example.insurance.common.problem;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Failures in the security filter chain (no token, bad token, wrong role) happen before any
 * controller runs, so a {@code @RestControllerAdvice} never sees them. These handlers write the
 * same RFC 7807 shape as {@link Problems} with the codes {@code UNAUTHENTICATED} and {@code
 * FORBIDDEN}. Servlet services only; the reactive gateway has its own.
 */
public final class ServletSecurityProblems {

  public static final String UNAUTHENTICATED = "UNAUTHENTICATED";
  public static final String FORBIDDEN = "FORBIDDEN";
  private static final String CONTENT_TYPE = "application/problem+json";

  private ServletSecurityProblems() {}

  public static AuthenticationEntryPoint entryPoint() {
    return (request, response, ex) ->
        write(response, HttpStatus.UNAUTHORIZED, UNAUTHENTICATED, "Authentication is required.");
  }

  public static AccessDeniedHandler accessDenied() {
    return (request, response, ex) ->
        write(
            response,
            HttpStatus.FORBIDDEN,
            FORBIDDEN,
            "You do not have permission for this action.");
  }

  /** The texts are constants, so building the JSON by hand cannot inject anything. */
  private static void write(
      HttpServletResponse response, HttpStatus status, String code, String detail)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(CONTENT_TYPE);
    response
        .getWriter()
        .write(
            "{\"type\":\"about:blank\",\"title\":\""
                + status.getReasonPhrase()
                + "\",\"status\":"
                + status.value()
                + ",\"detail\":\""
                + detail
                + "\",\"code\":\""
                + code
                + "\"}");
  }
}
