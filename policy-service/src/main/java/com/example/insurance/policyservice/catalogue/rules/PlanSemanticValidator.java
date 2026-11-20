package com.example.insurance.policyservice.catalogue.rules;

import com.example.insurance.policyservice.catalogue.domain.AgeRange;
import com.example.insurance.policyservice.catalogue.domain.BenefitKind;
import com.example.insurance.policyservice.catalogue.domain.BenefitNode;
import com.example.insurance.policyservice.catalogue.domain.Charge;
import com.example.insurance.policyservice.catalogue.domain.ChildAge;
import com.example.insurance.policyservice.catalogue.domain.Eligibility;
import com.example.insurance.policyservice.catalogue.domain.Limit;
import com.example.insurance.policyservice.catalogue.domain.LimitPeriod;
import com.example.insurance.policyservice.catalogue.domain.LimitScope;
import com.example.insurance.policyservice.catalogue.domain.ModuleDef;
import com.example.insurance.policyservice.catalogue.domain.ModuleOption;
import com.example.insurance.policyservice.catalogue.domain.PlanDefinition;
import com.example.insurance.policyservice.catalogue.domain.PlanDocument;
import com.example.insurance.policyservice.catalogue.domain.Violation;
import com.example.insurance.policyservice.catalogue.domain.WaitingRule;
import com.example.insurance.policyservice.catalogue.rating.RatingStrategyRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The rules a schema cannot express (PLAN 5.4, D31, D32, docs/catalogue-schema.md). Runs only on a
 * document that passed the schema, and reports every violation it finds.
 */
@Component
public class PlanSemanticValidator {

  private static final String SUPPORTED_CURRENCY = "KES";

  private final RatingStrategyRegistry ratings;

  public PlanSemanticValidator(RatingStrategyRegistry ratings) {
    this.ratings = ratings;
  }

  public List<Violation> validate(PlanDocument document) {
    PlanDefinition plan = document.plan();
    List<Violation> violations = new ArrayList<>();
    if (!SUPPORTED_CURRENCY.equals(plan.currency())) {
      violations.add(
          new Violation(
              "CURRENCY_NOT_SUPPORTED", "/plan/currency", "Only KES is supported (D15)."));
    }
    if (plan.sale().to() != null && plan.sale().to().isBefore(plan.sale().from())) {
      violations.add(
          new Violation("SALE_PERIOD_INVALID", "/plan/sale/to", "The sale ends before it starts."));
    }
    checkEligibility(plan.eligibility(), violations);
    Set<String> benefitCodes = checkModules(plan, violations);
    checkWaitingRules(plan.waitingRules(), benefitCodes, violations);
    checkCharges(plan, violations);
    return violations;
  }

  private static void checkEligibility(Eligibility eligibility, List<Violation> violations) {
    String at = "/plan/eligibility";
    checkRange(eligibility.principalAge(), at + "/principalAge", violations);
    if (eligibility.spouseAge() != null) {
      checkRange(eligibility.spouseAge(), at + "/spouseAge", violations);
    }
    ChildAge child = eligibility.childAge();
    if (child != null) {
      checkRange(new AgeRange(child.min(), child.max()), at + "/childAge", violations);
      if (child.maxIfStudent() != null && child.maxIfStudent() < child.max()) {
        violations.add(
            new Violation(
                "AGE_RANGE_INVALID",
                at + "/childAge/maxIfStudent",
                "The student age limit is below the normal child age limit."));
      }
    } else if (eligibility.allowsFamily() && eligibility.maxChildren() > 0) {
      violations.add(
          new Violation(
              "CHILD_AGE_MISSING", at, "Family cover with children needs a childAge range."));
    }
  }

  private static void checkRange(AgeRange range, String at, List<Violation> violations) {
    if (range.min() > range.max()) {
      violations.add(new Violation("AGE_RANGE_INVALID", at, "The range starts after it ends."));
    }
  }

  /** Checks modules, options, benefit trees and ratings; returns every benefit code in the plan. */
  private Set<String> checkModules(PlanDefinition plan, List<Violation> violations) {
    Set<String> allBenefits = new HashSet<>();
    if (plan.modules().stream().noneMatch(ModuleDef::mandatory)) {
      violations.add(
          new Violation("NO_MANDATORY_MODULE", "/plan/modules", "No module is mandatory."));
    }
    Set<String> moduleCodes = new HashSet<>();
    for (int m = 0; m < plan.modules().size(); m++) {
      ModuleDef module = plan.modules().get(m);
      String moduleAt = "/plan/modules/" + m;
      duplicate(moduleCodes, module.code(), moduleAt + "/code", violations);
      Set<String> optionCodes = new HashSet<>();
      for (int o = 0; o < module.options().size(); o++) {
        ModuleOption option = module.options().get(o);
        String optionAt = moduleAt + "/options/" + o;
        duplicate(optionCodes, option.code(), optionAt + "/code", violations);
        Set<String> treeCodes = new HashSet<>();
        checkBenefit(option.benefits(), null, optionAt + "/benefits", treeCodes, violations);
        allBenefits.addAll(treeCodes);
        violations.addAll(ratings.validate(option.rating(), plan, optionAt + "/rating"));
      }
    }
    return allBenefits;
  }

