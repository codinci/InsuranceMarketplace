package com.example.insurance.policyservice.policy.service;

import com.example.insurance.events.policy.PolicyActivated;
import com.example.insurance.events.policy.TermsSnapshot;
import com.example.insurance.policyservice.catalogue.api.CatalogueApi;
import com.example.insurance.policyservice.catalogue.api.Relationship;
import com.example.insurance.policyservice.config.BusinessCalendar;
import com.example.insurance.policyservice.exception.ApiException;
import com.example.insurance.policyservice.exception.ErrorCode;
import com.example.insurance.policyservice.policy.domain.Policy;
import com.example.insurance.policyservice.policy.domain.PolicyHolder;
import com.example.insurance.policyservice.policy.domain.PolicyMember;
import com.example.insurance.policyservice.policy.domain.PolicyNumbers;
import com.example.insurance.policyservice.policy.domain.Quote;
import com.example.insurance.policyservice.policy.domain.QuoteStatus;
import com.example.insurance.policyservice.policy.domain.QuotedMember;
import com.example.insurance.policyservice.policy.dto.InternalPolicyResponse;
import com.example.insurance.policyservice.policy.dto.PolicyMemberView;
import com.example.insurance.policyservice.policy.dto.PolicyResponse;
import com.example.insurance.policyservice.policy.dto.PolicySummary;
import com.example.insurance.policyservice.policy.dto.PremiumView;
import com.example.insurance.policyservice.policy.dto.PurchaseMemberRequest;
import com.example.insurance.policyservice.policy.dto.PurchaseRequest;
import com.example.insurance.policyservice.policy.repository.PolicyHolderRepository;
import com.example.insurance.policyservice.policy.repository.PolicyMemberRepository;
import com.example.insurance.policyservice.policy.repository.PolicyRepository;
import com.example.insurance.policyservice.policy.repository.QuoteRepository;
import com.example.insurance.policyservice.web.PageResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Buying a policy from a quote (PLAN 8.2). The premium is the quoted one, the terms are frozen from
 * the catalogue, and the policy waits for payment. payment-service creates the payment inside the
 * purchase; if it cannot, the purchase fails with 503 and nothing is saved (M4b).
 */
@Service
public class PolicyService {

  private final QuoteService quoteService;
  private final QuoteRepository quotes;
  private final PolicyRepository policies;
  private final PolicyMemberRepository members;
  private final PolicyHolderRepository holders;
  private final CatalogueApi catalogue;
  private final Clock clock;
  private final BusinessCalendar calendar;
  private final PaymentClient paymentClient;

  public PolicyService(
      QuoteService quoteService,
      QuoteRepository quotes,
      PolicyRepository policies,
      PolicyMemberRepository members,
      PolicyHolderRepository holders,
      CatalogueApi catalogue,
      Clock clock,
      BusinessCalendar calendar,
      PaymentClient paymentClient) {
    this.quoteService = quoteService;
    this.quotes = quotes;
    this.policies = policies;
    this.members = members;
    this.holders = holders;
    this.catalogue = catalogue;
    this.clock = clock;
    this.calendar = calendar;
    this.paymentClient = paymentClient;
  }

  @Transactional
  public PolicyResponse purchase(UUID userId, PurchaseRequest request) {
    Quote quote = quoteService.own(userId, request.quoteId());
    PolicyHolder holder =
        holders
            .findById(userId)
            .orElseThrow(() ->
                new ApiException(ErrorCode.PROFILE_REQUIRED, "Create your profile before buying."));
    Instant now = clock.instant();
    if (quote.getStatus() != QuoteStatus.OPEN) {
      throw new ApiException(ErrorCode.QUOTE_NOT_OPEN, "A policy was already bought from it.");
    }
    if (quote.expiredAt(now)) {
      throw new ApiException(ErrorCode.QUOTE_EXPIRED, "The quote has expired. Ask for a new one.");
    }
    QuoteService.requireStartInWindow(quote.getStartDate(), calendar.today());
    if (!request.declaration().accepted()) {
      throw new ApiException(ErrorCode.DECLARATION_REQUIRED, "Accept the declaration to buy.");
    }
    List<QuotedMember> quoted = QuoteService.members(quote);
    requireSameMembers(quoted, request.members());
    requirePrincipalIsHolder(quoted, holder);

    TermsSnapshot snapshot =
        catalogue.termsSnapshot(quote.getPlanId(), QuoteService.selection(quote));
    long sequence = policies.nextNumber();
    Policy policy =
        Policy.pendingPayment(
            quote,
            PolicyNumbers.policyNumber(
                quote.getInsurerCode(), calendar.today().getYear(), sequence),
            PolicyJson.write(snapshot),
            PolicyJson.write(declaration(request, now)),
            now);
    quote.markUsed();
    quotes.save(quote);
    policy = policies.save(policy);
    List<PolicyMember> saved = new ArrayList<>();
    for (int i = 0; i < request.members().size(); i++) {
      PurchaseMemberRequest m = request.members().get(i);
      saved.add(
          members.save(
              PolicyMember.of(
                  policy.getId(),
                  i,
                  PolicyNumbers.memberNumber(quote.getInsurerCode(), sequence, i),
                  new PolicyMember.Details(
                      m.relationship(),
                      m.sex(),
                      m.firstName(),
                      m.lastName(),
                      m.dateOfBirth(),
                      m.student()))));
    }
    // Write first, so a constraint failure (a racing purchase of the same quote) stops us before
    // payment-service is called. The remote call comes last, just before commit.
    policies.flush();
    policy.attachPayment(paymentClient.createPremium(policy));
    return toResponse(policy, saved);
  }

