package com.example.insurance.policyservice.policy.service;

import com.example.insurance.policyservice.config.PaymentClientProperties;
import com.example.insurance.policyservice.exception.ApiException;
import com.example.insurance.policyservice.exception.ErrorCode;
import com.example.insurance.policyservice.policy.domain.Policy;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Asks payment-service to create the premium payment for a new policy (PLAN 8.2, M4b), with a
 * SERVICE token (ADR-014). The policy id is the idempotency key, so a retry never makes a second
 * payment. If payment-service cannot be reached the purchase fails with 503 and rolls back.
 */
@Component
public class PaymentClient {

  static final String IDEMPOTENCY_KEY = "Idempotency-Key";

  private static final Logger LOG = LoggerFactory.getLogger(PaymentClient.class);

  private final RestClient payments;
  private final ServiceTokens tokens;
  private final CircuitBreaker breaker;
  private final Retry retry;

  public PaymentClient(
      PaymentClientProperties properties,
      ServiceTokens tokens,
      CircuitBreaker breaker,
      Retry retry) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(properties.connectTimeout());
    factory.setReadTimeout(properties.readTimeout());
    this.payments =
        RestClient.builder()
            .baseUrl(properties.baseUrl().toString())
            .requestFactory(factory)
            .build();
    this.tokens = tokens;
    this.breaker = breaker;
    this.retry = retry;
  }

  /** The id of the premium payment for {@code policy}; the same id if asked again. */
  public UUID createPremium(Policy policy) {
    CreateIntent body =
        new CreateIntent(
            policy.getId(),
            policy.getPolicyNumber(),
            policy.getInsurerId(),
            policy.getHolderUserId(),
            policy.getAnnualPremium(),
            policy.getCurrency());
    String key = policy.getId().toString();
    Supplier<UUID> call =
        Retry.decorateSupplier(
            retry, CircuitBreaker.decorateSupplier(breaker, () -> send(key, body)));
    try {
      return call.get();
    } catch (CallNotPermittedException | RestClientException | IllegalStateException e) {
      LOG.warn("payment-service call failed for policy {}: {}", policy.getId(), e.toString());
      throw new ApiException(
          ErrorCode.PAYMENT_UNAVAILABLE, "Payments are unavailable right now. Try again shortly.");
    }
  }

  /**
   * True for failures worth retrying: no connection or a timeout, a 5xx, or a rejected token (the
   * cached token is dropped first). Any other 4xx is a bug on this side and is not retried.
   */
  public static boolean isTransient(Throwable e) {
    return e instanceof ResourceAccessException
        || e instanceof HttpServerErrorException
        || e instanceof HttpClientErrorException.Unauthorized;
  }

  private UUID send(String idempotencyKey, CreateIntent body) {
    try {
      PaymentIntent intent =
          payments
              .post()
              .uri("/internal/payments")
              .header("Authorization", "Bearer " + tokens.token())
              .header(IDEMPOTENCY_KEY, idempotencyKey)
              .contentType(MediaType.APPLICATION_JSON)
              .body(body)
              .retrieve()
              .body(PaymentIntent.class);
      if (intent == null || intent.id() == null) {
        throw new IllegalStateException("payment-service returned no payment id");
      }
      return intent.id();
    } catch (HttpClientErrorException.Unauthorized e) {
      tokens.forget();
      throw e;
    }
  }

  /** The request body of {@code POST /internal/payments}. */
  record CreateIntent(
      UUID policyId,
      String policyNumber,
      UUID insurerId,
      UUID holderUserId,
      BigDecimal amount,
      String currency) {}

  /** The part of payment-service's answer this service needs. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record PaymentIntent(UUID id, String status) {}
}
