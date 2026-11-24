package com.example.insurance.claimservice.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.insurance.events.policy.PolicyActivated;
import com.example.insurance.events.policy.PolicyCancelled;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The ordering rules of the policy read model (PLAN 11, M5a): in order, duplicate, cancel before
 * activate, and a stale activate after a cancel. Pure domain logic, no Spring.
 */
class PolicySnapshotTest {

  private static final UUID POLICY = UUID.fromString("0a1b2c3d-0000-4000-8000-000000000001");
  private static final UUID INSURER = UUID.fromString("0a1b2c3d-0000-4000-8000-0000000000aa");
  private static final UUID HOLDER = UUID.fromString("0a1b2c3d-0000-4000-8000-0000000000bb");
  private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");
  private static final String ACTIVATION_JSON = "{\"policyId\":\"" + POLICY + "\"}";

  @Test
  void activationThenCancellationInOrderLeavesACancelledPolicy() {
    PolicySnapshot snapshot = PolicySnapshot.unseen(POLICY, "POL-1", INSURER);

    assertThat(snapshot.applyActivated(activated(2), INSURER, ACTIVATION_JSON, NOW)).isTrue();
    assertThat(snapshot.getStatus()).isEqualTo(SnapshotStatus.ACTIVE);
    assertThat(snapshot.isHeldBy(HOLDER)).isTrue();

    assertThat(snapshot.applyCancelled(cancelled(3), INSURER, NOW)).isTrue();
    assertThat(snapshot.getStatus()).isEqualTo(SnapshotStatus.CANCELLED);
    assertThat(snapshot.getCancelEffectiveDate()).isEqualTo(LocalDate.of(2026, 12, 1));
    assertThat(snapshot.getPolicyVersion()).isEqualTo(3);
  }

  @Test
  void aRedeliveredActivationChangesNothing() {
    PolicySnapshot snapshot = PolicySnapshot.unseen(POLICY, "POL-1", INSURER);
    snapshot.applyActivated(activated(2), INSURER, ACTIVATION_JSON, NOW);

    assertThat(snapshot.applyActivated(activated(2), INSURER, ACTIVATION_JSON, NOW)).isFalse();
    assertThat(snapshot.getPolicyVersion()).isEqualTo(2);
  }

  @Test
  void aCancellationThatArrivesBeforeTheActivationWinsOverIt() {
    PolicySnapshot snapshot = PolicySnapshot.unseen(POLICY, "POL-1", INSURER);
    assertThat(snapshot.applyCancelled(cancelled(2), INSURER, NOW)).isTrue();

    assertThat(snapshot.applyActivated(activated(2), INSURER, ACTIVATION_JSON, NOW)).isFalse();
    assertThat(snapshot.getStatus()).isEqualTo(SnapshotStatus.CANCELLED);
    assertThat(snapshot.getHolderUserId()).isNull();
    assertThat(snapshot.getActivation()).isNull();
  }

  @Test
  void aStaleActivationAfterACancellationIsIgnored() {
    PolicySnapshot snapshot = PolicySnapshot.unseen(POLICY, "POL-1", INSURER);
    snapshot.applyActivated(activated(2), INSURER, ACTIVATION_JSON, NOW);
    snapshot.applyCancelled(cancelled(3), INSURER, NOW);

    assertThat(snapshot.applyActivated(activated(2), INSURER, ACTIVATION_JSON, NOW)).isFalse();
    assertThat(snapshot.getStatus()).isEqualTo(SnapshotStatus.CANCELLED);
    assertThat(snapshot.getPolicyVersion()).isEqualTo(3);
  }

  @Test
  void aCustomerOtherThanTheHolderIsNotAnOwner() {
    PolicySnapshot snapshot = PolicySnapshot.unseen(POLICY, "POL-1", INSURER);
    snapshot.applyActivated(activated(2), INSURER, ACTIVATION_JSON, NOW);

    assertThat(snapshot.isHeldBy(UUID.randomUUID())).isFalse();
    assertThat(snapshot.isHeldBy(null)).isFalse();
  }

  private static PolicyActivated activated(long version) {
    return new PolicyActivated(
        POLICY,
        "POL-1",
        version,
        HOLDER,
        LocalDate.of(2026, 12, 1),
        LocalDate.of(2027, 11, 30),
        List.of(),
        null);
  }

  private static PolicyCancelled cancelled(long version) {
    return new PolicyCancelled(
        POLICY, "POL-1", UUID.randomUUID(), version, "UNPAID", LocalDate.of(2026, 12, 1));
  }
}
