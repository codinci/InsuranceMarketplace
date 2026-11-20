package com.example.insurance.auth.support;

import com.example.insurance.auth.config.AuthProperties;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Shared unit-test fixtures: a fixed clock, one generated RSA key and production-like properties.
 */
public final class TestSupport {

  public static final String ISSUER = "test-auth";
  public static final String KEY_ID = "test-key-1";
  // Tests use this as the fixed "now". It must be the real current time, because the JWT decoder
  // checks
  // expiry against the real clock.
  public static final Instant NOW =
      Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

  private static final RSAKey KEY = generateKey();

  private TestSupport() {
    // Static helpers only.
  }

  public static Clock fixedClock() {
    return Clock.fixed(NOW, ZoneOffset.UTC);
  }

  public static AuthProperties properties() {
    return new AuthProperties(
        new AuthProperties.Token(Duration.ofMinutes(15), Duration.ofDays(7)),
        new AuthProperties.Jwt(ISSUER, KEY_ID, null, true, null),
        new AuthProperties.Login(5, Duration.ofMinutes(15), 20, 10),
        new AuthProperties.PlatformAdminSeed(null, null));
  }

  public static JwtEncoder encoder() {
    return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(KEY)));
  }

  public static JwtDecoder decoder() throws Exception {
    return NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).build();
  }

  public static RSAKey signingKey() {
    return KEY;
  }

  private static RSAKey generateKey() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      KeyPair pair = generator.generateKeyPair();
      return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
          .privateKey(pair.getPrivate())
          .keyID(KEY_ID)
          .build();
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
