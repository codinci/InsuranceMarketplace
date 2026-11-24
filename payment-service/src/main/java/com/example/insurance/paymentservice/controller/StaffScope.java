package com.example.insurance.paymentservice.controller;

import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Reads the insurer a staff token is tied to (PLAN 4). The scope comes only from the token, never
 * from the request. An insurer admin has an {@code insurerId}; a platform admin has none and sees
 * every insurer, so the result is null for a platform admin.
 */
final class StaffScope {

  private StaffScope() {}

  static UUID insurerId(Jwt token) {
    String insurer = token.getClaimAsString("insurerId");
    return insurer == null ? null : UUID.fromString(insurer);
  }
}
