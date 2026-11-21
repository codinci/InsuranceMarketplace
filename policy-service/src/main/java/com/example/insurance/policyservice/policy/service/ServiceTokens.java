package com.example.insurance.policyservice.policy.service;

import com.example.insurance.policyservice.config.ServiceAuthProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * A SERVICE token from auth-service, kept until shortly before it expires (ADR-014). Fetching one
 * per call would double the internal traffic of every purchase.
 */
@Component
public class ServiceTokens {

  /** Renew this long before expiry, so a token never expires on its way to payment-service. */
  static final Duration RENEW_BEFORE = Duration.ofSeconds(30);

  private final RestClient auth;
  private final ServiceAuthProperties properties;
  private final Clock clock;

  private String token;
  private Instant renewAt = Instant.MIN;

  public ServiceTokens(ServiceAuthProperties properties, Clock clock) {
    this.properties = properties;
    this.auth = RestClient.create();
    this.clock = clock;
  }

  /** A valid token, fetched only when the cached one is missing or close to expiry. */
  public synchronized String token() {
    Instant now = clock.instant();
    if (token == null || !now.isBefore(renewAt)) {
      TokenResponse response =
          auth.post()
              .uri(properties.tokenUrl())
              .contentType(MediaType.APPLICATION_JSON)
              .body(Map.of("service", properties.serviceName(), "secret", secretOrEmpty()))
              .retrieve()
              .body(TokenResponse.class);
      if (response == null || response.accessToken() == null) {
        throw new IllegalStateException("auth-service returned no service token");
      }
      token = response.accessToken();
      renewAt = now.plusSeconds(response.expiresIn()).minus(RENEW_BEFORE);
    }
    return token;
  }

  /** Drops the cached token, for example after payment-service rejected it. */
  public synchronized void forget() {
    token = null;
    renewAt = Instant.MIN;
  }

  private String secretOrEmpty() {
    return properties.secret() == null ? "" : properties.secret();
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record TokenResponse(String accessToken, String tokenType, long expiresIn) {}
}
