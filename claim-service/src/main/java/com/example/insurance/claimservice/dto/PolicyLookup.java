package com.example.insurance.claimservice.dto;

import com.example.insurance.events.policy.PolicyActivated;
import com.example.insurance.events.policy.TermsSnapshot;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * policy-service's answer to the fallback call (PLAN 11). Only the fields claim-service uses; a
 * field added later is ignored, so the two services can change independently.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PolicyLookup(
    UUID policyId,
    String policyNumber,
    UUID insurerId,
    UUID holderUserId,
    String status,
    LocalDate coverStart,
    LocalDate coverEnd,
    long policyVersion,
    List<PolicyActivated.MemberTerms> members,
    TermsSnapshot termsSnapshot) {

  public PolicyLookup {
    members = members == null ? List.of() : List.copyOf(members);
  }
}