  @Transactional(readOnly = true)
  public PageResponse<PolicySummary> list(UUID userId, Pageable pageable) {
    return PageResponse.of(policies.findByHolderUserId(userId, pageable), PolicyService::summary);
  }

  /** The policy as claim-service reads it (ADR-014 caller check is in SecurityConfig). */
  @Transactional(readOnly = true)
  public InternalPolicyResponse internalView(UUID policyId) {
    Policy policy =
        policies
            .findById(policyId)
            .orElseThrow(() -> new ApiException(ErrorCode.POLICY_NOT_FOUND, "No such policy."));
    List<PolicyActivated.MemberTerms> memberTerms =
        members.findByPolicyIdOrderByPosition(policy.getId()).stream()
            .map(m ->
                new PolicyActivated.MemberTerms(
                    m.getMemberNumber(),
                    m.getRelationship().name(),
                    m.getSex().name(),
                    m.getFirstName(),
                    m.getLastName(),
                    m.getDateOfBirth(),
                    m.isStudent()))
            .toList();
    return new InternalPolicyResponse(
        policy.getId(),
        policy.getPolicyNumber(),
        policy.getInsurerId(),
        policy.getHolderUserId(),
        policy.getStatus().name(),
        policy.getStartDate(),
        policy.getEndDate(),
        policy.getPolicyVersion(),
        memberTerms,
        PolicyJson.read(policy.getTermsSnapshot(), TermsSnapshot.class));
  }

  @Transactional(readOnly = true)
  public PolicyResponse get(UUID userId, UUID policyId) {
    Policy policy =
        policies
            .findByIdAndHolderUserId(policyId, userId)
            .orElseThrow(() -> new ApiException(ErrorCode.POLICY_NOT_FOUND, "No such policy."));
    return toResponse(policy, members.findByPolicyIdOrderByPosition(policy.getId()));
  }

  /** Purchase repeats the quoted members exactly, in order; only names are added (D36). */
  private static void requireSameMembers(
      List<QuotedMember> quoted, List<PurchaseMemberRequest> given) {
    List<QuotedMember> asGiven =
        given.stream()
            .map(m -> new QuotedMember(m.relationship(), m.sex(), m.dateOfBirth(), m.student()))
            .toList();
    if (!quoted.equals(asGiven)) {
      throw new ApiException(
          ErrorCode.MEMBERS_DIFFER_FROM_QUOTE,
          "The members must be the quoted members, in the same order. Ask for a new quote.");
    }
  }

  private static void requirePrincipalIsHolder(List<QuotedMember> quoted, PolicyHolder holder) {
    QuotedMember principal =
        quoted.stream()
            .filter(m -> m.relationship() == Relationship.PRINCIPAL)
            .findFirst()
            .orElseThrow();
    if (!principal.dateOfBirth().equals(holder.getDateOfBirth())
        || principal.sex() != holder.getSex()) {
      throw new ApiException(
          ErrorCode.PRINCIPAL_NOT_HOLDER,
          "The principal's date of birth and sex must match your profile.");
    }
  }

  private static Map<String, Object> declaration(PurchaseRequest request, Instant now) {
    Map<String, Object> declaration = new LinkedHashMap<>();
    declaration.put("preExistingConditions", request.declaration().preExistingConditions());
    declaration.put("acceptedAt", now.toString());
    return declaration;
  }

  private static PolicySummary summary(Policy policy) {
    return new PolicySummary(
        policy.getId(),
        policy.getPolicyNumber(),
        policy.getStatus().name(),
        policy.getInsurerCode(),
        policy.getPlanCode(),
        policy.getStartDate(),
        policy.getEndDate(),
        policy.getAnnualPremium(),
        policy.getCurrency());
  }

  /** The customer view of a policy; also used by {@link PolicyCancellationService}. */
  static PolicyResponse toResponse(Policy policy, List<PolicyMember> policyMembers) {
    return new PolicyResponse(
        policy.getId(),
        policy.getPolicyNumber(),
        policy.getStatus().name(),
        policy.getPaymentId(),
        policy.getInsurerCode(),
        policy.getPlanId(),
        policy.getPlanCode(),
        policy.getPlanVersion(),
        policy.getCoverType(),
        policy.getStartDate(),
        policy.getEndDate(),
        PolicyJson.read(policy.getPremiumBreakdown(), PremiumView.class),
        policyMembers.stream()
            .map(m ->
                new PolicyMemberView(
                    m.getMemberNumber(),
                    m.getRelationship(),
                    m.getSex(),
                    m.getFirstName(),
                    m.getLastName(),
                    m.getDateOfBirth(),
                    m.isStudent()))
            .toList(),
        PolicyJson.read(policy.getTermsSnapshot(), TermsSnapshot.class),
        policy.getCancelEffectiveDate(),
        policy.getCancelReason(),
        policy.getRefundAmount());
  }
}