  /**
   * Walks one benefit tree. {@code limitedAncestor} is the nearest ancestor with a limit, the one a
   * child's limit is compared with (D32).
   */
  private static void checkBenefit(
      BenefitNode node,
      Limit limitedAncestor,
      String at,
      Set<String> treeCodes,
      List<Violation> violations) {
    duplicate(treeCodes, node.code(), at + "/code", violations);
    if (node.kind() == BenefitKind.FIXED_SUM) {
      violations.add(
          new Violation(
              "UNSUPPORTED_FEATURE",
              at + "/kind",
              "FIXED_SUM benefits are not supported in v1 (D18)."));
    }
    Limit limit = node.limit();
    if (limit != null) {
      checkLimit(limit, limitedAncestor, at + "/limit", violations);
    }
    Limit forChildren = limit != null ? limit : limitedAncestor;
    for (int c = 0; c < node.children().size(); c++) {
      checkBenefit(
          node.children().get(c), forChildren, at + "/children/" + c, treeCodes, violations);
    }
  }

  private static void checkLimit(
      Limit limit, Limit ancestor, String at, List<Violation> violations) {
    if (limit.period() == LimitPeriod.PER_CLAIM && limit.maxCount() != null) {
      violations.add(
          new Violation(
              "PER_CLAIM_WITH_MAX_COUNT",
              at + "/maxCount",
              "A PER_CLAIM limit has no running total, so it cannot count claims (D32)."));
    }
    if (limit.maxPerVisit() != null && limit.maxPerVisit().compareTo(limit.amount()) > 0) {
      violations.add(
          new Violation(
              "MAX_PER_VISIT_EXCEEDS_LIMIT",
              at + "/maxPerVisit",
              "The per-visit cap is above the limit."));
    }
    if (ancestor == null) {
      return;
    }
    if (limit.amount().compareTo(ancestor.amount()) > 0) {
      violations.add(
          new Violation(
              "CHILD_LIMIT_EXCEEDS_PARENT",
              at + "/amount",
              "A child limit is above its parent's limit of " + ancestor.amount() + "."));
    }
    if (ancestor.scope() == LimitScope.PER_MEMBER && limit.scope() == LimitScope.SHARED) {
      violations.add(
          new Violation(
              "INVALID_SCOPE_NESTING",
              at + "/scope",
              "A SHARED limit cannot sit under a PER_MEMBER limit (D32)."));
    }
  }

  private static void checkWaitingRules(
      List<WaitingRule> rules, Set<String> benefitCodes, List<Violation> violations) {
    Set<String> codes = new HashSet<>();
    for (int r = 0; r < rules.size(); r++) {
      WaitingRule rule = rules.get(r);
      String at = "/plan/waitingRules/" + r;
      duplicate(codes, rule.code(), at + "/code", violations);
      List<String> benefits = rule.when().benefits();
      for (int b = 0; b < benefits.size(); b++) {
        if (!benefitCodes.contains(benefits.get(b))) {
          violations.add(
              new Violation(
                  "UNKNOWN_WAITING_BENEFIT",
                  at + "/when/benefits/" + b,
                  "No benefit " + benefits.get(b) + " exists in this plan."));
        }
      }
    }
  }

  private static void checkCharges(PlanDefinition plan, List<Violation> violations) {
    Set<String> codes = new HashSet<>();
    for (int i = 0; i < plan.adjustments().size(); i++) {
      Charge charge = plan.adjustments().get(i);
      duplicate(codes, charge.code(), "/plan/adjustments/" + i + "/code", violations);
    }
    for (int i = 0; i < plan.levies().size(); i++) {
      Charge charge = plan.levies().get(i);
      duplicate(codes, charge.code(), "/plan/levies/" + i + "/code", violations);
    }
  }

  private static void duplicate(
      Set<String> seen, String code, String at, List<Violation> violations) {
    if (!seen.add(code)) {
      violations.add(new Violation("DUPLICATE_CODE", at, "The code " + code + " is repeated."));
    }
  }
}
