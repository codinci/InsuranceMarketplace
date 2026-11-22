package com.example.insurance.claimservice.controller;

import com.example.insurance.claimservice.exception.ApiException;
import com.example.insurance.claimservice.exception.ErrorCode;
import com.example.insurance.claimservice.service.Actor;
import com.example.insurance.common.Roles;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Builds the {@link Actor} from the access token: the subject, the role and, for staff, the insurer
 * (PLAN 4). An adjuster must carry an {@code insurerId}; without one the request is refused, so a
 * missing claim can never widen an adjuster's reach to every insurer.
 */
final class Actors {

  private Actors() {}

  static Actor of(Jwt token) {
    String subject = token.getSubject();
    if (subject == null) {
      throw new IllegalArgumentException("The token has no subject.");
    }
    UUID userId = UUID.fromString(subject);
    List<String> roles = token.getClaimAsStringList("roles");
    String role = roleOf(roles);
    String insurer = token.getClaimAsString("insurerId");
    UUID insurerId = insurer == null ? null : UUID.fromString(insurer);
    if (Roles.ADJUSTER.equals(role) && insurerId == null) {
      throw new ApiException(
          ErrorCode.STAFF_SCOPE_MISSING, "This staff account is not tied to an insurer.");
    }
    return new Actor(userId, role, insurerId);
  }

  private static String roleOf(List<String> roles) {
    if (roles != null) {
      for (String role : List.of(Roles.ADMIN, Roles.ADJUSTER, Roles.CUSTOMER)) {
        if (roles.contains(role)) {
          return role;
        }
      }
    }
    return Roles.CUSTOMER;
  }
}
