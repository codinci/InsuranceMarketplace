package com.example.insurance.auth.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed settings under {@code auth.*} (see application.properties). A record is bound through its
 * constructor, so every value is final and nothing can change after startup.
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(Token token, Jwt jwt, Login login, PlatformAdminSeed seed) {

  public AuthProperties {
    // Spring binds a group whose values are all empty as null. "No seed" is the same as an empty
    // seed.
    seed = seed == null ? new PlatformAdminSeed(null, null) : seed;
  }

  /** Lifetimes of access and refresh tokens (PLAN 12). */
  public record Token(Duration accessTtl, Duration refreshTtl) {}

  /**
   * Signing settings. {@code privateKeyBase64} is the PKCS#8 DER private key in base64, so it fits
   * on one line of {@code .env}. {@code additionalPublicKeys} are published in the JWKS for
   * verification only, during a key rotation.
   */
  public record Jwt(
      String issuer,
      String keyId,
      String privateKeyBase64,
      boolean generateEphemeralKey,
      List<PublicKeyEntry> additionalPublicKeys) {

    public Jwt {
      additionalPublicKeys =
          additionalPublicKeys == null ? List.of() : List.copyOf(additionalPublicKeys);
    }
  }

  /** A verification-only public key: an X.509 DER public key in base64, with its key id. */
  public record PublicKeyEntry(String keyId, String publicKeyBase64) {}

  /** Lockout and rate limits for {@code POST /api/v1/auth/login} (PLAN 12). */
  public record Login(
      int maxFailedAttempts,
      Duration lockDuration,
      int attemptsPerMinutePerIp,
      int attemptsPerMinutePerEmail) {}

  /** First platform admin. Created at startup when the email is set and no such user exists. */
  public record PlatformAdminSeed(String platformAdminEmail, String platformAdminPassword) {}
}
