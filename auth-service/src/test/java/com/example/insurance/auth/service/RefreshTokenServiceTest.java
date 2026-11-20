package com.example.insurance.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.insurance.auth.domain.RefreshToken;
import com.example.insurance.auth.exception.ApiException;
import com.example.insurance.auth.exception.ErrorCode;
import com.example.insurance.auth.repository.RefreshTokenRepository;
import com.example.insurance.auth.support.TestSupport;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RefreshTokenServiceTest {

  private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
  private final RefreshTokenService service =
      new RefreshTokenService(repository, TestSupport.properties(), TestSupport.fixedClock());

  @Test
  void storesTheHashNotTheRawToken() {
    String raw = service.issueFirst(UUID.randomUUID());

    ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
    verify(repository).save(saved.capture());
    assertThat(raw).isNotBlank();
    assertThat(saved.getValue().getTokenHash()).isEqualTo(RefreshTokenService.hash(raw));
    assertThat(saved.getValue().getTokenHash()).doesNotContain(raw);
  }

  @Test
  void rotationRevokesTheOldTokenAndIssuesOneInTheSameFamily() {
    UUID user = UUID.randomUUID();
    UUID family = UUID.randomUUID();
    RefreshToken presented = token(user, family, "presented", TestSupport.NOW.plusSeconds(60));
    when(repository.findByTokenHash(RefreshTokenService.hash("presented")))
        .thenReturn(Optional.of(presented));

    RefreshTokenService.Rotation rotation = service.rotate("presented");

    assertThat(presented.isRevoked()).isTrue();
    assertThat(rotation.userId()).isEqualTo(user);
    ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
    verify(repository).save(saved.capture());
    assertThat(saved.getValue().getFamilyId()).isEqualTo(family);
    assertThat(saved.getValue().getTokenHash())
        .isEqualTo(RefreshTokenService.hash(rotation.newToken()));
  }

  @Test
  void reusingARevokedTokenRevokesTheWholeFamilyAndFails() {
    UUID family = UUID.randomUUID();
    RefreshToken rotated = token(UUID.randomUUID(), family, "old", TestSupport.NOW.plusSeconds(60));
    rotated.revoke(TestSupport.NOW);
    RefreshToken sibling = token(UUID.randomUUID(), family, "new", TestSupport.NOW.plusSeconds(60));
    when(repository.findByTokenHash(RefreshTokenService.hash("old")))
        .thenReturn(Optional.of(rotated));
    when(repository.findByFamilyId(family)).thenReturn(List.of(rotated, sibling));

    assertThatThrownBy(() -> service.rotate("old"))
        .isInstanceOfSatisfying(
            ApiException.class,
            ex -> assertThat(ex.code()).isEqualTo(ErrorCode.REFRESH_TOKEN_REUSED));
    assertThat(sibling.isRevoked()).isTrue();
  }

  @Test
  void anExpiredTokenIsRejected() {
    RefreshToken expired =
        token(UUID.randomUUID(), UUID.randomUUID(), "late", TestSupport.NOW.minusSeconds(1));
    when(repository.findByTokenHash(RefreshTokenService.hash("late")))
        .thenReturn(Optional.of(expired));

    assertThatThrownBy(() -> service.rotate("late"))
        .isInstanceOfSatisfying(
            ApiException.class,
            ex -> assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));
  }

  @Test
  void anUnknownTokenIsRejectedButLogoutOfItIsSilent() {
    when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.rotate("nope"))
        .isInstanceOfSatisfying(
            ApiException.class,
            ex -> assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));
    service.revokeFamilyOf("nope");
    verify(repository, org.mockito.Mockito.never()).findByFamilyId(any());
  }

  /** Builds a stored token from a raw value, the same way the service does. */
  private static RefreshToken token(
      UUID user, UUID family, String raw, java.time.Instant expiresAt) {
    return RefreshToken.issue(
        user, family, RefreshTokenService.hash(raw), expiresAt, TestSupport.NOW);
  }
}
