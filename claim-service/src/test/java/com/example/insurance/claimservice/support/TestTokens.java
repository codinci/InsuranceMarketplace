package com.example.insurance.claimservice.support;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Signs test tokens with a key generated for the test run. Tokens look like auth-service's: RS256,
 * {@code sub}, {@code roles} and {@code iss} (ADR-006, ADR-014).
 */
public final class TestTokens {

  public static final String ISSUER = "mini-insurance-auth";
  private static final String KEY_ID = "test-key-1";

  private static final RSAPublicKey PUBLIC_KEY;
  private static final JwtEncoder ENCODER;

  static {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      KeyPair pair = generator.generateKeyPair();
      PUBLIC_KEY = (RSAPublicKey) pair.getPublic();
      RSAKey key =
          new RSAKey.Builder(PUBLIC_KEY).privateKey(pair.getPrivate()).keyID(KEY_ID).build();
      ENCODER = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    } catch (Exception e) {
      throw new IllegalStateException("Could not create the test signing key.", e);
    }
  }

  private TestTokens() {}

  public static RSAPublicKey publicKey() {
    return PUBLIC_KEY;
  }

  /** A customer with a known user id, for ownership tests. */
  public static String customer(UUID userId) {
    return mint(userId.toString(), List.of("CUSTOMER"));
  }

  /** A SERVICE token as auth-service issues it: the subject is the calling service's name. */
  public static String service(String serviceName) {
    return mint(serviceName, List.of("SERVICE"));
  }

  private static String mint(String subject, List<String> roles) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(subject)
            .issuedAt(now)
            .expiresAt(now.plus(15, ChronoUnit.MINUTES))
            .id(UUID.randomUUID().toString())
            .claim("roles", roles)
            .build();
    JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY_ID).build();
    return ENCODER.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }
}
