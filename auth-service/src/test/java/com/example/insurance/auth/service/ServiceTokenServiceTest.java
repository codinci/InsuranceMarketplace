package com.example.insurance.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.insurance.auth.config.ServiceAuthProperties;
import com.example.insurance.auth.exception.ApiException;
import com.example.insurance.auth.exception.ErrorCode;
import com.example.insurance.auth.support.TestSupport;
import com.example.insurance.common.Roles;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class ServiceTokenServiceTest {

  private static final String SECRET = "test-secret-policy";

  private final ServiceTokenService tokens =
      new ServiceTokenService(
          TestSupport.encoder(),
          TestSupport.properties(),
          new ServiceAuthProperties(
              Duration.ofMinutes(5), Map.of("policy-service", SECRET, "retired-service", "")),
          TestSupport.fixedClock());

  @Test
  void validSecretGetsAServiceTokenThatTheJwksDecoderAccepts() throws Exception {
    ServiceTokenService.ServiceToken token = tokens.issue("policy-service", SECRET);

    Jwt decoded = TestSupport.decoder().decode(token.value());

    assertThat(decoded.getSubject()).isEqualTo("policy-service");
    assertThat(decoded.getClaimAsStringList(TokenService.CLAIM_ROLES))
        .containsExactly(Roles.SERVICE);
    assertThat(decoded.getClaimAsString(TokenService.CLAIM_INSURER_ID)).isNull();
    assertThat(decoded.getClaimAsString("iss")).isEqualTo(TestSupport.ISSUER);
    assertThat(decoded.getHeaders()).containsEntry("kid", TestSupport.KEY_ID);
  }

  @Test
  void tokenLivesFiveMinutes() throws Exception {
    ServiceTokenService.ServiceToken token = tokens.issue("policy-service", SECRET);

    Jwt decoded = TestSupport.decoder().decode(token.value());

    assertThat(decoded.getExpiresAt()).isEqualTo(TestSupport.NOW.plusSeconds(5 * 60));
    assertThat(token.expiresInSeconds()).isEqualTo(5 * 60);
  }

  @Test
  void wrongSecretIsRefused() {
    assertThatThrownBy(() -> tokens.issue("policy-service", "guess"))
        .isInstanceOfSatisfying(
            ApiException.class,
            ex -> assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_SERVICE_CREDENTIALS));
  }

  @Test
  void unknownServiceGetsTheSameAnswerAsAWrongSecret() {
    assertThatThrownBy(() -> tokens.issue("claim-service", SECRET))
        .isInstanceOfSatisfying(
            ApiException.class,
            ex -> assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_SERVICE_CREDENTIALS));
  }

  @Test
  void serviceWithBlankSecretCannotGetAToken() {
    assertThatThrownBy(() -> tokens.issue("retired-service", ""))
        .isInstanceOfSatisfying(
            ApiException.class,
            ex -> assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_SERVICE_CREDENTIALS));
  }

  @Test
  void missingSecretIsRefused() {
    assertThatThrownBy(() -> tokens.issue("policy-service", null)).isInstanceOf(ApiException.class);
  }
}
