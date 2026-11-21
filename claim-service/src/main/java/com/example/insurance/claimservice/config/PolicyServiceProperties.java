package com.example.insurance.claimservice.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where policy-service is, for the fallback lookup (PLAN 11). {@code retryAfter} is the Retry-After
 * value sent to the customer when policy-service cannot be reached.
 */
@ConfigurationProperties(prefix = "claim.policy-service")
public record PolicyServiceProperties(
    URI baseUrl,
    @DefaultValue("1s") Duration connectTimeout,
    @DefaultValue("2s") Duration readTimeout,
    @DefaultValue("3") int maxAttempts,
    @DefaultValue("50ms") Duration retryWait,
    @DefaultValue("30s") Duration retryAfter) {}
