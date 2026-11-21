package com.example.insurance.claimservice.config;

import com.example.insurance.claimservice.client.PolicyClient;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Retry and circuit breaker for the policy-service fallback (PLAN 11, M5a). Retry rides out a short
 * blip; the breaker stops calling a policy-service that is down, so customers get a fast 503 with
 * Retry-After instead of waiting for timeouts.
 */
@Configuration
@EnableConfigurationProperties({PolicyServiceProperties.class, ServiceAuthProperties.class})
public class PolicyClientConfig {

  private static final String NAME = "policy-service";

  @Bean
  public CircuitBreaker policyCircuitBreaker() {
    return CircuitBreaker.of(
        NAME,
        CircuitBreakerConfig.custom()
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .recordException(PolicyClient::isTransient)
            .build());
  }

  @Bean
  public Retry policyRetry(PolicyServiceProperties properties) {
    return Retry.of(
        NAME,
        RetryConfig.custom()
            .maxAttempts(properties.maxAttempts())
            .waitDuration(properties.retryWait())
            .retryOnException(PolicyClient::isTransient)
            .ignoreExceptions(CallNotPermittedException.class)
            .build());
  }
}
