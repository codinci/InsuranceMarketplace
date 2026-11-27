package com.example.insurance.auth.service;

import com.example.insurance.auth.config.AuthProperties;
import com.example.insurance.auth.domain.UserAccount;
import com.example.insurance.auth.dto.LoginRequest;
import com.example.insurance.auth.dto.RefreshRequest;
import com.example.insurance.auth.dto.RegisterRequest;
import com.example.insurance.auth.dto.TokenResponse;
import com.example.insurance.auth.dto.UserSummary;
import com.example.insurance.auth.exception.ApiException;
import com.example.insurance.auth.exception.ErrorCode;
import com.example.insurance.auth.messaging.Topics;
import com.example.insurance.auth.repository.UserAccountRepository;
import com.example.insurance.common.Roles;
import com.example.insurance.events.EventTypes;
import com.example.insurance.events.user.UserRegistered;
import com.example.insurance.messaging.OutboxWriter;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration, login, refresh, logout and "who am I" (PLAN 13, M1). Methods that record failures
 * (login, refresh) are declared without rollback for {@link ApiException}, so a failed-login
 * counter or a family revocation is kept even though the request fails.
 */
@Service
public class AuthService {

  private final UserAccountRepository users;
  private final PasswordEncoder passwordEncoder;
  private final TokenService tokens;
  private final RefreshTokenService refreshTokens;
  private final LoginRateLimiter loginRateLimiter;
  private final AuthProperties properties;
  private final Clock clock;
  private final OutboxWriter outbox;
  private final String timingEqualiserHash;

  public AuthService(
      UserAccountRepository users,
      PasswordEncoder passwordEncoder,
      TokenService tokens,
      RefreshTokenService refreshTokens,
      LoginRateLimiter loginRateLimiter,
      AuthProperties properties,
      Clock clock,
      OutboxWriter outbox) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.tokens = tokens;
    this.refreshTokens = refreshTokens;
    this.loginRateLimiter = loginRateLimiter;
    this.properties = properties;
    this.clock = clock;
    this.outbox = outbox;
    // Checking a password against this hash when the email is unknown makes both answers take the
    // same time.
    this.timingEqualiserHash = passwordEncoder.encode("not-a-real-password-for-timing");
  }

  /**
   * Always creates a CUSTOMER. Role and insurer come from the server, never from the request (PLAN
   * 4).
   */
  @Transactional
  public UserSummary register(RegisterRequest request) {
    checkPasswordLength(request.password());
    String email = UserSummaries.normaliseEmail(request.email());
    if (users.existsByEmail(email)) {
      throw emailTaken();
    }
    UserAccount user =
        UserAccount.create(
            email,
            passwordEncoder.encode(request.password()),
            request.firstName().trim(),
            request.lastName().trim(),
            null,
            Set.of(Roles.CUSTOMER),
            clock.instant());
    try {
      users.saveAndFlush(user);
    } catch (DataIntegrityViolationException ex) {
      // Two registrations for the same email raced past the check above; the unique index wins.
      throw emailTaken();
    }
    // Same transaction as the user row: a registered customer is always announced, and a failed
    // registration announces nothing (ADR-013).
    outbox.record(
        Topics.USER_EVENTS,
        EventTypes.USER_REGISTERED,
        UserRegistered.VERSION,
        user.getId().toString(),
        null,
        new UserRegistered(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName()));
    return UserSummaries.of(user);
  }

  @Transactional(noRollbackFor = ApiException.class)
  public TokenResponse login(LoginRequest request, String clientIp) {
    String email = UserSummaries.normaliseEmail(request.email());
    if (!loginRateLimiter.allow(clientIp, email)) {
      throw new ApiException(
          ErrorCode.RATE_LIMITED, "Too many login attempts. Wait a minute and try again.");
    }
    Instant now = clock.instant();
    Optional<UserAccount> found = users.findByEmail(email);
    if (found.isEmpty() || tooLongForBcrypt(request.password())) {
      passwordEncoder.matches(request.password(), timingEqualiserHash);
      throw invalidCredentials();
    }
    UserAccount user = found.get();
    if (user.isLockedAt(now)) {
      throw new ApiException(ErrorCode.ACCOUNT_LOCKED, "The account is temporarily locked.");
    }
    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      user.recordFailedLogin(
          properties.login().maxFailedAttempts(), now.plus(properties.login().lockDuration()));
      throw invalidCredentials();
    }
    user.recordSuccessfulLogin();
    return tokenResponse(user, refreshTokens.issueFirst(user.getId()));
  }

  @Transactional(noRollbackFor = ApiException.class)
  public TokenResponse refresh(RefreshRequest request) {
    RefreshTokenService.Rotation rotation = refreshTokens.rotate(request.refreshToken());
    UserAccount user =
        users
            .findById(rotation.userId())
            .orElseThrow(() ->
                new ApiException(ErrorCode.INVALID_REFRESH_TOKEN, "Refresh token is not valid."));
    return tokenResponse(user, rotation.newToken());
  }

  /** Idempotent: an unknown or already revoked token still returns success. */
  @Transactional
  public void logout(RefreshRequest request) {
    refreshTokens.revokeFamilyOf(request.refreshToken());
  }

  @Transactional(readOnly = true)
  public UserSummary me(UUID userId) {
    return users
        .findById(userId)
        .map(UserSummaries::of)
        .orElseThrow(
            () -> new ApiException(ErrorCode.UNAUTHENTICATED, "Authentication is required."));
  }

  private TokenResponse tokenResponse(UserAccount user, String refreshToken) {
    TokenService.AccessToken access = tokens.issue(user);
    return new TokenResponse(access.value(), "Bearer", access.expiresInSeconds(), refreshToken);
  }

  private static void checkPasswordLength(String password) {
    if (tooLongForBcrypt(password)) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED,
          "The password is longer than " + UserSummaries.MAX_PASSWORD_BYTES + " bytes.");
    }
  }

  private static boolean tooLongForBcrypt(String password) {
    return password.getBytes(StandardCharsets.UTF_8).length > UserSummaries.MAX_PASSWORD_BYTES;
  }

  private static ApiException invalidCredentials() {
    return new ApiException(ErrorCode.INVALID_CREDENTIALS, "Email or password is incorrect.");
  }

  private static ApiException emailTaken() {
    return new ApiException(ErrorCode.EMAIL_TAKEN, "An account with this email already exists.");
  }
}
