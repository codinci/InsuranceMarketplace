package com.example.insurance.policyservice.catalogue.service;

import com.example.insurance.policyservice.catalogue.domain.AgeRange;
import com.example.insurance.policyservice.catalogue.domain.BenefitNode;
import com.example.insurance.policyservice.catalogue.domain.ChildAge;
import com.example.insurance.policyservice.catalogue.domain.CoverType;
import com.example.insurance.policyservice.catalogue.domain.Eligibility;
import com.example.insurance.policyservice.catalogue.domain.InsuranceProduct;
import com.example.insurance.policyservice.catalogue.domain.Insurer;
import com.example.insurance.policyservice.catalogue.domain.Limit;
import com.example.insurance.policyservice.catalogue.domain.ModuleDef;
import com.example.insurance.policyservice.catalogue.domain.ModuleOption;
import com.example.insurance.policyservice.catalogue.domain.PlanDefinition;
import com.example.insurance.policyservice.catalogue.domain.PlanVersion;
import com.example.insurance.policyservice.catalogue.domain.WaitingRule;
import com.example.insurance.policyservice.catalogue.dto.PlanDetailResponse;
import com.example.insurance.policyservice.catalogue.dto.PlanDetailResponse.AgeRangeView;
import com.example.insurance.policyservice.catalogue.dto.PlanDetailResponse.BenefitView;
import com.example.insurance.policyservice.catalogue.dto.PlanDetailResponse.CancellationView;
import com.example.insurance.policyservice.catalogue.dto.PlanDetailResponse.ChildAgeView;
import com.example.insurance.policyservice.catalogue.dto.PlanDetailResponse.CopayView;
import com.example.insurance.policyservice.catalogue.dto.PlanDetailResponse.EligibilityView;
import com.example.insurance.policyservice.catalogue.dto.PlanDetailResponse.LimitView;
import com.example.insurance.policyservice.catalogue.dto.PlanDetailResponse.ModuleView;
import com.example.insurance.policyservice.catalogue.dto.PlanDetailResponse.OptionView;
import com.example.insurance.policyservice.catalogue.dto.PlanDetailResponse.WaitingRuleView;
import com.example.insurance.policyservice.catalogue.dto.PlanSummaryResponse;
import com.example.insurance.policyservice.catalogue.dto.PlanSummaryResponse.ModuleSummary;

/** Maps a stored plan document to the public DTOs, leaving rating out (PLAN 13). */
final class PlanViews {

  private PlanViews() {}

  static PlanSummaryResponse summary(
      PlanVersion plan, PlanDefinition terms, Insurer insurer, InsuranceProduct product) {
    return new PlanSummaryResponse(
        plan.getId(),
        insurer.getCode(),
        product.getCode(),
        product.getName(),
        plan.getCode(),
        plan.getName(),
        plan.getPlanVersion(),
        plan.getCurrency(),
        plan.getSaleFrom(),
        plan.getSaleTo(),
        terms.modules().stream()
            .map(m ->
                new ModuleSummary(
                    m.code(), m.mandatory(), m.options().stream().map(ModuleOption::code).toList()))
            .toList());
  }

  static PlanDetailResponse detail(
      PlanVersion plan, PlanDefinition terms, Insurer insurer, InsuranceProduct product) {
    return new PlanDetailResponse(
        plan.getId(),
        insurer.getCode(),
        insurer.getName(),
        product.getCode(),
        plan.getCode(),
        plan.getName(),
        plan.getPlanVersion(),
        plan.getCurrency(),
        plan.getSaleFrom(),
        plan.getSaleTo(),
        terms.ageBasis().name(),
        eligibility(terms.eligibility()),
        terms.modules().stream().map(PlanViews::module).toList(),
        terms.waitingRules().stream().map(PlanViews::waitingRule).toList(),
        terms.exclusions(),
        terms.claimRules().filingDeadlineDays(),
        new CancellationView(
            terms.cancellation().noticeDays(),
            terms.cancellation().refund().model(),
            terms.cancellation().refund().noRefundAfterMonths(),
            terms.cancellation().refund().noRefundIfClaims()));
  }

  private static EligibilityView eligibility(Eligibility eligibility) {
    ChildAge child = eligibility.childAge();
    return new EligibilityView(
        eligibility.coverTypes().stream().map(CoverType::name).toList(),
        range(eligibility.principalAge()),
        range(eligibility.effectiveSpouseAge()),
        child == null
            ? null
            : new ChildAgeView(
                child.min(), child.max(), child.maxIfStudent(), child.studentEvidenceRequired()),
        eligibility.maxChildren());
  }

  private static AgeRangeView range(AgeRange range) {
    return new AgeRangeView(range.min(), range.max());
  }

  private static ModuleView module(ModuleDef module) {
    return new ModuleView(
        module.code(),
        module.mandatory(),
        module.options().stream()
            .map(o -> new OptionView(o.code(), benefit(o.benefits())))
            .toList());
  }

  private static BenefitView benefit(BenefitNode node) {
    Limit limit = node.limit();
    return new BenefitView(
        node.code(),
        node.kind().name(),
        limit == null
            ? null
            : new LimitView(
                limit.amount(),
                limit.period().name(),
                limit.scope().name(),
                limit.maxPerVisit(),
                limit.maxCount()),
        node.copay() == null
            ? null
            : new CopayView(node.copay().type().name(), node.copay().value()),
        node.requiredDocuments(),
        node.preAuthRequired(),
        node.children().stream().map(PlanViews::benefit).toList());
  }

  private static WaitingRuleView waitingRule(WaitingRule rule) {
    return new WaitingRuleView(
        rule.code(),
        rule.days(),
        rule.precedence(),
        rule.when().cause(),
        rule.when().surgical(),
        rule.when().conditionTags(),
        rule.when().benefits());
  }
}
