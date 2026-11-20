package com.example.insurance.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import java.util.UUID;

@Schema(description = "A user as the API shows it. Never includes the password hash.")
public record UserSummary(
    UUID id, String email, String firstName, String lastName, Set<String> roles, UUID insurerId) {

  public UserSummary {
    // A defensive, immutable copy: the record must not share a mutable set with its caller.
    roles = Set.copyOf(roles);
  }
}
