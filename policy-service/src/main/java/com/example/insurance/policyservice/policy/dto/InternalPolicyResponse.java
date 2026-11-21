package com.example.insurance.policyservice.policy.dto;

import com.example.insurance.events.policy.PolicyActivated;
import com.example.insurance.events.policy.TermsSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What claim-service reads when its policy read model does not have a policy yet (PLAN 11, M5a). It
 * carries the same frozen terms as {@code PolicyActivated}, so the two sources agree.
 */
@Schema(description = "A policy as claim-service needs it. SERVICE token only, never routed.")
public record InternalPolicyResponse(
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

  public InternalPolicyResponse {
    members = List.copyOf(members);
  }
}
