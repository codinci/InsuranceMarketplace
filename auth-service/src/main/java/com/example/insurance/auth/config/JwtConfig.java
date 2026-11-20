package com.example.insurance.auth.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * RS256 keys for access tokens (PLAN 12). Services verify tokens against the public half, published
 * at {@code /.well-known/jwks.json}. The private key never leaves auth-service.
 */
@Configuration
public class JwtConfig {

  private static final int EPHEMERAL_KEY_BITS = 2048;

  @Bean
  RSAKey signingKey(AuthProperties properties) throws Exception {
    AuthProperties.Jwt jwt = properties.jwt();
    KeyPair pair = loadOrGenerate(jwt);
    return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
        .privateKey(pair.getPrivate())
        .keyID(jwt.keyId())
        .algorithm(JWSAlgorithm.RS256)
        .build();
  }

  @Bean
  JWKSource<SecurityContext> jwkSource(RSAKey signingKey) {
    return new ImmutableJWKSet<>(new JWKSet(signingKey));
  }

  @Bean
  JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
    return new NimbusJwtEncoder(jwkSource);
  }

  @Bean
  JwtDecoder jwtDecoder(RSAKey signingKey, AuthProperties properties) throws Exception {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey()).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()));
    return decoder;
  }

  /**
   * The public keys for {@code /.well-known/jwks.json}: the current key plus any rotation overlap.
   */
  @Bean
  JWKSet publicJwkSet(RSAKey signingKey, AuthProperties properties) throws Exception {
    List<RSAKey> keys = new ArrayList<>();
    keys.add(signingKey.toPublicJWK());
    for (AuthProperties.PublicKeyEntry entry : properties.jwt().additionalPublicKeys()) {
      RSAPublicKey key = publicKey(Base64.getDecoder().decode(entry.publicKeyBase64()));
      keys.add(new RSAKey.Builder(key).keyID(entry.keyId()).algorithm(JWSAlgorithm.RS256).build());
    }
    return new JWKSet(List.copyOf(keys));
  }

  private static KeyPair loadOrGenerate(AuthProperties.Jwt jwt) throws Exception {
    if (jwt.privateKeyBase64() != null && !jwt.privateKeyBase64().isBlank()) {
      RSAPrivateCrtKey privateKey = privateKey(Base64.getDecoder().decode(jwt.privateKeyBase64()));
      // The public key is derived from the private key, so the two can never disagree.
      RSAPublicKey publicKey =
          (RSAPublicKey)
              KeyFactory.getInstance("RSA")
                  .generatePublic(
                      new RSAPublicKeySpec(
                          privateKey.getModulus(), privateKey.getPublicExponent()));
      return new KeyPair(publicKey, privateKey);
    }
    if (jwt.generateEphemeralKey()) {
      // Local development only: tokens stop validating after each restart.
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(EPHEMERAL_KEY_BITS);
      return generator.generateKeyPair();
    }
    throw new IllegalStateException(
        "auth.jwt.private-key-base64 is not set. Set it, or set auth.jwt.generate-ephemeral-key=true for local development only.");
  }

  private static RSAPrivateCrtKey privateKey(byte[] pkcs8Der) throws Exception {
    return (RSAPrivateCrtKey)
        KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs8Der));
  }

  private static RSAPublicKey publicKey(byte[] x509Der) throws Exception {
    return (RSAPublicKey)
        KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(x509Der));
  }
}
