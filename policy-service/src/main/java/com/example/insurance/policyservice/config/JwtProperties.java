package com.example.insurance.policyservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Where access tokens come from (PLAN 12, ADR-006). */
@ConfigurationProperties(prefix = "policy.jwt")
public record JwtProperties(String issuer, String jwkSetUri) {}
