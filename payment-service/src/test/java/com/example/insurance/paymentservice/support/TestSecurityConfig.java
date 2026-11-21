package com.example.insurance.paymentservice.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Replaces the JWKS-backed decoder with one that checks the test key. The rules are unchanged:
 * issuer and expiry are still checked (ADR-006).
 */
@TestConfiguration
public class TestSecurityConfig {

  @Bean
  @Primary
  JwtDecoder testJwtDecoder() {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(TestTokens.publicKey()).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(TestTokens.ISSUER));
    return decoder;
  }
}
