package com.example.insurance.claimservice.service;

import com.example.insurance.claimservice.config.ReviewProperties;
import com.example.insurance.claimservice.domain.AssessmentResult;
import com.example.insurance.claimservice.domain.Claim;
import com.example.insurance.claimservice.domain.ClaimAssessment;
import com.example.insurance.claimservice.domain.ClaimDocument;
import com.example.insurance.claimservice.domain.ClaimStatus;
import com.example.insurance.claimservice.domain.ClaimStatusHistory;
import com.example.insurance.claimservice.dto.ClaimDetails;
import com.example.insurance.claimservice.dto.CreateClaimRequest;
import com.example.insurance.claimservice.dto.PolicyView;
import com.example.insurance.claimservice.exception.ApiException;
import com.example.insurance.claimservice.exception.ErrorCode;
import com.example.insurance.claimservice.messaging.Topics;
import com.example.insurance.claimservice.repository.ClaimAssessmentRepository;
import com.example.insurance.claimservice.repository.ClaimDocumentRepository;
import com.example.insurance.claimservice.repository.ClaimRepository;
import com.example.insurance.claimservice.repository.ClaimStatusHistoryRepository;
import com.example.insurance.claimservice.rules.Assessment;
import com.example.insurance.claimservice.rules.HardChecks;
import com.example.insurance.claimservice.rules.SoftFlags;
import com.example.insurance.claimservice.statemachine.ClaimStateMachine;
import com.example.insurance.events.EventTypes;
import com.example.insurance.events.claim.ClaimSubmitted;
import com.example.insurance.events.policy.PolicyActivated.MemberTerms;
import com.example.insurance.events.policy.TermsSnapshot;
import com.example.insurance.messaging.EventJson;
import com.example.insurance.messaging.OutboxWriter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The customer's claims (PLAN 7, 9, M5b). Every method starts from the caller's own claim, so
 * another customer's claim is simply not found. Status changes go through {@link ClaimStateMachine}
 * and are always written to the history (CLAUDE.md).
 */
@Service
public class ClaimService {

  static final String ACTOR_CUSTOMER = "CUSTOMER";
  static final String ACTOR_SYSTEM = "SYSTEM";

  private final ClaimRepository claims;
  private final ClaimStatusHistoryRepository history;
  private final ClaimAssessmentRepository assessments;
  private final ClaimDocumentRepository documents;
  private final PolicyReadService policies;
  private final OutboxWriter outbox;
  private final Clock clock;
  private final ZoneId businessZone;
  private final ClaimLookup lookup;
  private final ReviewProperties review;

  public ClaimService(
      ClaimRepository claims,
      ClaimStatusHistoryRepository history,
      ClaimAssessmentRepository assessments,
      ClaimDocumentRepository documents,
      PolicyReadService policies,
      OutboxWriter outbox,
      Clock clock,
      @Value("${claim.business-zone}") ZoneId businessZone,
      ClaimLookup lookup,
      ReviewProperties review) {
    this.claims = claims;
    this.history = history;
    this.assessments = assessments;
    this.documents = documents;
    this.policies = policies;
    this.outbox = outbox;
    this.clock = clock;
    this.businessZone = businessZone;
    this.lookup = lookup;
    this.review = review;
  }

  /** Starts a DRAFT. The policy must be one the caller holds; the rest is checked at submission. */
  @Transactional
  public Claim create(UUID userId, CreateClaimRequest request) {
    PolicyView policy = policies.get(userId, request.policyId());
    TermsSnapshot terms = usableTerms(policy);
    Instant now = clock.instant();
    Claim claim =
        Claim.draft(
            UUID.randomUUID(),
            claimNumber(terms.insurerCode(), now),
            policy.insurerId(),
            policy.policyId(),
            userId,
            request.claim().toDomain(),
            terms.currency(),
            now);
    claims.save(claim);
    recordChange(claim, null, ClaimStatus.DRAFT, userId, ACTOR_CUSTOMER, "created", now);
    return claim;
  }

  /** Changes a DRAFT's details. A submitted claim is no longer the customer's to edit. */
  @Transactional
  public Claim update(UUID userId, UUID claimId, ClaimDetails details) {
    Claim claim = owned(userId, claimId);
    if (!claim.isDraft()) {
      throw new ApiException(ErrorCode.CLAIM_NOT_EDITABLE, "Only a draft can be changed.");
    }
    claim.editDetails(details.toDomain(), clock.instant());
    return claim;
  }

