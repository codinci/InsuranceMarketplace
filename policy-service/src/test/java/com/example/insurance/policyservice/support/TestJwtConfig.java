package com.example.insurance.policyservice.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** Checks test tokens with TestTokens' public key instead of fetching auth-service's JWKS. */
@TestConfiguration
public class TestJwtConfig {

  @Bean
  @Primary
  JwtDecoder testJwtDecoder() {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(TestTokens.publicKey()).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(TestTokens.ISSUER));
    return decoder;
  }
}
