package com.example.insurance.policyservice.catalogue.service;

import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Which insurers a staff member may act on (PLAN 4). A platform admin has no {@code insurerId} and
 * may act on every insurer; an insurer admin only on their own. Built from the token, never from a
 * request body (CLAUDE.md).
 */
public record StaffScope(UUID insurerId) {

  private static final StaffScope PLATFORM = new StaffScope(null);

  public static StaffScope platform() {
    return PLATFORM;
  }

  public static StaffScope of(Jwt token) {
    String claim = token.getClaimAsString("insurerId");
    return claim == null ? PLATFORM : new StaffScope(UUID.fromString(claim));
  }

  public boolean isPlatform() {
    return insurerId == null;
  }

  public boolean canSee(UUID insurer) {
    return isPlatform() || insurerId.equals(insurer);
  }
}
