package com.example.insurance.auth.service;

import com.example.insurance.auth.config.AuthProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Token-bucket limits on login attempts (PLAN 12), one bucket per client IP and one per email
 * address. Buckets live in memory: a restart resets them, and each instance has its own. That is
 * acceptable for v1. The gateway adds a coarser limit at M2.
 */
@Component
public class LoginRateLimiter {

  private static final Duration WINDOW = Duration.ofMinutes(1);

  private final AuthProperties.Login limits;
  private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

  public LoginRateLimiter(AuthProperties properties) {
    this.limits = properties.login();
  }

  /** Returns true when the attempt is allowed and consumes one token from both buckets. */
  public boolean allow(String clientIp, String email) {
    boolean ipAllowed = bucket("ip:" + clientIp, limits.attemptsPerMinutePerIp()).tryConsume(1);
    boolean emailAllowed =
        bucket("email:" + email, limits.attemptsPerMinutePerEmail()).tryConsume(1);
    return ipAllowed && emailAllowed;
  }

  private Bucket bucket(String key, int perMinute) {
    return buckets.computeIfAbsent(
        key,
        ignored ->
            Bucket.builder()
                .addLimit(
                    Bandwidth.builder()
                        .capacity(perMinute)
                        .refillIntervally(perMinute, WINDOW)
                        .build())
                .build());
  }
}
