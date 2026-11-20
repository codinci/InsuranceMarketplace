package com.example.insurance.policyservice.catalogue.service;

import com.example.insurance.policyservice.catalogue.domain.InsuranceProduct;
import com.example.insurance.policyservice.catalogue.domain.Insurer;
import com.example.insurance.policyservice.catalogue.domain.PlanDocument;
import com.example.insurance.policyservice.catalogue.domain.PlanStatus;
import com.example.insurance.policyservice.catalogue.domain.PlanVersion;
import com.example.insurance.policyservice.catalogue.repository.InsuranceProductRepository;
import com.example.insurance.policyservice.catalogue.repository.PlanVersionRepository;
import com.example.insurance.policyservice.catalogue.rules.PlanValidator;
import com.example.insurance.policyservice.catalogue.rules.ValidationReport;
import com.example.insurance.policyservice.exception.ApiException;
import com.example.insurance.policyservice.exception.ErrorCode;
import com.example.insurance.policyservice.exception.PlanInvalidException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imports a plan document for an insurer (PLAN 5.4, ADR-010). The document is untrusted input: it
 * is size-capped, schema-checked and semantically checked before anything is stored (PLAN 12).
 */
@Service
public class PlanImportService {

  /** Larger documents are refused before parsing (PLAN 12). */
  public static final int MAX_DOCUMENT_BYTES = 256 * 1024;

  private final InsurerService insurers;
  private final InsuranceProductRepository products;
  private final PlanVersionRepository plans;
  private final PlanValidator validator;
  private final Clock clock;

  public PlanImportService(
      InsurerService insurers,
      InsuranceProductRepository products,
      PlanVersionRepository plans,
      PlanValidator validator,
      Clock clock) {
    this.insurers = insurers;
    this.products = products;
    this.plans = plans;
    this.validator = validator;
    this.clock = clock;
  }

  /**
   * @param actor the user id from the token, or null for the startup seeder
   */
  @Transactional
  public ImportOutcome importPlan(
      StaffScope scope, UUID insurerId, byte[] document, boolean dryRun, UUID actor) {
    if (document.length > MAX_DOCUMENT_BYTES) {
      throw new ApiException(
          ErrorCode.PLAN_TOO_LARGE,
          "A plan document can be at most " + MAX_DOCUMENT_BYTES / 1024 + " KB.");
    }
    Insurer insurer = insurers.visible(scope, insurerId);
    String json = new String(document, StandardCharsets.UTF_8);
    ValidationReport report = validator.validate(json);
    PlanDocument plan =
        report.validPlan().orElseThrow(() -> new PlanInvalidException(report.violations()));
    if (!plan.insurer().code().equals(insurer.getCode())) {
      throw new ApiException(
          ErrorCode.INSURER_MISMATCH,
          "The document's insurer code does not match the insurer it is imported into.");
    }
    if (dryRun) {
      return new ImportOutcome(ImportOutcome.Result.VALIDATED, null);
    }

    Instant now = clock.instant();
    InsuranceProduct product = productFor(insurer, plan, now);
    Optional<PlanVersion> existing =
        plans.findByProductIdAndCodeAndPlanVersion(
            product.getId(), plan.plan().code(), plan.plan().version());
    if (existing.isPresent()) {
      PlanVersion draft = existing.get();
      if (draft.getStatus() != PlanStatus.DRAFT) {
        throw new ApiException(
            ErrorCode.PLAN_VERSION_IMMUTABLE,
            "Version "
                + draft.getPlanVersion()
                + " is "
                + draft.getStatus()
                + ". Import it as a new version.");
      }
      draft.replaceDraft(plan, json, now);
      return new ImportOutcome(ImportOutcome.Result.REPLACED, AdminPlans.toResponse(draft));
    }
    PlanVersion created =
        plans.save(PlanVersion.draft(insurer.getId(), product.getId(), plan, json, actor, now));
    return new ImportOutcome(ImportOutcome.Result.CREATED, AdminPlans.toResponse(created));
  }

  private InsuranceProduct productFor(Insurer insurer, PlanDocument plan, Instant now) {
    return products
        .findByInsurerIdAndCode(insurer.getId(), plan.product().code())
        .orElseGet(() ->
            products.save(
                InsuranceProduct.create(
                    insurer.getId(),
                    plan.product().code(),
                    plan.product().name(),
                    plan.product().line(),
                    now)));
  }
}
