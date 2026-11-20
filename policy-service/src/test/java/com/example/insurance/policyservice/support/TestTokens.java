package com.example.insurance.policyservice.support;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Signs access tokens the way auth-service does (ADR-006), with a key generated per test run. */
public final class TestTokens {

  public static final String ISSUER = "mini-insurance-auth";
  private static final String KEY_ID = "policy-test-key";
  private static final RSAKey KEY = generate();

  private TestTokens() {}

  public static RSAPublicKey publicKey() {
    try {
      return KEY.toRSAPublicKey();
    } catch (com.nimbusds.jose.JOSEException e) {
      throw new IllegalStateException(e);
    }
  }

  public static String platformAdmin() {
    return sign(List.of("ADMIN"), null);
  }

  public static String insurerAdmin(UUID insurerId) {
    return sign(List.of("ADMIN"), insurerId);
  }

  public static String adjuster(UUID insurerId) {
    return sign(List.of("ADJUSTER"), insurerId);
  }

  public static String customer() {
    return sign(List.of("CUSTOMER"), null);
  }

  private static String sign(List<String> roles, UUID insurerId) {
    Instant now = Instant.now();
    JwtClaimsSet.Builder claims =
        JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(UUID.randomUUID().toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(900))
            .claim("roles", roles);
    if (insurerId != null) {
      claims.claim("insurerId", insurerId.toString());
    }
    NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(KEY)));
    JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY_ID).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
  }

  private static RSAKey generate() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      KeyPair pair = generator.generateKeyPair();
      return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
          .privateKey(pair.getPrivate())
          .keyID(KEY_ID)
          .build();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
