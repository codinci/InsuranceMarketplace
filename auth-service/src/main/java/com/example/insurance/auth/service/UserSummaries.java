package com.example.insurance.auth.service;

import com.example.insurance.auth.domain.UserAccount;
import com.example.insurance.auth.dto.UserSummary;
import java.util.Locale;

/**
 * The one place that turns a user entity into the API shape, so no entity leaves the service layer.
 */
final class UserSummaries {

  /**
   * Bcrypt only uses the first 72 bytes and Spring rejects longer input, so we check the byte
   * length.
   */
  static final int MAX_PASSWORD_BYTES = 72;

  private UserSummaries() {
    // Static helpers only.
  }

  static UserSummary of(UserAccount user) {
    return new UserSummary(
        user.getId(),
        user.getEmail(),
        user.getFirstName(),
        user.getLastName(),
        user.getRoles(),
        user.getInsurerId());
  }

  static String normaliseEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
