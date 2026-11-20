package com.example.insurance.policyservice.catalogue.service;

import com.example.insurance.policyservice.catalogue.domain.PlanVersion;
import com.example.insurance.policyservice.catalogue.dto.AdminPlanResponse;

/** Maps plan entities to the staff DTO. */
final class AdminPlans {

  private AdminPlans() {}

  static AdminPlanResponse toResponse(PlanVersion plan) {
    return new AdminPlanResponse(
        plan.getId(),
        plan.getInsurerId(),
        plan.getProductId(),
        plan.getCode(),
        plan.getName(),
        plan.getPlanVersion(),
        plan.getStatus().name(),
        plan.getSaleFrom(),
        plan.getSaleTo(),
        plan.getCurrency(),
        plan.getCreatedAt(),
        plan.getPublishedAt(),
        plan.getRetiredAt());
  }
}
