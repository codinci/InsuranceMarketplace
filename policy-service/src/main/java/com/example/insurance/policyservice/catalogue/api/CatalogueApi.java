package com.example.insurance.policyservice.catalogue.api;

import com.example.insurance.events.policy.TermsSnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything the {@code policy} package may ask of the catalogue (ADR-001). Only this package's
 * types cross the boundary, so the catalogue can later move to its own service by replacing this
 * interface with HTTP calls. Failures are {@link CatalogueApiException} with a stable code.
 */
public interface CatalogueApi {

  /** The plan, if it is published and on sale on {@code day}. */
  Optional<PlanForSale> planOnSale(UUID planId, LocalDate day);

  /** Every eligibility problem of these members on this plan; empty when they are eligible. */
  List<EligibilityProblem> checkEligibility(
      UUID planId, CoverType coverType, List<MemberInput> members, LocalDate startDate);

  /** The server-side premium (PLAN 8.1). {@code selection} maps module code to option code. */
  QuotedPrice price(
      UUID planId, Map<String, String> selection, List<MemberInput> members, LocalDate startDate);

  /**
   * The terms of the selected options, frozen for a policy (ADR-011). Works for a published or a
   * retired version, because a quote stays valid for 30 days after the plan was retired.
   */
  TermsSnapshot termsSnapshot(UUID planId, Map<String, String> selection);
}