  /**
   * Runs the hard checks (PLAN 9). A claim that passes is SUBMITTED and announced with
   * ClaimSubmitted. A claim that fails is REJECTED at once, with the first failing rule as the
   * reason. Either way the checks are stored.
   */
  @Transactional
  public Claim submit(UUID userId, UUID claimId) {
    Claim claim = owned(userId, claimId);
    if (!ClaimStateMachine.canMove(claim.getStatus(), ClaimStatus.SUBMITTED)) {
      throw new ApiException(ErrorCode.INVALID_TRANSITION, "Only a draft can be submitted.");
    }
    PolicyView policy = policies.get(userId, claim.getPolicyId());
    usableTerms(policy);
    if (policy.coverStart() == null || policy.coverEnd() == null) {
      throw new ApiException(
          ErrorCode.POLICY_UNUSABLE, "The policy's cover dates are not available.");
    }

    List<String> documentTypes =
        documents.findByClaimId(claim.getId()).stream().map(ClaimDocument::getDocType).toList();
    LocalDate today = LocalDate.now(clock.withZone(businessZone));
    List<Assessment> results =
        HardChecks.evaluate(claimFacts(claim, documentTypes), policyFacts(policy), today);

    Instant now = clock.instant();
    ClaimStatus fromDraft = claim.moveTo(ClaimStatus.SUBMITTED, now);
    recordChange(claim, fromDraft, ClaimStatus.SUBMITTED, userId, ACTOR_CUSTOMER, "submitted", now);
    claim.markSubmitted(now);
    for (Assessment result : results) {
      assessments.save(
          new ClaimAssessment(
              claim.getId(), result.ruleCode(), result.result(), result.detail(), now));
    }

    if (HardChecks.accepted(results)) {
      raiseSoftFlags(claim, policy, now);
      outbox.record(
          Topics.CLAIM_EVENTS,
          EventTypes.CLAIM_SUBMITTED,
          ClaimSubmitted.VERSION,
          claim.getId().toString(),
          claim.getInsurerId().toString(),
          new ClaimSubmitted(
              claim.getId(),
              claim.getClaimNumber(),
              claim.getPolicyId(),
              claim.getHolderUserId(),
              claim.getMemberId(),
              claim.getModuleCode(),
              claim.getBenefitCode(),
              claim.getCause().name(),
              claim.getIncidentDate(),
              claim.getAmountClaimed(),
              claim.getCurrency(),
              now));
    } else {
      String reason = firstFailure(results);
      ClaimStatus fromSubmitted = claim.moveTo(ClaimStatus.REJECTED, now);
      recordChange(claim, fromSubmitted, ClaimStatus.REJECTED, userId, ACTOR_SYSTEM, reason, now);
      claim.rejectWith(reason);
    }
    return claim;
  }

  /** The customer withdraws a claim that is still open (DRAFT or SUBMITTED). */
  @Transactional
  public Claim withdraw(UUID userId, UUID claimId) {
    Claim claim = owned(userId, claimId);
    Instant now = clock.instant();
    ClaimStatus from = claim.moveTo(ClaimStatus.WITHDRAWN, now);
    recordChange(
        claim,
        from,
        ClaimStatus.WITHDRAWN,
        userId,
        ACTOR_CUSTOMER,
        "withdrawn by the customer",
        now);
    return claim;
  }

  /**
   * The customer answers an information request (PLAN 7, M6): INFO_REQUESTED back to UNDER_REVIEW.
   * They may add documents while the claim waits for them.
   */
  @Transactional
  public Claim respond(UUID userId, UUID claimId) {
    Claim claim = owned(userId, claimId);
    Instant now = clock.instant();
    ClaimStatus from = claim.moveTo(ClaimStatus.UNDER_REVIEW, now);
    recordChange(
        claim, from, ClaimStatus.UNDER_REVIEW, userId, ACTOR_CUSTOMER, "customer responded", now);
    return claim;
  }

  /** The claim for its customer, or for staff of its insurer; anyone else gets 404. */
  @Transactional(readOnly = true)
  public Claim get(Actor actor, UUID claimId) {
    return lookup.visible(actor, claimId);
  }

  @Transactional(readOnly = true)
  public Page<Claim> list(UUID userId, Pageable pageable) {
    return claims.findByHolderUserId(userId, pageable);
  }

