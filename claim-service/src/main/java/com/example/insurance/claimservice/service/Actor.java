package com.example.insurance.claimservice.service;

import com.example.insurance.common.Roles;
import java.util.UUID;

/**
 * Who is acting, built from the token and never from a request body (CLAUDE.md). A customer owns
 * claims; staff act on claims of their insurer. A platform admin has no insurer and may act on
 * every insurer. {@code insurerId} is null for customers and for platform admins.
 */
public record Actor(UUID userId, String role, UUID insurerId) {

  public boolean isStaff() {
    return Roles.ADJUSTER.equals(role) || Roles.ADMIN.equals(role);
  }

  public boolean isCustomer() {
    return Roles.CUSTOMER.equals(role);
  }

  /** True for a platform admin: staff with no insurer, who see every insurer. */
  public boolean isPlatform() {
    return Roles.ADMIN.equals(role) && insurerId == null;
  }

  /**
   * True when staff may act on a claim of {@code insurer}: their own, or any for a platform admin.
   */
  public boolean canSee(UUID insurer) {
    return isPlatform() || (insurerId != null && insurerId.equals(insurer));
  }
}
