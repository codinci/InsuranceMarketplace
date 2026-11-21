package com.example.insurance.policyservice.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How this service gets a SERVICE token from auth-service for internal calls (ADR-014). The secret
 * comes from the environment and is never logged, so {@link #toString()} leaves it out.
 */
@ConfigurationProperties(prefix = "policy.service-auth")
public record ServiceAuthProperties(
    URI tokenUrl, @DefaultValue("policy-service") String serviceName, String secret) {

  @Override
  public String toString() {
    return "ServiceAuthProperties[tokenUrl=" + tokenUrl + ", serviceName=" + serviceName + "]";
  }
}
