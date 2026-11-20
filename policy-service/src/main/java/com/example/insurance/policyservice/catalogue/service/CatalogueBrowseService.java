package com.example.insurance.policyservice.catalogue.service;

import com.example.insurance.policyservice.catalogue.domain.InsuranceProduct;
import com.example.insurance.policyservice.catalogue.domain.Insurer;
import com.example.insurance.policyservice.catalogue.domain.InsurerStatus;
import com.example.insurance.policyservice.catalogue.domain.PlanStatus;
import com.example.insurance.policyservice.catalogue.domain.PlanVersion;
import com.example.insurance.policyservice.catalogue.dto.InsurerResponse;
import com.example.insurance.policyservice.catalogue.dto.PageResponse;
import com.example.insurance.policyservice.catalogue.dto.PlanDetailResponse;
import com.example.insurance.policyservice.catalogue.dto.PlanSummaryResponse;
import com.example.insurance.policyservice.catalogue.repository.InsuranceProductRepository;
import com.example.insurance.policyservice.catalogue.repository.InsurerRepository;
import com.example.insurance.policyservice.catalogue.repository.PlanVersionRepository;
import com.example.insurance.policyservice.exception.ApiException;
import com.example.insurance.policyservice.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The public catalogue (PLAN 13): active insurers, plans on sale today, and the terms of a
 * published plan. Drafts and retired plans are never shown.
 */
@Service
public class CatalogueBrowseService {

  private final InsurerRepository insurers;
  private final InsuranceProductRepository products;
  private final PlanVersionRepository plans;
  private final StoredPlans stored;
  private final Clock clock;

  public CatalogueBrowseService(
      InsurerRepository insurers,
      InsuranceProductRepository products,
      PlanVersionRepository plans,
      StoredPlans stored,
      Clock clock) {
    this.insurers = insurers;
    this.products = products;
    this.plans = plans;
    this.stored = stored;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public PageResponse<InsurerResponse> insurers(Pageable pageable) {
    return PageResponse.of(
        insurers.findByStatus(InsurerStatus.ACTIVE, pageable), InsurerService::toResponse);
  }

  @Transactional(readOnly = true)
  public PageResponse<PlanSummaryResponse> plansOnSale(String insurerCode, Pageable pageable) {
    Insurer insurer =
        insurers
            .findByCode(insurerCode)
            .filter(i -> i.getStatus() == InsurerStatus.ACTIVE)
            .orElseThrow(() -> new ApiException(ErrorCode.INSURER_NOT_FOUND, "No such insurer."));
    LocalDate today = LocalDate.now(clock);
    return PageResponse.of(
        plans.findOnSale(insurer.getId(), today, pageable),
        plan -> PlanViews.summary(plan, stored.document(plan).plan(), insurer, product(plan)));
  }

  @Transactional(readOnly = true)
  public PlanDetailResponse plan(UUID planId) {
    PlanVersion plan =
        plans
            .findById(planId)
            .filter(p -> p.getStatus() == PlanStatus.PUBLISHED)
            .orElseThrow(() -> new ApiException(ErrorCode.PLAN_NOT_FOUND, "No such plan."));
    Insurer insurer = insurers.findById(plan.getInsurerId()).orElseThrow();
    return PlanViews.detail(plan, stored.document(plan).plan(), insurer, product(plan));
  }

  private InsuranceProduct product(PlanVersion plan) {
    return products.findById(plan.getProductId()).orElseThrow();
  }
}
