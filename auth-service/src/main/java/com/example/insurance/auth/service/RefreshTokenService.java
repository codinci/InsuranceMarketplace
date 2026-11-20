package com.example.insurance.auth.service;

import com.example.insurance.auth.config.AuthProperties;
import com.example.insurance.auth.domain.RefreshToken;
import com.example.insurance.auth.exception.ApiException;
import com.example.insurance.auth.exception.ErrorCode;
import com.example.insurance.auth.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh tokens are random, opaque and stored only as SHA-256 hashes (PLAN 12). Each use rotates
 * the token. Presenting a token that was already rotated means it was copied, so the whole family
 * is revoked.
 */
@Service
public class RefreshTokenService {

  private static final int TOKEN_BYTES = 32;

  private final RefreshTokenRepository repository;
  private final AuthProperties properties;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public RefreshTokenService(
      RefreshTokenRepository repository, AuthProperties properties, Clock clock) {
    this.repository = repository;
    this.properties = properties;
    this.clock = clock;
  }

  /** The result of a rotation: the owner and the replacement token. */
  public record Rotation(UUID userId, String newToken) {}

  /** Starts a new family for a fresh login. Returns the raw token, which is shown once. */
  @Transactional
  public String issueFirst(UUID userId) {
    return issue(userId, UUID.randomUUID());
  }

  /**
   * Replaces a valid token with a new one in the same family. Declared without rollback for {@link
   * ApiException}, so the revocation of a reused family is kept when the request then fails.
   */
  @Transactional(noRollbackFor = ApiException.class)
  public Rotation rotate(String rawToken) {
    Instant now = clock.instant();
    RefreshToken stored =
        repository
            .findByTokenHash(hash(rawToken))
            .orElseThrow(() ->
                new ApiException(ErrorCode.INVALID_REFRESH_TOKEN, "Refresh token is not valid."));
    if (stored.isRevoked()) {
      revokeFamily(stored.getFamilyId(), now);
      throw new ApiException(
          ErrorCode.REFRESH_TOKEN_REUSED, "Refresh token was already used. Sign in again.");
    }
    if (stored.isExpiredAt(now)) {
      throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN, "Refresh token has expired.");
    }
    stored.revoke(now);
    String replacement = issue(stored.getUserId(), stored.getFamilyId());
    return new Rotation(stored.getUserId(), replacement);
  }

  /**
   * Revokes the family of the presented token. Unknown tokens are ignored, so logout is idempotent.
   */
  @Transactional
  public void revokeFamilyOf(String rawToken) {
    repository
        .findByTokenHash(hash(rawToken))
        .ifPresent(stored -> revokeFamily(stored.getFamilyId(), clock.instant()));
  }

  private void revokeFamily(UUID familyId, Instant now) {
    repository.findByFamilyId(familyId).forEach(token -> token.revoke(now));
  }

  private String issue(UUID userId, UUID familyId) {
    byte[] bytes = new byte[TOKEN_BYTES];
    random.nextBytes(bytes);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    Instant now = clock.instant();
    repository.save(
        RefreshToken.issue(
            userId, familyId, hash(raw), now.plus(properties.token().refreshTtl()), now));
    return raw;
  }

  /** SHA-256 of the raw token, as lowercase hex. A database leak does not reveal usable tokens. */
  static String hash(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is required by every Java runtime", ex);
    }
  }
}
