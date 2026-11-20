package com.example.insurance.auth.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** A login identity. Roles and the insurer link travel into the JWT (PLAN 4). */
@Entity
@Table(name = "users")
public class UserAccount {

  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 254)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  @Column(name = "first_name", nullable = false, length = 100)
  private String firstName;

  @Column(name = "last_name", nullable = false, length = 100)
  private String lastName;

  @Column(name = "insurer_id")
  private UUID insurerId;

  @Column(name = "failed_login_count", nullable = false)
  private int failedLoginCount;

  @Column(name = "locked_until")
  private Instant lockedUntil;

  @Version private long version;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  // Eager, because roles are read after the transaction ends (open-in-view is off).
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
  @Column(name = "role", nullable = false, length = 20)
  private Set<String> roles = new HashSet<>();

  protected UserAccount() {
    // Required by JPA.
  }

  public static UserAccount create(
      String email,
      String passwordHash,
      String firstName,
      String lastName,
      UUID insurerId,
      Set<String> roles,
      Instant now) {
    UserAccount user = new UserAccount();
    user.id = UUID.randomUUID();
    user.email = email;
    user.passwordHash = passwordHash;
    user.firstName = firstName;
    user.lastName = lastName;
    user.insurerId = insurerId;
    user.roles = new HashSet<>(roles);
    user.createdAt = now;
    return user;
  }

  /** Counts one failed login. Returns true when this failure locks the account. */
  public boolean recordFailedLogin(int maxAttempts, Instant lockUntil) {
    failedLoginCount++;
    if (failedLoginCount >= maxAttempts) {
      failedLoginCount = 0;
      lockedUntil = lockUntil;
      return true;
    }
    return false;
  }

  public void recordSuccessfulLogin() {
    failedLoginCount = 0;
    lockedUntil = null;
  }

  public boolean isLockedAt(Instant now) {
    return lockedUntil != null && lockedUntil.isAfter(now);
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getFirstName() {
    return firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public UUID getInsurerId() {
    return insurerId;
  }

  public Set<String> getRoles() {
    return Set.copyOf(roles);
  }

  public Instant getLockedUntil() {
    return lockedUntil;
  }

  public int getFailedLoginCount() {
    return failedLoginCount;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
