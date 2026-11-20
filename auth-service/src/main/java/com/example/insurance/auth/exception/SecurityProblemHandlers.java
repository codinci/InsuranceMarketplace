package com.example.insurance.auth.exception;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Filter-level failures (no token, bad token, wrong role) happen before any controller runs, so the
 * advice above does not see them. These write the same RFC 7807 shape with the same codes.
 */
public final class SecurityProblemHandlers {

  private static final String CONTENT_TYPE = "application/problem+json";

  private SecurityProblemHandlers() {
    // Static helpers only.
  }

  public static AuthenticationEntryPoint entryPoint() {
    return (request, response, ex) ->
        write(response, ErrorCode.UNAUTHENTICATED, "Authentication is required.");
  }

  public static AccessDeniedHandler accessDenied() {
    return (request, response, ex) ->
        write(response, ErrorCode.FORBIDDEN, "You do not have permission for this action.");
  }

  private static void write(HttpServletResponse response, ErrorCode code, String detail)
      throws IOException {
    int status = code.status().value();
    response.setStatus(status);
    response.setContentType(CONTENT_TYPE);
    response
        .getWriter()
        .write(
            "{\"type\":\"about:blank\",\"title\":\""
                + code.status().getReasonPhrase()
                + "\",\"status\":"
                + status
                + ",\"detail\":\""
                + detail
                + "\",\"code\":\""
                + code.name()
                + "\"}");
  }
}
