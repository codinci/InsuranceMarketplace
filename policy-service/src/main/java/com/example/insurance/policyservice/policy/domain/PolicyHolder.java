package com.example.insurance.policyservice.policy.domain;

import com.example.insurance.policyservice.catalogue.api.Sex;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The customer behind policies (PLAN 7). Keyed by the auth-service user id from the token's {@code
 * sub}. The national ID is encrypted at rest and never logged (PLAN 12).
 */
@Entity
@Table(name = "policy_holders")
public class PolicyHolder {

  @Id
  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "first_name", nullable = false, length = 100)
  private String firstName;

  @Column(name = "last_name", nullable = false, length = 100)
  private String lastName;

  @Column(name = "date_of_birth", nullable = false)
  private LocalDate dateOfBirth;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Sex sex;

  @Convert(converter = NationalIdConverter.class)
  @Column(name = "national_id", nullable = false, length = 200)
  private String nationalId;

  @Column(nullable = false, length = 20)
  private String phone;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "lock_version", nullable = false)
  private long lockVersion;

  protected PolicyHolder() {
    // Required by JPA.
  }

  public static PolicyHolder create(UUID userId, Instant now) {
    PolicyHolder holder = new PolicyHolder();
    holder.userId = userId;
    holder.createdAt = now;
    return holder;
  }

  public void update(
      String firstName,
      String lastName,
      LocalDate dateOfBirth,
      Sex sex,
      String nationalId,
      String phone,
      Instant now) {
    this.firstName = firstName;
    this.lastName = lastName;
    this.dateOfBirth = dateOfBirth;
    this.sex = sex;
    this.nationalId = nationalId;
    this.phone = phone;
    this.updatedAt = now;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getFirstName() {
    return firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public LocalDate getDateOfBirth() {
    return dateOfBirth;
  }

  public Sex getSex() {
    return sex;
  }

  public String getNationalId() {
    return nationalId;
  }

  public String getPhone() {
    return phone;
  }

  /** Only the last three characters are ever shown (PLAN 12). */
  public String maskedNationalId() {
    return nationalId.length() <= 3 ? "***" : "***" + nationalId.substring(nationalId.length() - 3);
  }

  @Override
  public String toString() {
    return "PolicyHolder[userId=" + userId + "]";
  }
}
