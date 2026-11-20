package com.example.insurance.policyservice.catalogue.service;

import com.example.insurance.events.policy.TermsSnapshot;
import com.example.insurance.events.policy.TermsSnapshot.BenefitTerms;
import com.example.insurance.events.policy.TermsSnapshot.CancellationTerms;
import com.example.insurance.events.policy.TermsSnapshot.CopayTerms;
import com.example.insurance.events.policy.TermsSnapshot.LimitTerms;
import com.example.insurance.events.policy.TermsSnapshot.ModuleTerms;
import com.example.insurance.events.policy.TermsSnapshot.WaitingRuleTerms;
import com.example.insurance.policyservice.catalogue.api.CatalogueApiException;
import com.example.insurance.policyservice.catalogue.domain.BenefitNode;
import com.example.insurance.policyservice.catalogue.domain.Limit;
import com.example.insurance.policyservice.catalogue.domain.ModuleDef;
import com.example.insurance.policyservice.catalogue.domain.ModuleOption;
import com.example.insurance.policyservice.catalogue.domain.PlanDocument;
import com.example.insurance.policyservice.catalogue.domain.WaitingRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Builds the frozen terms of the selected options (ADR-011). Rating tables are not included. */
final class TermsSnapshots {

  private TermsSnapshots() {}

  static TermsSnapshot of(UUID planId, PlanDocument document, Map<String, String> selection) {
    var plan = document.plan();
    List<ModuleTerms> modules = new ArrayList<>();
    for (ModuleDef module : plan.modules()) {
      String optionCode = selection.get(module.code());
      if (optionCode == null) {
        continue;
      }
      ModuleOption option =
          module.options().stream()
              .filter(o -> o.code().equals(optionCode))
              .findFirst()
              .orElseThrow(() ->
                  new CatalogueApiException(
                      "OPTION_NOT_FOUND",
                      "The module " + module.code() + " has no option " + optionCode + "."));
      modules.add(new ModuleTerms(module.code(), option.code(), benefit(option.benefits())));
    }
    return new TermsSnapshot(
        TermsSnapshot.CURRENT_VERSION,
        document.insurer().code(),
        planId,
        plan.code(),
        plan.version(),
        plan.currency(),
        plan.ageBasis().name(),
        modules,
        plan.waitingRules().stream().map(TermsSnapshots::waitingRule).toList(),
        plan.exclusions(),
        plan.claimRules().filingDeadlineDays(),
        new CancellationTerms(
            plan.cancellation().noticeDays(),
            plan.cancellation().refund().model(),
            plan.cancellation().refund().noRefundAfterMonths(),
            plan.cancellation().refund().noRefundIfClaims()));
  }

  private static BenefitTerms benefit(BenefitNode node) {
    Limit limit = node.limit();
    return new BenefitTerms(
        node.code(),
        node.kind().name(),
        limit == null
            ? null
            : new LimitTerms(
                limit.amount(),
                limit.period().name(),
                limit.scope().name(),
                limit.maxPerVisit(),
                limit.maxCount()),
        node.copay() == null
            ? null
            : new CopayTerms(node.copay().type().name(), node.copay().value()),
        node.requiredDocuments(),
        node.preAuthRequired(),
        node.children().stream().map(TermsSnapshots::benefit).toList());
  }

  private static WaitingRuleTerms waitingRule(WaitingRule rule) {
    return new WaitingRuleTerms(
        rule.code(),
        rule.days(),
        rule.precedence(),
        rule.when().cause(),
        rule.when().surgical(),
        rule.when().conditionTags(),
        rule.when().benefits());
  }
}
