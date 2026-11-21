package com.example.insurance.paymentservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where to check tokens: the issuer every token must carry and the JWKS of auth-service (ADR-006).
 * Bound from {@code payment.jwt.*}.
 */
@ConfigurationProperties(prefix = "payment.jwt")
public record JwtProperties(String issuer, String jwkSetUri) {}
