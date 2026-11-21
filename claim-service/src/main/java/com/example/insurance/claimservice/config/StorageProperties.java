package com.example.insurance.claimservice.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Object storage for claim documents (PLAN 12). {@code endpoint} is where this service uploads (the
 * compose network); {@code publicEndpoint} is what goes into presigned download URLs, because the
 * customer's browser must reach it. The secret keys come from the environment and are never logged,
 * so {@link #toString()} leaves them out.
 */
@ConfigurationProperties(prefix = "claim.storage")
public record StorageProperties(
    URI endpoint,
    URI publicEndpoint,
    @DefaultValue("us-east-1") String region,
    String bucket,
    String accessKey,
    String secretKey,
    @DefaultValue("5m") Duration presignTtl) {

  @Override
  public String toString() {
    return "StorageProperties[endpoint=" + endpoint + ", bucket=" + bucket + "]";
  }
}
