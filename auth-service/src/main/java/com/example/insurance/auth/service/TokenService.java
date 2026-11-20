package com.example.insurance.auth.service;

import com.example.insurance.auth.config.AuthProperties;
import com.example.insurance.auth.domain.UserAccount;
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
 * Issues RS256 access tokens (PLAN 12). Claims: {@code sub}, {@code roles}, {@code insurerId} (only
 * when set), {@code iss}, {@code iat}, {@code exp}, {@code jti}. The header carries the key id.
 */
@Service
public class TokenService {

  public static final String CLAIM_ROLES = "roles";
  public static final String CLAIM_INSURER_ID = "insurerId";

  private final JwtEncoder encoder;
  private final AuthProperties properties;
  private final Clock clock;

  public TokenService(JwtEncoder encoder, AuthProperties properties, Clock clock) {
    this.encoder = encoder;
    this.properties = properties;
    this.clock = clock;
  }

  /** A signed access token and its expiry. */
  public record AccessToken(String value, long expiresInSeconds) {}

  public AccessToken issue(UserAccount user) {
    Instant now = clock.instant();
    Instant expiresAt = now.plus(properties.token().accessTtl());
    JwtClaimsSet.Builder claims =
        JwtClaimsSet.builder()
            .issuer(properties.jwt().issuer())
            .subject(user.getId().toString())
            .issuedAt(now)
            .expiresAt(expiresAt)
            .id(UUID.randomUUID().toString())
            .claim(CLAIM_ROLES, List.copyOf(user.getRoles().stream().sorted().toList()));
    if (user.getInsurerId() != null) {
      claims.claim(CLAIM_INSURER_ID, user.getInsurerId().toString());
    }
    JwsHeader header =
        JwsHeader.with(SignatureAlgorithm.RS256).keyId(properties.jwt().keyId()).build();
    String value =
        encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    return new AccessToken(value, properties.token().accessTtl().toSeconds());
  }
}
