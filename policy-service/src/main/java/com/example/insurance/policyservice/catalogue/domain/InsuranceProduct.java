package com.example.insurance.policyservice.catalogue.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A product line of an insurer, such as MEDICAL. Plans belong to a product (PLAN 7). */
@Entity
@Table(name = "products")
public class InsuranceProduct {

  @Id private UUID id;

  @Column(name = "insurer_id", nullable = false)
  private UUID insurerId;

  @Column(nullable = false, length = 40)
  private String code;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(nullable = false, length = 20)
  private String line;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected InsuranceProduct() {
    // Required by JPA.
  }

  public static InsuranceProduct create(
      UUID insurerId, String code, String name, String line, Instant now) {
    InsuranceProduct product = new InsuranceProduct();
    product.id = UUID.randomUUID();
    product.insurerId = insurerId;
    product.code = code;
    product.name = name;
    product.line = line;
    product.createdAt = now;
    return product;
  }

  public UUID getId() {
    return id;
  }

  public UUID getInsurerId() {
    return insurerId;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public String getLine() {
    return line;
  }
}
