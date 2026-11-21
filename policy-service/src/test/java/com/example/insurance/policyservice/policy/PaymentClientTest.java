package com.example.insurance.policyservice.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.insurance.policyservice.config.PaymentClientConfig;
import com.example.insurance.policyservice.config.PaymentClientProperties;
import com.example.insurance.policyservice.config.ServiceAuthProperties;
import com.example.insurance.policyservice.exception.ApiException;
import com.example.insurance.policyservice.exception.ErrorCode;
import com.example.insurance.policyservice.policy.domain.Policy;
import com.example.insurance.policyservice.policy.service.PaymentClient;
import com.example.insurance.policyservice.policy.service.ServiceTokens;
import com.example.insurance.policyservice.support.PaymentServiceStub;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * The payment-service client against the HTTP stub: token caching, retry, and the circuit breaker
 * (PLAN 11, M4b). Real config beans, built without Spring.
 */
class PaymentClientTest {

  private final PaymentServiceStub stub = PaymentServiceStub.start();
  private final MovableClock clock = new MovableClock(Instant.parse("2026-11-21T08:00:00Z"));
  private final PaymentClientConfig config = new PaymentClientConfig();

  private CircuitBreaker breaker;
  private ServiceTokens tokens;
  private PaymentClient client;

  @BeforeEach
  void client() {
    PaymentClientProperties properties =
        new PaymentClientProperties(
            URI.create(stub.baseUrl()),
            Duration.ofSeconds(1),
            Duration.ofSeconds(2),
            3,
            Duration.ofMillis(10));
    breaker = config.paymentCircuitBreaker();
    Retry retry = config.paymentRetry(properties);
    tokens =
        new ServiceTokens(
            new ServiceAuthProperties(
                URI.create(stub.baseUrl() + "/internal/service-token"),
                "policy-service",
                "test-only-secret"),
            clock);
    client = new PaymentClient(properties, tokens, breaker, retry);
  }

  @AfterEach
  void stopStub() {
    stub.stop();
  }

  @Test
  void theSamePolicyGetsTheSamePayment() {
    Policy policy = PolicyFixtures.pendingPolicy();

    UUID first = client.createPremium(policy);
    UUID second = client.createPremium(policy);

    assertThat(second).isEqualTo(first);
    assertThat(stub.received()).allMatch(r -> r.idempotencyKey().equals(policy.getId().toString()));
  }

  @Test
  void theServiceTokenIsReusedUntilCloseToExpiry() {
    client.createPremium(PolicyFixtures.pendingPolicy());
    client.createPremium(PolicyFixtures.pendingPolicy());
    assertThat(stub.tokenRequests()).isEqualTo(1);

    // The stub's tokens last 300 s; renewal starts 30 s before that.
    clock.advance(Duration.ofSeconds(271));
    client.createPremium(PolicyFixtures.pendingPolicy());
    assertThat(stub.tokenRequests()).isEqualTo(2);
  }

  @Test
  void aForgottenTokenIsFetchedAgain() {
    tokens.token();
    tokens.forget();
    tokens.token();

    assertThat(stub.tokenRequests()).isEqualTo(2);
  }

  @Test
  void twoFailuresAreRetriedAndTheThirdAttemptSucceeds() {
    stub.failNext(2);

    assertThat(client.createPremium(PolicyFixtures.pendingPolicy())).isNotNull();
    assertThat(stub.received()).hasSize(3);
  }

  @Test
  void threeFailuresGiveA503() {
    stub.down();

    assertThatThrownBy(() -> client.createPremium(PolicyFixtures.pendingPolicy()))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.PAYMENT_UNAVAILABLE));
    assertThat(stub.received()).hasSize(3);
  }

  @Test
  void theBreakerOpensAndThenStopsCalling() {
    stub.down();
    for (int i = 0; i < 2; i++) {
      assertThatThrownBy(() -> client.createPremium(PolicyFixtures.pendingPolicy()))
          .isInstanceOf(ApiException.class);
    }
    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    int callsSoFar = stub.received().size();

    assertThatThrownBy(() -> client.createPremium(PolicyFixtures.pendingPolicy()))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.PAYMENT_UNAVAILABLE));
    assertThat(stub.received()).hasSize(callsSoFar);
  }

  @Test
  void onlyConnectionErrorsServerErrorsAndRejectedTokensAreTransient() {
    assertThat(PaymentClient.isTransient(new ResourceAccessException("timeout"))).isTrue();
    assertThat(
            PaymentClient.isTransient(
                HttpServerErrorException.create(
                    org.springframework.http.HttpStatus.BAD_GATEWAY, "", null, null, null)))
        .isTrue();
    assertThat(
            PaymentClient.isTransient(
                HttpClientErrorException.create(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "", null, null, null)))
        .isTrue();
    assertThat(
            PaymentClient.isTransient(
                HttpClientErrorException.create(
                    org.springframework.http.HttpStatus.CONFLICT, "", null, null, null)))
        .isFalse();
    assertThat(PaymentClient.isTransient(new IllegalStateException())).isFalse();
  }

  /** A clock the test can move forward. */
  private static final class MovableClock extends Clock {
    private final AtomicReference<Instant> now;

    MovableClock(Instant start) {
      now = new AtomicReference<>(start);
    }

    void advance(Duration by) {
      now.updateAndGet(t -> t.plus(by));
    }

    @Override
    public Instant instant() {
      return now.get();
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }
  }
}
