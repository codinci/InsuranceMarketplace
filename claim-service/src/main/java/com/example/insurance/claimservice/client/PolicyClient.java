package com.example.insurance.claimservice.client;

import com.example.insurance.claimservice.config.PolicyServiceProperties;
import com.example.insurance.claimservice.dto.PolicyLookup;
import com.example.insurance.claimservice.exception.ApiException;
import com.example.insurance.claimservice.exception.ErrorCode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Asks policy-service for one policy when the read model does not have it (PLAN 11, M5a), with a
 * SERVICE token (ADR-014). Retry and the circuit breaker stop a dead policy-service from making
 * every request wait; the caller then gets 503 with Retry-After.
 */
@Component
public class PolicyClient {

  private static final Logger LOG = LoggerFactory.getLogger(PolicyClient.class);

  private final RestClient policies;
  private final ServiceTokens tokens;
  private final CircuitBreaker breaker;
  private final Retry retry;
  private final Duration retryAfter;

  public PolicyClient(
      PolicyServiceProperties properties,
      ServiceTokens tokens,
      CircuitBreaker breaker,
      Retry retry) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(properties.connectTimeout());
    factory.setReadTimeout(properties.readTimeout());
    this.policies =
        RestClient.builder()
            .baseUrl(properties.baseUrl().toString())
            .requestFactory(factory)
            .build();
    this.tokens = tokens;
    this.breaker = breaker;
    this.retry = retry;
    this.retryAfter = properties.retryAfter();
  }

  /** The policy as policy-service has it, or empty if policy-service does not know the id. */
  public Optional<PolicyLookup> fetch(UUID policyId) {
    Supplier<Optional<PolicyLookup>> call =
        Retry.decorateSupplier(
            retry, CircuitBreaker.decorateSupplier(breaker, () -> send(policyId)));
    try {
      return call.get();
    } catch (CallNotPermittedException | RestClientException | IllegalStateException e) {
      LOG.warn(
          "policy-service lookup failed for policy {}: {}",
          policyId,
          NestedExceptionUtils.getMostSpecificCause(e).toString());
      throw new ApiException(
          ErrorCode.POLICY_UNAVAILABLE,
          "Policies are unavailable right now. Try again shortly.",
          retryAfter);
    }
  }

  /**
   * True for failures worth retrying: no connection or a timeout, a 5xx, or a rejected token (the
   * cached token is dropped first). A 404 means the policy does not exist, which is an answer, not
   * a failure.
   */
  public static boolean isTransient(Throwable e) {
    return e instanceof ResourceAccessException
        || e instanceof HttpServerErrorException
        || e instanceof HttpClientErrorException.Unauthorized;
  }

  private Optional<PolicyLookup> send(UUID policyId) {
    try {
      PolicyLookup lookup =
          policies
              .get()
              .uri("/internal/policies/{policyId}", policyId)
              .header("Authorization", "Bearer " + tokens.token())
              .retrieve()
              .body(PolicyLookup.class);
      if (lookup == null || lookup.policyId() == null) {
        throw new IllegalStateException("policy-service returned no policy");
      }
      return Optional.of(lookup);
    } catch (HttpClientErrorException.NotFound e) {
      return Optional.empty();
    } catch (HttpClientErrorException.Unauthorized e) {
      tokens.forget();
      throw e;
    }
  }
}
