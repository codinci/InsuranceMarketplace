package com.example.insurance.policyservice.catalogue.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One version of a plan, holding the whole validated document as {@code jsonb} (ADR-002). Only a
 * DRAFT can change; PUBLISHED and RETIRED versions are immutable, which a database trigger also
 * enforces (ADR-010). {@code @DynamicUpdate} writes only changed columns, so a status change never
 * rewrites the document.
 */
@Entity
@Table(name = "plans")
@DynamicUpdate
public class PlanVersion {

  @Id private UUID id;

  @Column(name = "insurer_id", nullable = false)
  private UUID insurerId;

  @Column(name = "product_id", nullable = false)
  private UUID productId;

  @Column(nullable = false, length = 40)
  private String code;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(name = "plan_version", nullable = false)
  private int planVersion;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PlanStatus status;

  @Column(name = "sale_from", nullable = false)
  private LocalDate saleFrom;

  @Column(name = "sale_to")
  private LocalDate saleTo;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "schema_version", nullable = false)
  private int schemaVersion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String definition;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "published_by")
  private UUID publishedBy;

  @Column(name = "retired_at")
  private Instant retiredAt;

  @Version
  @Column(name = "lock_version", nullable = false)
  private long lockVersion;

  protected PlanVersion() {
    // Required by JPA.
  }

  /** A new DRAFT from a validated document. */
  public static PlanVersion draft(
      UUID insurerId, UUID productId, PlanDocument document, String json, UUID actor, Instant now) {
    PlanVersion plan = new PlanVersion();
    plan.id = UUID.randomUUID();
    plan.insurerId = insurerId;
    plan.productId = productId;
    plan.code = document.plan().code();
    plan.planVersion = document.plan().version();
    plan.status = PlanStatus.DRAFT;
    plan.createdAt = now;
    plan.createdBy = actor;
    plan.setTerms(document, json, now);
    return plan;
  }

  /** Replaces the document of a DRAFT (D34). */
  public void replaceDraft(PlanDocument document, String json, Instant now) {
    requireStatus(PlanStatus.DRAFT);
    setTerms(document, json, now);
  }

  public void publish(UUID actor, Instant now) {
    requireStatus(PlanStatus.DRAFT);
    status = PlanStatus.PUBLISHED;
    publishedAt = now;
    publishedBy = actor;
    updatedAt = now;
  }

  public void retire(Instant now) {
    requireStatus(PlanStatus.PUBLISHED);
    status = PlanStatus.RETIRED;
    retiredAt = now;
    updatedAt = now;
  }

  private void setTerms(PlanDocument document, String json, Instant now) {
    PlanDefinition terms = document.plan();
    name = terms.name();
    saleFrom = terms.sale().from();
    saleTo = terms.sale().to();
    currency = terms.currency();
    schemaVersion = document.schemaVersion();
    definition = json;
    updatedAt = now;
  }

  private void requireStatus(PlanStatus expected) {
    if (status != expected) {
      throw new IllegalStateException("Plan " + id + " is " + status + ", not " + expected + ".");
    }
  }

  /** Whether customers can buy it on {@code day}. */
  public boolean onSale(LocalDate day) {
    return status == PlanStatus.PUBLISHED
        && !saleFrom.isAfter(day)
        && (saleTo == null || !saleTo.isBefore(day));
  }

  public UUID getId() {
    return id;
  }

  public UUID getInsurerId() {
    return insurerId;
  }

  public UUID getProductId() {
    return productId;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public int getPlanVersion() {
    return planVersion;
  }

  public PlanStatus getStatus() {
    return status;
  }

  public LocalDate getSaleFrom() {
    return saleFrom;
  }

  public LocalDate getSaleTo() {
    return saleTo;
  }

  public String getCurrency() {
    return currency;
  }

  public String getDefinition() {
    return definition;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public Instant getRetiredAt() {
    return retiredAt;
  }
}
