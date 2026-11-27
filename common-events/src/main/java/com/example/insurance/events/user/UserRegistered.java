package com.example.insurance.events.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/**
 * Payload of {@code UserRegistered} on {@code user.events} (PLAN 10, M8a). Sent by auth-service
 * when a customer registers. It carries what a welcome message needs and nothing else: never a
 * password hash, a national ID or a role, because registration only ever creates a CUSTOMER.
 *
 * @param userId the new user's id; the envelope's aggregate id is the same value
 * @param email the address the customer registered with
 * @param firstName the customer's first name
 * @param lastName the customer's last name
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserRegistered(UUID userId, String email, String firstName, String lastName) {
  /** Payload schema version for the envelope's {@code version} field. */
  public static final int VERSION = 1;
}
