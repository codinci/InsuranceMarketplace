package com.example.insurance.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.RSAKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class JwtConfigTest {

  private final JwtConfig config = new JwtConfig();

  @Test
  void aMissingKeyFailsStartupUnlessEphemeralKeysAreExplicitlyAllowed() throws Exception {
    AuthProperties properties = properties(new AuthProperties.Jwt("iss", "k1", "", false, null));

    assertThatThrownBy(() -> config.signingKey(properties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("auth.jwt.private-key-base64");
  }

  @Test
  void theConfiguredPrivateKeyIsUsedAndItsPublicHalfIsDerivedFromIt() throws Exception {
    KeyPair pair = generate();
    String privateBase64 = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
    AuthProperties properties =
        properties(new AuthProperties.Jwt("iss", "prod-1", privateBase64, false, null));

    RSAKey key = config.signingKey(properties);

    assertThat(key.getKeyID()).isEqualTo("prod-1");
    assertThat(key.toRSAPublicKey().getModulus())
        .isEqualTo(((RSAPublicKey) pair.getPublic()).getModulus());
    assertThat(key.toRSAPublicKey().getPublicExponent())
        .isEqualTo(((RSAPublicKey) pair.getPublic()).getPublicExponent());
  }

  @Test
  void ephemeralKeysAreAcceptedForLocalDevelopment() throws Exception {
    RSAKey key =
        config.signingKey(properties(new AuthProperties.Jwt("iss", "dev-1", null, true, null)));

    assertThat(key.isPrivate()).isTrue();
  }

  @Test
  void rotationKeysAreAddedToThePublishedSetForVerificationOnly() throws Exception {
    RSAKey current =
        config.signingKey(properties(new AuthProperties.Jwt("iss", "new", null, true, null)));
    KeyPair previous = generate();
    String previousPublic = Base64.getEncoder().encodeToString(previous.getPublic().getEncoded());
    AuthProperties properties =
        properties(
            new AuthProperties.Jwt(
                "iss",
                "new",
                null,
                true,
                List.of(new AuthProperties.PublicKeyEntry("old", previousPublic))));

    var published = config.publicJwkSet(current, properties);

    assertThat(published.getKeys()).hasSize(2);
    assertThat(published.getKeyByKeyId("old").isPrivate()).isFalse();
    assertThat(published.getKeyByKeyId("new").isPrivate()).isFalse();
  }

  private static AuthProperties properties(AuthProperties.Jwt jwt) {
    return new AuthProperties(
        new AuthProperties.Token(Duration.ofMinutes(15), Duration.ofDays(7)),
        jwt,
        new AuthProperties.Login(5, Duration.ofMinutes(15), 20, 10),
        new AuthProperties.PlatformAdminSeed(null, null));
  }

  private static KeyPair generate() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }
}
