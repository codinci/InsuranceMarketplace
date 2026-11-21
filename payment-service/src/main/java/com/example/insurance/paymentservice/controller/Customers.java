package com.example.insurance.paymentservice.controller;

import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** The calling customer's user id, from the token's {@code sub}. */
final class Customers {

  private Customers() {}

  static UUID of(Jwt token) {
    String subject = token.getSubject();
    if (subject == null) {
      throw new IllegalArgumentException("The token has no subject.");
    }
    return UUID.fromString(subject);
  }
}
