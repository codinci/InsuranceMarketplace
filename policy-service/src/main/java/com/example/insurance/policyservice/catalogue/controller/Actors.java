package com.example.insurance.policyservice.catalogue.controller;

import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** The acting user's id from the access token's {@code sub}. */
final class Actors {

  private Actors() {}

  /** Null when the token has no subject; the audit columns then stay empty. */
  static UUID of(Jwt token) {
    String subject = token.getSubject();
    return subject == null ? null : UUID.fromString(subject);
  }
}