  @Transactional(readOnly = true)
  public Page<ClaimStatusHistory> history(Actor actor, UUID claimId, Pageable pageable) {
    lookup.visible(actor, claimId);
    return history.findByClaimId(claimId, pageable);
  }

  /** The checks of a claim, in the order they ran. Empty while it is a draft. */
  @Transactional(readOnly = true)
  public List<ClaimAssessment> assessmentsOf(UUID claimId) {
    return assessments.findByClaimIdOrderByIdAsc(claimId);
  }

  /** The claim, for the owner only; anyone else gets the same answer as for a missing claim. */
  Claim owned(UUID userId, UUID claimId) {
    return lookup.ownedBy(userId, claimId);
  }

  /**
   * Raises the soft flags of a claim that passed the hard checks (PLAN 9.1). A flag never rejects;
   * it is stored as a FLAG check and marks the claim for the top of the review queue.
   */
  private void raiseSoftFlags(Claim claim, PolicyView policy, Instant now) {
    Set<ClaimStatus> ignored = EnumSet.of(ClaimStatus.DRAFT, ClaimStatus.WITHDRAWN);
    long duplicates =
        claims.countPossibleDuplicates(
            claim.getPolicyId(),
            claim.getMemberId(),
            claim.getBenefitCode(),
            claim.getProviderName(),
            claim.getIncidentDate(),
            claim.getAmountClaimed(),
            claim.getId(),
            ignored);
    long recent =
        claims.countRecentClaims(
            claim.getPolicyId(),
            claim.getMemberId(),
            claim.getId(),
            now.minus(Duration.ofDays(review.frequentClaimsDays())),
            ignored);
    List<Assessment> flags =
        SoftFlags.evaluate(
            new SoftFlags.Facts(
                claim.getAmountClaimed(),
                claim.getIncidentDate(),
                policy.coverStart(),
                duplicates,
                recent),
            new SoftFlags.Thresholds(
                review.highAmountFlag(), review.earlyCoverDays(), review.frequentClaimsCount()));
    for (Assessment flag : flags) {
      assessments.save(
          new ClaimAssessment(claim.getId(), flag.ruleCode(), flag.result(), flag.detail(), now));
    }
    if (!flags.isEmpty()) {
      claim.markFlagged();
    }
  }

  private void recordChange(
      Claim claim,
      ClaimStatus from,
      ClaimStatus to,
      UUID actor,
      String actorRole,
      String reason,
      Instant now) {
    history.save(new ClaimStatusHistory(claim.getId(), from, to, actor, actorRole, reason, now));
  }

  private static TermsSnapshot usableTerms(PolicyView policy) {
    TermsSnapshot terms = policy.termsSnapshot();
    if (terms == null || terms.insurerCode() == null || terms.currency() == null) {
      throw new ApiException(ErrorCode.POLICY_UNUSABLE, "The policy's terms are not available.");
    }
    return terms;
  }

  private String claimNumber(String insurerCode, Instant now) {
    int year = ZonedDateTime.ofInstant(now, businessZone).getYear();
    return String.format("CLM-%s-%d-%06d", insurerCode, year, claims.nextClaimNumber());
  }

  private static HardChecks.ClaimFacts claimFacts(Claim claim, List<String> documentTypes) {
    String[] tags = EventJson.read(claim.getConditionTags(), String[].class);
    return new HardChecks.ClaimFacts(
        claim.getMemberId(),
        claim.getModuleCode(),
        claim.getBenefitCode(),
        claim.getCause(),
        claim.isSurgical(),
        tags == null ? Set.of() : Set.of(tags),
        claim.getIncidentDate(),
        claim.getAmountClaimed(),
        Set.copyOf(documentTypes));
  }

  private HardChecks.PolicyFacts policyFacts(PolicyView policy) {
    return new HardChecks.PolicyFacts(
        policy.status(),
        policy.coverStart(),
        policy.coverEnd(),
        policy.cancelEffectiveDate(),
        policy.members().stream()
            .map(MemberTerms::memberNumber)
            .collect(Collectors.toUnmodifiableSet()),
        policy.termsSnapshot());
  }

  private static String firstFailure(List<Assessment> results) {
    return results.stream()
        .filter(result -> result.result() == AssessmentResult.FAIL)
        .map(Assessment::ruleCode)
        .findFirst()
        .orElseThrow();
  }
}
