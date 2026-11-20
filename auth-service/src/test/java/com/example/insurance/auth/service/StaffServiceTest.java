package com.example.insurance.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.insurance.auth.dto.CreateStaffRequest;
import com.example.insurance.auth.exception.ApiException;
import com.example.insurance.auth.exception.ErrorCode;
import com.example.insurance.auth.repository.UserAccountRepository;
import com.example.insurance.auth.support.TestSupport;
import com.example.insurance.common.Roles;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;

/** The insurer-scope rules from PLAN 4, checked without a database. */
class StaffServiceTest {

  private static final UUID INSURER_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final UUID INSURER_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

  private final UserAccountRepository users = mock(UserAccountRepository.class);
  private final PasswordEncoder encoder = mock(PasswordEncoder.class);
  private final StaffService service = new StaffService(users, encoder, TestSupport.fixedClock());

  @Test
  void platformAdminCanCreateAnAdjusterForAnyInsurer() {
    assertThat(
            service
                .create(actor(List.of(Roles.ADMIN), null), request("ADJUSTER", INSURER_B))
                .insurerId())
        .isEqualTo(INSURER_B);
  }

  @Test
  void insurerAdminCanCreateStaffForTheirOwnInsurer() {
    assertThat(
            service
                .create(actor(List.of(Roles.ADMIN), INSURER_A), request("ADJUSTER", INSURER_A))
                .insurerId())
        .isEqualTo(INSURER_A);
  }

  @Test
  void insurerAdminCannotCreateStaffForAnotherInsurer() {
    assertCode(
        () ->
            service.create(actor(List.of(Roles.ADMIN), INSURER_A), request("ADJUSTER", INSURER_B)),
        ErrorCode.INSURER_SCOPE_VIOLATION);
    verify(users, never()).saveAndFlush(any());
  }

  @Test
  void insurerAdminCannotCreatePlatformAdmin() {
    assertCode(
        () -> service.create(actor(List.of(Roles.ADMIN), INSURER_A), request("ADMIN", null)),
        ErrorCode.INSURER_SCOPE_VIOLATION);
  }

  @Test
  void anAdjusterNeedsAnInsurer() {
    assertCode(
        () -> service.create(actor(List.of(Roles.ADMIN), null), request("ADJUSTER", null)),
        ErrorCode.INSURER_REQUIRED);
  }

  @Test
  void someoneWithoutTheAdminRoleIsForbidden() {
    assertCode(
        () ->
            service.create(
                actor(List.of(Roles.ADJUSTER), INSURER_A), request("ADJUSTER", INSURER_A)),
        ErrorCode.FORBIDDEN);
  }

  @Test
  void aDuplicateEmailIsReported() {
    when(users.existsByEmail("staff@example.com")).thenReturn(true);

    assertCode(
        () -> service.create(actor(List.of(Roles.ADMIN), null), request("ADJUSTER", INSURER_A)),
        ErrorCode.EMAIL_TAKEN);
  }

  private static void assertCode(Runnable call, ErrorCode expected) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            ApiException.class, ex -> assertThat(ex.code()).isEqualTo(expected));
  }

  private static CreateStaffRequest request(String role, UUID insurer) {
    return new CreateStaffRequest(
        "staff@example.com", "a-long-enough-password", "Wanjiru", "Mwangi", role, insurer);
  }

  private static Jwt actor(List<String> roles, UUID insurer) {
    Instant now = TestSupport.NOW;
    Jwt.Builder builder =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject(UUID.randomUUID().toString())
            .claim(TokenService.CLAIM_ROLES, roles)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(900));
    if (insurer != null) {
      builder.claim(TokenService.CLAIM_INSURER_ID, insurer.toString());
    }
    return builder.build();
  }
}
