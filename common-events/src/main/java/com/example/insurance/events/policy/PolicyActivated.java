package com.example.insurance.events.policy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Payload of {@code PolicyActivated} on {@code policy.events} (PLAN 10, ADR-011). Sent once the
 * premium is paid. It carries the frozen terms so claim-service can build its read model without
 * calling policy-service (PLAN 8.2, D9 in the claim path).
 *
 * @param policyId the policy
 * @param policyNumber human-readable policy number
 * @param policyVersion increases with every change to this policy; consumers ignore older versions
 * @param holderUserId the customer who owns the policy
 * @param coverStart first day of cover
 * @param coverEnd last day of cover
 * @param members the insured members, fixed at quote time (D36)
 * @param terms the terms snapshot, interpreted generically by claim-service
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PolicyActivated(
    UUID policyId,
    String policyNumber,
    long policyVersion,
    UUID holderUserId,
    LocalDate coverStart,
    LocalDate coverEnd,
    List<MemberTerms> members,
    TermsSnapshot terms) {

  /** Payload schema version for the envelope's {@code version} field. */
  public static final int VERSION = 1;

  public PolicyActivated {
    members = members == null ? List.of() : List.copyOf(members);
  }

  /** One insured member as sold. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record MemberTerms(
      String memberNumber,
      String relationship,
      String sex,
      String firstName,
      String lastName,
      LocalDate dateOfBirth,
      boolean student) {}
}
