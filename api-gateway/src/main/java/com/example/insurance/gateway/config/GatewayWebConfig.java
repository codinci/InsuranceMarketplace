package com.example.insurance.gateway.config;

import java.util.List;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * CORS from configuration and the Redis-backed rate limiter (ADR-009). The CORS source is wired
 * into the security chain, so a preflight is answered before authentication is required.
 */
@Configuration
public class GatewayWebConfig {

  @Bean
  CorsConfigurationSource corsConfigurationSource(GatewayProperties properties) {
    CorsConfiguration cors = new CorsConfiguration();
    // With no origins configured, every browser preflight is refused. Nothing is allowed until the
    // frontend exists.
    cors.setAllowedOrigins(properties.cors().allowedOrigins());
    cors.setAllowedMethods(
        List.of(
            HttpMethod.GET.name(),
            HttpMethod.POST.name(),
            HttpMethod.PUT.name(),
            HttpMethod.PATCH.name(),
            HttpMethod.DELETE.name(),
            HttpMethod.OPTIONS.name()));
    cors.setAllowedHeaders(
        List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Correlation-Id"));
    cors.setExposedHeaders(
        List.of(
            "X-Correlation-Id",
            "X-RateLimit-Remaining",
            "X-RateLimit-Replenish-Rate",
            "X-RateLimit-Burst-Capacity"));
    cors.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cors);
    return source;
  }

  @Bean
  RedisRateLimiter redisRateLimiter(GatewayProperties properties) {
    return new RedisRateLimiter(
        properties.rateLimit().replenishRatePerSecond(), properties.rateLimit().burstCapacity());
  }
}
