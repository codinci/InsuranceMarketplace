package com.example.insurance.auth.service;

import com.example.insurance.auth.config.AuthProperties;
import com.example.insurance.auth.domain.UserAccount;
import com.example.insurance.auth.repository.UserAccountRepository;
import com.example.insurance.common.Roles;
import java.time.Clock;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first platform admin from environment variables when no user has that email (PLAN 4).
 * It runs on every start and does nothing once the account exists, so the password is never changed
 * by a restart.
 */
@Component
public class PlatformAdminSeeder implements ApplicationRunner {

  private static final int MIN_PASSWORD_LENGTH = 12;

  private final AuthProperties.PlatformAdminSeed seed;
  private final UserAccountRepository users;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  public PlatformAdminSeeder(
      AuthProperties properties,
      UserAccountRepository users,
      PasswordEncoder passwordEncoder,
      Clock clock) {
    this.seed = properties.seed();
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (seed.platformAdminEmail() == null || seed.platformAdminEmail().isBlank()) {
      return;
    }
    String email = UserSummaries.normaliseEmail(seed.platformAdminEmail());
    if (users.existsByEmail(email)) {
      return;
    }
    if (seed.platformAdminPassword() == null
        || seed.platformAdminPassword().length() < MIN_PASSWORD_LENGTH) {
      throw new IllegalStateException(
          "auth.seed.platform-admin-password must be at least "
              + MIN_PASSWORD_LENGTH
              + " characters when auth.seed.platform-admin-email is set.");
    }
    users.save(
        UserAccount.create(
            email,
            passwordEncoder.encode(seed.platformAdminPassword()),
            "Platform",
            "Admin",
            null,
            Set.of(Roles.ADMIN),
            clock.instant()));
  }
}
