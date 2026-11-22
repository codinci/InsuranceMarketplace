package com.example.insurance.claimservice.service;

import com.example.insurance.claimservice.adjudication.LimitEvaluator.RunningLimit;
import com.example.insurance.claimservice.adjudication.LimitEvaluator.Usage;
import com.example.insurance.claimservice.domain.BenefitUtilisation;
import com.example.insurance.claimservice.domain.ClaimReservation;
import com.example.insurance.claimservice.repository.BenefitUtilisationRepository;
import com.example.insurance.claimservice.repository.ClaimReservationRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The running totals against a policy's limits (PLAN 9.2, 11). Approval reads the totals,
 * evaluates, and reserves the approved amount in one transaction. Each total row has a version, so
 * two approvals that read the same total cannot both save it: the second fails and the caller
 * retries with the new total. {@link #release} gives a reservation back exactly, for M7's failed
 * payouts.
 */
@Service
public class UtilisationService {

  private final BenefitUtilisationRepository totals;
  private final ClaimReservationRepository reservations;
  private final Clock clock;

  public UtilisationService(
      BenefitUtilisationRepository totals, ClaimReservationRepository reservations, Clock clock) {
    this.totals = totals;
    this.reservations = reservations;
    this.clock = clock;
  }

  /** The totals as they stand, without creating any row. Used for the payable preview. */
  @Transactional(readOnly = true)
  public Map<String, Usage> peek(
      List<RunningLimit> limits, UUID policyId, int policyYear, String memberId) {
    Map<String, Usage> usage = new HashMap<>();
    for (RunningLimit limit : limits) {
      usage.put(
          limit.benefitCode(),
          totals
              .findKey(policyId, limit.benefitCode(), policyYear, keyMember(limit, memberId))
              .map(row -> new Usage(row.getAmountUsed(), row.getCountUsed()))
              .orElse(Usage.NONE));
    }
    return usage;
  }

  /**
   * The total rows for the limits, created at zero when a limit has not been used yet. A concurrent
   * creation breaks the unique key; the caller retries, and the second attempt finds the row.
   */
  @Transactional
  public List<BenefitUtilisation> rowsFor(
      List<RunningLimit> limits, UUID policyId, int policyYear, String memberId) {
    List<BenefitUtilisation> rows = new ArrayList<>();
    for (RunningLimit limit : limits) {
      String member = keyMember(limit, memberId);
      rows.add(
          totals
              .findKey(policyId, limit.benefitCode(), policyYear, member)
              .orElseGet(() ->
                  totals.saveAndFlush(
                      BenefitUtilisation.start(
                          policyId, member, limit.benefitCode(), policyYear))));
    }
    return rows;
  }

  /**
   * Adds the approved amount to every total and records what was reserved, so it can be released.
   */
  @Transactional
  public void reserve(
      UUID claimId, List<RunningLimit> limits, List<BenefitUtilisation> rows, BigDecimal approved) {
    Instant now = clock.instant();
    for (int i = 0; i < limits.size(); i++) {
      BenefitUtilisation row = rows.get(i);
      boolean counted = limits.get(i).countsVisits();
      row.reserve(approved, counted);
      totals.save(row);
      reservations.save(new ClaimReservation(claimId, row.getId(), approved, counted, now));
    }
  }

  /**
   * Gives back everything this claim reserved: the amounts and the visits. Safe to call twice: a
   * reservation already released is skipped. Returns how many reservations were released. M7 calls
   * it when a payout fails for good.
   */
  @Transactional
  public int release(UUID claimId) {
    Instant now = clock.instant();
    int released = 0;
    for (ClaimReservation reservation : reservations.findByClaimIdAndReleasedAtIsNull(claimId)) {
      BenefitUtilisation row = totals.findById(reservation.getUtilisationId()).orElseThrow();
      row.release(reservation.getAmount(), reservation.isCounted());
      totals.save(row);
      reservation.markReleased(now);
      released++;
    }
    return released;
  }

  private static String keyMember(RunningLimit limit, String memberId) {
    return limit.perMember() ? memberId : null;
  }
}
