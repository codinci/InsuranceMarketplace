package com.example.insurance.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.insurance.auth.domain.UserAccount;
import com.example.insurance.auth.support.TestSupport;
import com.example.insurance.common.Roles;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class TokenServiceTest {

  private final TokenService tokens =
      new TokenService(TestSupport.encoder(), TestSupport.properties(), TestSupport.fixedClock());

  @Test
  void accessTokenIsRs256WithKeyIdAndCarriesRolesAndInsurer() throws Exception {
    UUID insurer = UUID.randomUUID();
    UserAccount user = account(Set.of(Roles.ADJUSTER), insurer);

    Jwt decoded = TestSupport.decoder().decode(tokens.issue(user).value());

    assertThat(decoded.getSubject()).isEqualTo(user.getId().toString());
    assertThat(decoded.getClaimAsStringList(TokenService.CLAIM_ROLES))
        .containsExactly(Roles.ADJUSTER);
    assertThat(decoded.getClaimAsString(TokenService.CLAIM_INSURER_ID))
        .isEqualTo(insurer.toString());
    assertThat(decoded.getClaimAsString("iss")).isEqualTo(TestSupport.ISSUER);
    assertThat(decoded.getHeaders()).containsEntry("kid", TestSupport.KEY_ID);
    assertThat(decoded.getHeaders()).containsEntry("alg", "RS256");
  }

  @Test
  void accessTokenExpiresAfterFifteenMinutes() throws Exception {
    Jwt decoded =
        TestSupport.decoder().decode(tokens.issue(account(Set.of(Roles.CUSTOMER), null)).value());

    assertThat(decoded.getExpiresAt()).isEqualTo(TestSupport.NOW.plusSeconds(15 * 60));
  }

  @Test
  void customerTokenHasNoInsurerClaim() throws Exception {
    Jwt decoded =
        TestSupport.decoder().decode(tokens.issue(account(Set.of(Roles.CUSTOMER), null)).value());

    assertThat(decoded.hasClaim(TokenService.CLAIM_INSURER_ID)).isFalse();
    assertThat(decoded.getClaimAsStringList(TokenService.CLAIM_ROLES))
        .containsExactly(Roles.CUSTOMER);
  }

  private static UserAccount account(Set<String> roles, UUID insurer) {
    return UserAccount.create(
        "user@example.com", "hash", "Amina", "Otieno", insurer, roles, TestSupport.NOW);
  }
}
