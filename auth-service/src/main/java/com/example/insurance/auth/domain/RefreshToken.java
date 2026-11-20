package com.example.insurance.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One refresh token. Only its SHA-256 hash is stored (PLAN 12). Every token from one login shares a
 * {@code familyId}, so reuse of a rotated token can revoke the whole family.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "family_id", nullable = false)
  private UUID familyId;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected RefreshToken() {
    // Required by JPA.
  }

  public static RefreshToken issue(
      UUID userId, UUID familyId, String tokenHash, Instant expiresAt, Instant now) {
    RefreshToken token = new RefreshToken();
    token.id = UUID.randomUUID();
    token.userId = userId;
    token.familyId = familyId;
    token.tokenHash = tokenHash;
    token.expiresAt = expiresAt;
    token.createdAt = now;
    return token;
  }

  public void revoke(Instant now) {
    if (revokedAt == null) {
      revokedAt = now;
    }
  }

  public boolean isRevoked() {
    return revokedAt != null;
  }

  public boolean isExpiredAt(Instant now) {
    return !expiresAt.isAfter(now);
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getFamilyId() {
    return familyId;
  }

  public String getTokenHash() {
    return tokenHash;
  }
}
