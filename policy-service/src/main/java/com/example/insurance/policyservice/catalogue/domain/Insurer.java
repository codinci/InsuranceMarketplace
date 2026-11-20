package com.example.insurance.policyservice.catalogue.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** An insurer on the marketplace (PLAN 7). Staff tokens carry its id as {@code insurerId}. */
@Entity
@Table(name = "insurers")
public class Insurer {

  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 40)
  private String code;

  @Column(nullable = false, length = 120)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private InsurerStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected Insurer() {
    // Required by JPA.
  }

  public static Insurer create(String code, String name, Instant now) {
    Insurer insurer = new Insurer();
    insurer.id = UUID.randomUUID();
    insurer.code = code;
    insurer.name = name;
    insurer.status = InsurerStatus.ACTIVE;
    insurer.createdAt = now;
    return insurer;
  }

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public InsurerStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
