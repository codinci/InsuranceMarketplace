package com.example.insurance.claimservice.statemachine;

import com.example.insurance.claimservice.domain.ClaimStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The claim status machine (PLAN 7). A status changes only through {@link #move}, and the caller
 * always writes a {@code ClaimStatusHistory} row with the change (CLAUDE.md).
 *
 * <p>SUBMITTED to REJECTED is the automatic rejection by the hard checks (PLAN 9, ADR-016). It is
 * not in the PLAN 7 diagram, which predates it.
 */
public final class ClaimStateMachine {

  private static final Map<ClaimStatus, Set<ClaimStatus>> ALLOWED =
      new EnumMap<>(ClaimStatus.class);

  static {
    allow(ClaimStatus.DRAFT, ClaimStatus.SUBMITTED, ClaimStatus.WITHDRAWN);
    allow(
        ClaimStatus.SUBMITTED,
        ClaimStatus.UNDER_REVIEW,
        ClaimStatus.REJECTED,
        ClaimStatus.WITHDRAWN);
    allow(
        ClaimStatus.UNDER_REVIEW,
        ClaimStatus.INFO_REQUESTED,
        ClaimStatus.REJECTED,
        ClaimStatus.APPROVED);
    allow(ClaimStatus.INFO_REQUESTED, ClaimStatus.UNDER_REVIEW, ClaimStatus.WITHDRAWN);
    allow(ClaimStatus.APPROVED, ClaimStatus.PAYOUT_PENDING);
    allow(ClaimStatus.PAYOUT_PENDING, ClaimStatus.PAID, ClaimStatus.PAYOUT_FAILED);
    allow(ClaimStatus.PAYOUT_FAILED, ClaimStatus.PAYOUT_PENDING);
  }

  private ClaimStateMachine() {
    // Static rules only; not instantiable.
  }

  private static void allow(ClaimStatus from, ClaimStatus... to) {
    ALLOWED.put(from, EnumSet.copyOf(List.of(to)));
  }

  /** True if the change from {@code from} to {@code to} is in the machine. */
  public static boolean canMove(ClaimStatus from, ClaimStatus to) {
    return ALLOWED.getOrDefault(from, Set.of()).contains(to);
  }

  /** The new status, or {@link InvalidTransitionException} if the change is not allowed. */
  public static ClaimStatus move(ClaimStatus from, ClaimStatus to) {
    if (!canMove(from, to)) {
      throw new InvalidTransitionException(from, to);
    }
    return to;
  }

  /** Thrown for any change the machine does not allow; the service maps it to 409. */
  public static final class InvalidTransitionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidTransitionException(ClaimStatus from, ClaimStatus to) {
      super("A claim cannot move from " + from + " to " + to + ".");
    }
  }
}
