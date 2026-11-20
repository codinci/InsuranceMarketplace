package com.example.insurance.policyservice.catalogue.service;

import com.example.insurance.events.policy.TermsSnapshot;
import com.example.insurance.policyservice.catalogue.api.CatalogueApi;
import com.example.insurance.policyservice.catalogue.api.CatalogueApiException;
import com.example.insurance.policyservice.catalogue.api.CoverType;
import com.example.insurance.policyservice.catalogue.api.EligibilityProblem;
import com.example.insurance.policyservice.catalogue.api.MemberInput;
import com.example.insurance.policyservice.catalogue.api.PlanForSale;
import com.example.insurance.policyservice.catalogue.api.QuotedPrice;
import com.example.insurance.policyservice.catalogue.domain.Insurer;
import com.example.insurance.policyservice.catalogue.domain.PlanDocument;
import com.example.insurance.policyservice.catalogue.domain.PlanStatus;
import com.example.insurance.policyservice.catalogue.domain.PlanVersion;
import com.example.insurance.policyservice.catalogue.domain.Relationship;
import com.example.insurance.policyservice.catalogue.domain.Sex;
import com.example.insurance.policyservice.catalogue.rating.PremiumBreakdown;
import com.example.insurance.policyservice.catalogue.rating.PremiumCalculator;
import com.example.insurance.policyservice.catalogue.rating.RatedMember;
import com.example.insurance.policyservice.catalogue.rating.RatingException;
import com.example.insurance.policyservice.catalogue.repository.InsurerRepository;
import com.example.insurance.policyservice.catalogue.repository.PlanVersionRepository;
import com.example.insurance.policyservice.catalogue.rules.EligibilityChecker;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The catalogue side of the {@link CatalogueApi} boundary (ADR-001). */
@Service
public class CatalogueApiService implements CatalogueApi {

  private final PlanVersionRepository plans;
  private final InsurerRepository insurers;
  private final StoredPlans stored;
  private final EligibilityChecker eligibility;
  private final PremiumCalculator calculator;

  public CatalogueApiService(
      PlanVersionRepository plans,
      InsurerRepository insurers,
      StoredPlans stored,
      EligibilityChecker eligibility,
      PremiumCalculator calculator) {
    this.plans = plans;
    this.insurers = insurers;
    this.stored = stored;
    this.eligibility = eligibility;
    this.calculator = calculator;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<PlanForSale> planOnSale(UUID planId, LocalDate day) {
    return plans
        .findById(planId)
        .filter(plan -> plan.onSale(day))
        .map(plan -> {
          Insurer insurer = insurers.findById(plan.getInsurerId()).orElseThrow();
          return new PlanForSale(
              plan.getId(),
              insurer.getId(),
              insurer.getCode(),
              plan.getCode(),
              plan.getPlanVersion(),
              plan.getCurrency());
        });
  }

  @Override
  @Transactional(readOnly = true)
  public List<EligibilityProblem> checkEligibility(
      UUID planId, CoverType coverType, List<MemberInput> members, LocalDate startDate) {
    PlanDocument document = soldDocument(planId);
    return eligibility
        .check(
            document.plan(),
            com.example.insurance.policyservice.catalogue.domain.CoverType.valueOf(
                coverType.name()),
            members.stream()
                .map(m ->
                    new EligibilityChecker.Applicant(
                        Relationship.valueOf(m.relationship().name()),
                        Sex.valueOf(m.sex().name()),
                        m.dateOfBirth(),
                        m.student()))
                .toList(),
            startDate)
        .stream()
        .map(p -> new EligibilityProblem(p.code(), p.memberIndex(), p.message()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public QuotedPrice price(
      UUID planId, Map<String, String> selection, List<MemberInput> members, LocalDate startDate) {
    PlanDocument document = soldDocument(planId);
    List<RatedMember> rated =
        members.stream()
            .map(m ->
                new RatedMember(
                    Relationship.valueOf(m.relationship().name()),
                    Sex.valueOf(m.sex().name()),
                    m.dateOfBirth()))
            .toList();
    PremiumBreakdown breakdown;
    try {
      breakdown = calculator.price(document.plan(), selection, rated, startDate);
    } catch (RatingException e) {
      throw new CatalogueApiException(e.code(), e.getMessage());
    }
    return new QuotedPrice(
        breakdown.modules().stream()
            .map(m -> new QuotedPrice.ModuleAmount(m.module(), m.option(), m.amount()))
            .toList(),
        breakdown.subtotal(),
        breakdown.adjustments().stream()
            .map(a -> new QuotedPrice.ChargeAmount(a.code(), a.amount()))
            .toList(),
        breakdown.adjustedSubtotal(),
        breakdown.levies().stream()
            .map(l -> new QuotedPrice.ChargeAmount(l.code(), l.amount()))
            .toList(),
        breakdown.annualPremium(),
        breakdown.currency());
  }

  @Override
  @Transactional(readOnly = true)
  public TermsSnapshot termsSnapshot(UUID planId, Map<String, String> selection) {
    return TermsSnapshots.of(planId, soldDocument(planId), selection);
  }

  /** A published or retired version; a draft was never on sale, so it is not found. */
  private PlanDocument soldDocument(UUID planId) {
    PlanVersion plan =
        plans
            .findById(planId)
            .filter(p -> p.getStatus() != PlanStatus.DRAFT)
            .orElseThrow(() ->
                new CatalogueApiException(CatalogueApiException.PLAN_NOT_FOUND, "No such plan."));
    return stored.document(plan);
  }
}
