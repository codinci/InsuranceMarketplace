package com.example.insurance.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.insurance.auth.support.TestSupport;
import com.example.insurance.common.Roles;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserAccountTest {

  private static final Instant LOCK_UNTIL = TestSupport.NOW.plusSeconds(900);

  @Test
  void theFifthFailedLoginLocksTheAccountAndResetsTheCounter() {
    UserAccount user = customer();

    for (int attempt = 1; attempt <= 4; attempt++) {
      assertThat(user.recordFailedLogin(5, LOCK_UNTIL)).isFalse();
    }
    assertThat(user.recordFailedLogin(5, LOCK_UNTIL)).isTrue();

    assertThat(user.getFailedLoginCount()).isZero();
    assertThat(user.isLockedAt(TestSupport.NOW)).isTrue();
    assertThat(user.isLockedAt(LOCK_UNTIL)).isFalse();
  }

  @Test
  void aSuccessfulLoginClearsFailuresAndTheLock() {
    UserAccount user = customer();
    user.recordFailedLogin(1, LOCK_UNTIL);

    user.recordSuccessfulLogin();

    assertThat(user.isLockedAt(TestSupport.NOW)).isFalse();
    assertThat(user.getLockedUntil()).isNull();
  }

  @Test
  void theRoleSetCannotBeChangedFromOutside() {
    UserAccount user = customer();

    assertThatThrownBy(() -> user.getRoles().add(Roles.ADMIN))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(user.getRoles()).containsExactly(Roles.CUSTOMER);
  }

  private static UserAccount customer() {
    return UserAccount.create(
        "c@example.com", "hash", "Amina", "Otieno", null, Set.of(Roles.CUSTOMER), TestSupport.NOW);
  }
}
