package com.example.insurance.policyservice.catalogue.domain;

import java.util.List;

/**
 * One node of a benefit tree (PLAN 5.3). A node without a limit is covered and limited only by its
 * ancestors. {@code preAuthRequired} is stored only (D2).
 */
public record BenefitNode(
    String code,
    BenefitKind kind,
    Limit limit,
    Copay copay,
    List<String> requiredDocuments,
    Boolean preAuthRequired,
    List<BenefitNode> children) {

  public BenefitNode {
    kind = kind == null ? BenefitKind.REIMBURSEMENT : kind;
    requiredDocuments = requiredDocuments == null ? List.of() : List.copyOf(requiredDocuments);
    preAuthRequired = preAuthRequired != null && preAuthRequired;
    children = children == null ? List.of() : List.copyOf(children);
  }
}
