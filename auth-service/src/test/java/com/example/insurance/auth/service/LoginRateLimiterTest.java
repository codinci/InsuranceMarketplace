package com.example.insurance.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.insurance.auth.config.AuthProperties;
import com.example.insurance.auth.support.TestSupport;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {

  private final LoginRateLimiter limiter =
      new LoginRateLimiter(
          new AuthProperties(
              TestSupport.properties().token(),
              TestSupport.properties().jwt(),
              new AuthProperties.Login(5, Duration.ofMinutes(15), 3, 2),
              TestSupport.properties().seed()));

  @Test
  void aClientIpGetsItsBudgetThenIsThrottled() {
    assertThat(limiter.allow("10.0.0.1", "a@example.com")).isTrue();
    assertThat(limiter.allow("10.0.0.1", "b@example.com")).isTrue();
    assertThat(limiter.allow("10.0.0.1", "c@example.com")).isTrue();
    assertThat(limiter.allow("10.0.0.1", "d@example.com")).isFalse();
  }

  @Test
  void aDifferentIpIsNotAffectedByTheFirst() {
    limiter.allow("10.0.0.2", "x@example.com");
    limiter.allow("10.0.0.2", "y@example.com");
    limiter.allow("10.0.0.2", "z@example.com");

    assertThat(limiter.allow("10.0.0.3", "w@example.com")).isTrue();
  }

  @Test
  void anEmailIsLimitedEvenWhenAttemptsComeFromManyIps() {
    assertThat(limiter.allow("10.1.0.1", "same@example.com")).isTrue();
    assertThat(limiter.allow("10.1.0.2", "same@example.com")).isTrue();
    assertThat(limiter.allow("10.1.0.3", "same@example.com")).isFalse();
  }
}
