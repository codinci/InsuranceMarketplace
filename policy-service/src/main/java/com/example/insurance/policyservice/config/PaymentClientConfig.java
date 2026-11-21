package com.example.insurance.policyservice.config;

import com.example.insurance.policyservice.policy.service.PaymentClient;
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
 * Retry and circuit breaker for the payment-service call (PLAN 11). Retry rides out a short blip;
 * the breaker stops calling a service that is down, so purchases fail fast with 503 instead of each
 * one waiting for timeouts.
 */
@Configuration
@EnableConfigurationProperties({PaymentClientProperties.class, ServiceAuthProperties.class})
public class PaymentClientConfig {

  private static final String NAME = "payment-service";

  @Bean
  public CircuitBreaker paymentCircuitBreaker() {
    return CircuitBreaker.of(
        NAME,
        CircuitBreakerConfig.custom()
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .recordException(PaymentClient::isTransient)
            .build());
  }

  @Bean
  public Retry paymentRetry(PaymentClientProperties properties) {
    return Retry.of(
        NAME,
        RetryConfig.custom()
            .maxAttempts(properties.maxAttempts())
            .waitDuration(properties.retryWait())
            .retryOnException(PaymentClient::isTransient)
            .ignoreExceptions(CallNotPermittedException.class)
            .build());
  }
}
