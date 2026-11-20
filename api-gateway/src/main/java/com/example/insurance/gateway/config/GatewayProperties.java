package com.example.insurance.gateway.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gateway settings under {@code gateway.*} (see application.properties). Routes live in Spring
 * Cloud's own config.
 */
@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(Jwt jwt, Cors cors, RateLimit rateLimit) {

  /** Access tokens are checked against auth-service's published keys and issuer (PLAN 12). */
  public record Jwt(String issuer, String jwkSetUri) {}

  /**
   * Origins allowed to call the API from a browser. Empty until the frontend exists (PLAN 17, M2).
   */
  public record Cors(List<String> allowedOrigins) {

    public Cors {
      // An unset environment variable binds as one blank entry, which must mean "no origins".
      allowedOrigins =
          allowedOrigins == null
              ? List.of()
              : List.copyOf(
                  allowedOrigins.stream()
                      .filter(origin -> !origin.isBlank())
                      .map(String::trim)
                      .toList());
    }
  }

  /** Token bucket per client IP, stored in Redis (ADR-009). */
  public record RateLimit(int replenishRatePerSecond, long burstCapacity) {}
}
