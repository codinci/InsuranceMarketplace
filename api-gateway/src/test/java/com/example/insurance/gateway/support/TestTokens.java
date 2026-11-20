package com.example.insurance.gateway.support;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Signs access tokens with a generated key, as auth-service would, and publishes its public half as
 * a JWKS.
 */
public final class TestTokens {

  public static final String ISSUER = "mini-insurance-auth";
  private static final String KEY_ID = "gateway-test-key";
  private static final RSAKey KEY = generate();
  // A second key the gateway has never been configured with, for the wrong-signature case.
  private static final RSAKey OTHER_KEY = generate();

  private TestTokens() {
    // Static helpers only.
  }

  public static String jwks() {
    return new JWKSet(KEY.toPublicJWK()).toString();
  }

  public static String valid() {
    return sign(KEY, ISSUER, Instant.now().plusSeconds(900));
  }

  public static String expired() {
    return sign(KEY, ISSUER, Instant.now().minusSeconds(60));
  }

  public static String wrongIssuer() {
    return sign(KEY, "someone-else", Instant.now().plusSeconds(900));
  }

  public static String wrongSignature() {
    return sign(OTHER_KEY, ISSUER, Instant.now().plusSeconds(900));
  }

  private static String sign(RSAKey key, String issuer, Instant expiresAt) {
    NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .subject("00000000-0000-0000-0000-000000000001")
            .issuedAt(expiresAt.minusSeconds(900))
            .expiresAt(expiresAt)
            .claim("roles", List.of("CUSTOMER"))
            .build();
    JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY_ID).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
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
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }
}
