package com.example.insurance.auth.service;

import com.example.insurance.auth.domain.UserAccount;
import com.example.insurance.auth.dto.CreateStaffRequest;
import com.example.insurance.auth.dto.UserSummary;
import com.example.insurance.auth.exception.ApiException;
import com.example.insurance.auth.exception.ErrorCode;
import com.example.insurance.auth.repository.UserAccountRepository;
import com.example.insurance.common.Roles;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates ADJUSTER and ADMIN accounts (PLAN 4). The insurer rules depend on the caller's token: an
 * insurer admin may only create staff for their own {@code insurerId}; only a platform admin (no
 * insurerId) may create another platform admin or name any insurer.
 */
@Service
public class StaffService {

  private final UserAccountRepository users;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  public StaffService(UserAccountRepository users, PasswordEncoder passwordEncoder, Clock clock) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
  }

  @Transactional
  public UserSummary create(Jwt actor, CreateStaffRequest request) {
    List<String> actorRoles =
        Optional.ofNullable(actor.getClaimAsStringList(TokenService.CLAIM_ROLES)).orElse(List.of());
    if (!actorRoles.contains(Roles.ADMIN)) {
      throw new ApiException(ErrorCode.FORBIDDEN, "You do not have permission for this action.");
    }
    UUID actorInsurer = insurerOf(actor);
    boolean platformAdmin = actorInsurer == null;
    UUID target = request.insurerId();

    if (Roles.ADJUSTER.equals(request.role()) && target == null) {
      throw new ApiException(ErrorCode.INSURER_REQUIRED, "An adjuster must belong to an insurer.");
    }
    if (target == null && !platformAdmin) {
      throw new ApiException(
          ErrorCode.INSURER_SCOPE_VIOLATION, "Only a platform admin can create a platform admin.");
    }
    if (!platformAdmin && !target.equals(actorInsurer)) {
      throw new ApiException(
          ErrorCode.INSURER_SCOPE_VIOLATION, "You can only create staff for your own insurer.");
    }

    String email = UserSummaries.normaliseEmail(request.email());
    if (users.existsByEmail(email)) {
      throw new ApiException(ErrorCode.EMAIL_TAKEN, "An account with this email already exists.");
    }
    if (request.password().getBytes(StandardCharsets.UTF_8).length
        > UserSummaries.MAX_PASSWORD_BYTES) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "The password is too long.");
    }
    UserAccount user =
        UserAccount.create(
            email,
            passwordEncoder.encode(request.password()),
            request.firstName().trim(),
            request.lastName().trim(),
            target,
            Set.of(request.role()),
            clock.instant());
    try {
      users.saveAndFlush(user);
    } catch (DataIntegrityViolationException ex) {
      throw new ApiException(ErrorCode.EMAIL_TAKEN, "An account with this email already exists.");
    }
    return UserSummaries.of(user);
  }

  private static UUID insurerOf(Jwt actor) {
    String value = actor.getClaimAsString(TokenService.CLAIM_INSURER_ID);
    return value == null ? null : UUID.fromString(value);
  }
}
