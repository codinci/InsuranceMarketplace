package com.example.insurance.auth.config;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for service tokens under {@code auth.service.*} (ADR-014). {@code secrets} maps a
 * calling service's name to its secret, which comes from the environment and is never stored.
 *
 * @param tokenTtl how long a service token lives; five minutes by default
 * @param secrets service name to secret; a service with a blank secret cannot get a token
 */
@ConfigurationProperties(prefix = "auth.service")
public record ServiceAuthProperties(Duration tokenTtl, Map<String, String> secrets) {

  public ServiceAuthProperties {
    tokenTtl = tokenTtl == null ? Duration.ofMinutes(5) : tokenTtl;
    secrets = secrets == null ? Map.of() : Map.copyOf(secrets);
  }
}
