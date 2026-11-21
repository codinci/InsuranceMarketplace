package com.example.insurance.claimservice.statemachine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.insurance.claimservice.domain.ClaimStatus;
import com.example.insurance.claimservice.statemachine.ClaimStateMachine.InvalidTransitionException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The status machine, checked against an explicit table of every pair of statuses, written from
 * PLAN 7 and ADR-016 rather than read back from the implementation.
 */
class ClaimStateMachineTest {

  private static final Map<ClaimStatus, Set<ClaimStatus>> EXPECTED =
      Map.of(
          ClaimStatus.DRAFT, Set.of(ClaimStatus.SUBMITTED, ClaimStatus.WITHDRAWN),
          ClaimStatus.SUBMITTED,
              Set.of(ClaimStatus.UNDER_REVIEW, ClaimStatus.REJECTED, ClaimStatus.WITHDRAWN),
          ClaimStatus.UNDER_REVIEW,
              Set.of(ClaimStatus.INFO_REQUESTED, ClaimStatus.REJECTED, ClaimStatus.APPROVED),
          ClaimStatus.INFO_REQUESTED, Set.of(ClaimStatus.UNDER_REVIEW, ClaimStatus.WITHDRAWN),
          ClaimStatus.APPROVED, Set.of(ClaimStatus.PAYOUT_PENDING),
          ClaimStatus.PAYOUT_PENDING, Set.of(ClaimStatus.PAID, ClaimStatus.PAYOUT_FAILED),
          ClaimStatus.PAYOUT_FAILED, Set.of(ClaimStatus.PAYOUT_PENDING),
          ClaimStatus.REJECTED, Set.of(),
          ClaimStatus.PAID, Set.of(),
          ClaimStatus.WITHDRAWN, Set.of());

  @Test
  void everyPairOfStatusesIsAllowedExactlyWhenTheTableSaysSo() {
    for (ClaimStatus from : ClaimStatus.values()) {
      for (ClaimStatus to : ClaimStatus.values()) {
        assertThat(ClaimStateMachine.canMove(from, to))
            .as("%s to %s", from, to)
            .isEqualTo(EXPECTED.get(from).contains(to));
      }
    }
  }

  @Test
  void theTableCoversEveryStatus() {
    assertThat(EXPECTED.keySet()).containsExactlyInAnyOrder(ClaimStatus.values());
  }

  @Test
  void aTerminalStatusHasNoWayOut() {
    for (ClaimStatus terminal :
        EnumSet.of(ClaimStatus.REJECTED, ClaimStatus.PAID, ClaimStatus.WITHDRAWN)) {
      for (ClaimStatus to : ClaimStatus.values()) {
        assertThat(ClaimStateMachine.canMove(terminal, to)).isFalse();
      }
    }
  }

  @Test
  void moveReturnsTheTargetWhenAllowed() {
    assertThat(ClaimStateMachine.move(ClaimStatus.DRAFT, ClaimStatus.SUBMITTED))
        .isEqualTo(ClaimStatus.SUBMITTED);
  }

  @Test
  void moveRefusesAForbiddenChangeWithAMessageThatNamesBothStatuses() {
    assertThatThrownBy(() -> ClaimStateMachine.move(ClaimStatus.DRAFT, ClaimStatus.PAID))
        .isInstanceOf(InvalidTransitionException.class)
        .hasMessageContaining("DRAFT")
        .hasMessageContaining("PAID");
  }
}
