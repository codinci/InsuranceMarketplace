package com.example.insurance.auth.service;

import com.example.insurance.auth.config.AuthProperties;
import com.example.insurance.auth.config.ServiceAuthProperties;
import com.example.insurance.auth.exception.ApiException;
import com.example.insurance.auth.exception.ErrorCode;
import com.example.insurance.common.Roles;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues short-lived SERVICE tokens for internal calls (ADR-014). The token is RS256, signed with
 * the same key as user tokens, so receiving services need no extra configuration. It carries {@code
 * sub} = service name and {@code roles} = [SERVICE], and never an {@code insurerId}.
 */
@Service
public class ServiceTokenService {

  private final JwtEncoder encoder;
  private final AuthProperties properties;
  private final ServiceAuthProperties serviceProperties;
  private final Clock clock;

  public ServiceTokenService(
      JwtEncoder encoder,
      AuthProperties properties,
      ServiceAuthProperties serviceProperties,
      Clock clock) {
    this.encoder = encoder;
    this.properties = properties;
    this.serviceProperties = serviceProperties;
    this.clock = clock;
  }

  /** A signed service token and its lifetime in seconds. */
  public record ServiceToken(String value, long expiresInSeconds) {}

  /**
   * Checks the secret and issues a token. An unknown service, a blank configured secret and a wrong
   * secret all give the same answer, so the caller learns nothing about which services exist.
   */
  public ServiceToken issue(String serviceName, String presentedSecret) {
    String expected = serviceName == null ? null : serviceProperties.secrets().get(serviceName);
    if (expected == null
        || expected.isBlank()
        || presentedSecret == null
        || !MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            presentedSecret.getBytes(StandardCharsets.UTF_8))) {
      throw new ApiException(
          ErrorCode.INVALID_SERVICE_CREDENTIALS, "Unknown service or wrong secret.");
    }
    Instant now = clock.instant();
    Instant expiresAt = now.plus(serviceProperties.tokenTtl());
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(properties.jwt().issuer())
            .subject(serviceName)
            .issuedAt(now)
            .expiresAt(expiresAt)
            .id(UUID.randomUUID().toString())
            .claim(TokenService.CLAIM_ROLES, List.of(Roles.SERVICE))
            .build();
    JwsHeader header =
        JwsHeader.with(SignatureAlgorithm.RS256).keyId(properties.jwt().keyId()).build();
    String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    return new ServiceToken(value, serviceProperties.tokenTtl().toSeconds());
  }
}
