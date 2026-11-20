package com.example.insurance.policyservice.catalogue.service;

import com.example.insurance.policyservice.catalogue.domain.PlanStatus;
import com.example.insurance.policyservice.catalogue.domain.PlanVersion;
import com.example.insurance.policyservice.catalogue.dto.AdminPlanResponse;
import com.example.insurance.policyservice.catalogue.repository.PlanVersionRepository;
import com.example.insurance.policyservice.exception.ApiException;
import com.example.insurance.policyservice.exception.ErrorCode;
import com.example.insurance.policyservice.web.PageResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Publishing, retiring and listing plan versions (ADR-010, D33). */
@Service
public class PlanLifecycleService {

  private final PlanVersionRepository plans;
  private final InsurerService insurers;
  private final Clock clock;

  public PlanLifecycleService(PlanVersionRepository plans, InsurerService insurers, Clock clock) {
    this.plans = plans;
    this.insurers = insurers;
    this.clock = clock;
  }

  /**
   * Publishes a DRAFT and retires the older published version of the same plan in the same
   * transaction (D33). Publishing an older version over a newer published one is refused.
   */
  @Transactional
  public AdminPlanResponse publish(StaffScope scope, UUID planId, UUID actor) {
    PlanVersion plan = visible(scope, planId);
    if (plan.getStatus() != PlanStatus.DRAFT) {
      throw new ApiException(ErrorCode.PLAN_NOT_DRAFT, "Only a DRAFT can be published.");
    }
    if (plans.existsByProductIdAndCodeAndStatusAndPlanVersionGreaterThan(
        plan.getProductId(), plan.getCode(), PlanStatus.PUBLISHED, plan.getPlanVersion())) {
      throw new ApiException(
          ErrorCode.NEWER_VERSION_PUBLISHED, "A newer version of this plan is already published.");
    }
    Instant now = clock.instant();
    for (PlanVersion older :
        plans.findByProductIdAndCodeAndStatus(
            plan.getProductId(), plan.getCode(), PlanStatus.PUBLISHED)) {
      older.retire(now);
      // Flush before publishing: at most one published version may exist at any moment, and the
      // database checks that row by row (plans_one_published).
      plans.saveAndFlush(older);
    }
    plan.publish(actor, now);
    return AdminPlans.toResponse(plans.saveAndFlush(plan));
  }

  @Transactional
  public AdminPlanResponse retire(StaffScope scope, UUID planId) {
    PlanVersion plan = visible(scope, planId);
    if (plan.getStatus() != PlanStatus.PUBLISHED) {
      throw new ApiException(ErrorCode.PLAN_NOT_PUBLISHED, "Only a PUBLISHED plan can be retired.");
    }
    plan.retire(clock.instant());
    return AdminPlans.toResponse(plans.saveAndFlush(plan));
  }

  /**
   * Plans the staff member may see. A platform admin may filter by insurer; an insurer admin always
   * sees their own insurer, and naming another insurer answers 404.
   */
  @Transactional(readOnly = true)
  public PageResponse<AdminPlanResponse> list(
      StaffScope scope, String statusName, UUID insurerFilter, Pageable pageable) {
    PlanStatus status = statusName == null ? null : PlanStatus.valueOf(statusName);
    UUID insurerId = scope.isPlatform() ? insurerFilter : scope.insurerId();
    if (insurerFilter != null) {
      insurers.visible(scope, insurerFilter);
    }
    Specification<PlanVersion> spec = Specification.unrestricted();
    if (insurerId != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("insurerId"), insurerId));
    }
    if (status != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
    }
    return PageResponse.of(plans.findAll(spec, pageable), AdminPlans::toResponse);
  }

  private PlanVersion visible(StaffScope scope, UUID planId) {
    return plans
        .findById(planId)
        .filter(plan -> scope.canSee(plan.getInsurerId()))
        .orElseThrow(() -> new ApiException(ErrorCode.PLAN_NOT_FOUND, "No such plan."));
  }
}
