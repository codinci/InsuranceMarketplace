package com.example.insurance.policyservice.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where payment-service is and how patiently to call it (PLAN 11, M4b). The whole purchase waits
 * for this call, so the timeouts are short.
 */
@ConfigurationProperties(prefix = "policy.payment")
public record PaymentClientProperties(
    URI baseUrl,
    @DefaultValue("PT1S") Duration connectTimeout,
    @DefaultValue("PT2S") Duration readTimeout,
    @DefaultValue("3") int maxAttempts,
    @DefaultValue("PT0.2S") Duration retryWait) {}
