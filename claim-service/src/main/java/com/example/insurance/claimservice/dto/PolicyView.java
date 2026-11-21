package com.example.insurance.claimservice.dto;

import com.example.insurance.claimservice.domain.PolicySnapshot;
import com.example.insurance.events.policy.PolicyActivated;
import com.example.insurance.events.policy.TermsSnapshot;
import com.example.insurance.messaging.EventJson;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A policy as a customer sees it for a claim: its status, cover dates, members and frozen terms.
 */
@Schema(description = "A policy the customer holds, with the terms it was sold with.")
public record PolicyView(
    UUID policyId,
    @Schema(example = "POL-ACACIA-2026-000123") String policyNumber,
    UUID insurerId,
    @Schema(example = "ACTIVE") String status,
    LocalDate coverStart,
    LocalDate coverEnd,
    LocalDate cancelEffectiveDate,
    long policyVersion,
    List<PolicyActivated.MemberTerms> members,
    TermsSnapshot termsSnapshot) {

  public PolicyView {
    members = members == null ? List.of() : List.copyOf(members);
  }

  /** From the read model; the terms come from the stored activation payload. */
  public static PolicyView of(PolicySnapshot snapshot) {
    PolicyActivated activation =
        snapshot.getActivation() == null
            ? null
            : EventJson.read(snapshot.getActivation(), PolicyActivated.class);
    return new PolicyView(
        snapshot.getPolicyId(),
        snapshot.getPolicyNumber(),
        snapshot.getInsurerId(),
        snapshot.getStatus().name(),
        snapshot.getCoverStart(),
        snapshot.getCoverEnd(),
        snapshot.getCancelEffectiveDate(),
        snapshot.getPolicyVersion(),
        activation == null ? List.of() : activation.members(),
        activation == null ? null : activation.terms());
  }

  /** From policy-service's answer to the fallback call. */
  public static PolicyView of(PolicyLookup lookup) {
    return new PolicyView(
        lookup.policyId(),
        lookup.policyNumber(),
        lookup.insurerId(),
        lookup.status(),
        lookup.coverStart(),
        lookup.coverEnd(),
        null,
        lookup.policyVersion(),
        lookup.members(),
        lookup.termsSnapshot());
  }
}
