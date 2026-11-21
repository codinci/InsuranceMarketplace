package com.example.insurance.claimservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.insurance.claimservice.config.PolicyClientConfig;
import com.example.insurance.claimservice.config.PolicyServiceProperties;
import com.example.insurance.claimservice.config.ServiceAuthProperties;
import com.example.insurance.claimservice.dto.PolicyLookup;
import com.example.insurance.claimservice.exception.ApiException;
import com.example.insurance.claimservice.exception.ErrorCode;
import com.example.insurance.claimservice.support.PolicyServiceStub;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * The policy-service fallback client against the HTTP stub: retry, the circuit breaker, and the 503
 * with Retry-After (PLAN 11, M5a). Real config beans, built without Spring.
 */
class PolicyClientTest {

  private final PolicyServiceStub stub = PolicyServiceStub.start();
  private final PolicyClientConfig config = new PolicyClientConfig();
  private final UUID policyId = UUID.randomUUID();

  private PolicyClient client;

  @BeforeEach
  void client() {
    PolicyServiceProperties properties =
        new PolicyServiceProperties(
            URI.create(stub.baseUrl()),
            Duration.ofSeconds(1),
            Duration.ofSeconds(2),
            3,
            Duration.ofMillis(10),
            Duration.ofSeconds(30));
    CircuitBreaker breaker = config.policyCircuitBreaker();
    Retry retry = config.policyRetry(properties);
    ServiceTokens tokens =
        new ServiceTokens(
            new ServiceAuthProperties(
                URI.create(stub.baseUrl() + "/internal/service-token"),
                "claim-service",
                "test-only-secret"),
            Clock.systemUTC());
    client = new PolicyClient(properties, tokens, breaker, retry);
  }

  @AfterEach
  void stopStub() {
    stub.stop();
  }

  @Test
  void aKnownPolicyIsReturned() {
    // A complete answer: policy-service always sends policyVersion, which is a primitive here.
    stub.givenPolicy(
        policyId,
        "{\"policyId\":\"" + policyId + "\",\"policyNumber\":\"POL-1\",\"policyVersion\":2}");

    Optional<PolicyLookup> found = client.fetch(policyId);

    assertThat(found).isPresent();
    assertThat(found.get().policyNumber()).isEqualTo("POL-1");
  }

  @Test
  void anUnknownPolicyIsEmptyAndNotRetried() {
    assertThat(client.fetch(policyId)).isEmpty();
    assertThat(stub.lookups()).isEqualTo(1);
  }

  @Test
  void aDownServiceIsTriedThreeTimesBeforeGivingUp() {
    stub.down(true);
    assertThatThrownBy(() -> client.fetch(policyId)).isInstanceOf(ApiException.class);
    assertThat(stub.lookups()).isEqualTo(3);
  }

  @Test
  void aDownPolicyServiceGivesA503WithRetryAfter() {
    stub.down(true);

    assertThatThrownBy(() -> client.fetch(policyId))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.code()).isEqualTo(ErrorCode.POLICY_UNAVAILABLE);
              assertThat(e.retryAfter()).contains(Duration.ofSeconds(30));
            });
  }

  @Test
  void theBreakerOpensAndThenStopsCalling() {
    stub.down(true);
    for (int i = 0; i < 2; i++) {
      assertThatThrownBy(() -> client.fetch(policyId)).isInstanceOf(ApiException.class);
    }
    int callsSoFar = stub.lookups();

    assertThatThrownBy(() -> client.fetch(policyId))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.POLICY_UNAVAILABLE));
    assertThat(stub.lookups()).isEqualTo(callsSoFar);
  }

  @Test
  void onlyConnectionErrorsServerErrorsAndRejectedTokensAreTransient() {
    assertThat(PolicyClient.isTransient(new ResourceAccessException("timeout"))).isTrue();
    assertThat(
            PolicyClient.isTransient(
                HttpServerErrorException.create(
                    org.springframework.http.HttpStatus.BAD_GATEWAY, "", null, null, null)))
        .isTrue();
    assertThat(
            PolicyClient.isTransient(
                HttpClientErrorException.create(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "", null, null, null)))
        .isTrue();
    assertThat(
            PolicyClient.isTransient(
                HttpClientErrorException.create(
                    org.springframework.http.HttpStatus.NOT_FOUND, "", null, null, null)))
        .isFalse();
  }

  /** Keeps the assertion above readable: the lookup is an Optional inside a holder. */
  private record PolicyLookupHolder(
      Optional<com.example.insurance.claimservice.dto.PolicyLookup> lookup) {}
}
