package com.example.insurance.policyservice.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.insurance.policyservice.policy.domain.Policy;
import com.example.insurance.policyservice.policy.domain.PolicyStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The policy's own state rules (PLAN 7): only a policy awaiting payment can move on. */
class PolicyLifecycleTest {

  @Test
  void aNewPolicyAwaitsPaymentAtVersionOne() {
    Policy policy = PolicyFixtures.pendingPolicy();

    assertThat(policy.getStatus()).isEqualTo(PolicyStatus.PENDING_PAYMENT);
    assertThat(policy.getPolicyVersion()).isEqualTo(1);
  }

  @Test
  void activationMakesItActiveAndBumpsTheVersion() {
    Policy policy = PolicyFixtures.pendingPolicy();

    policy.activate();

    assertThat(policy.getStatus()).isEqualTo(PolicyStatus.ACTIVE);
    assertThat(policy.getPolicyVersion()).isEqualTo(2);
  }

  @Test
  void anActivePolicyCannotBeActivatedOrCancelledForNonPayment() {
    Policy policy = PolicyFixtures.pendingPolicy();
    policy.activate();

    assertThatThrownBy(policy::activate).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(policy::cancelUnpaid).isInstanceOf(IllegalStateException.class);
    assertThat(policy.getPolicyVersion()).isEqualTo(2);
  }

  @Test
  void anUnpaidCancellationIsFinal() {
    Policy policy = PolicyFixtures.pendingPolicy();

    policy.cancelUnpaid();

    assertThat(policy.getStatus()).isEqualTo(PolicyStatus.CANCELLED);
    assertThat(policy.getPolicyVersion()).isEqualTo(2);
    assertThatThrownBy(policy::activate).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void thePaymentLinkIsSetOnce() {
    Policy policy = PolicyFixtures.pendingPolicy();
    UUID payment = UUID.randomUUID();

    policy.attachPayment(payment);
    policy.attachPayment(payment);

    assertThat(policy.getPaymentId()).isEqualTo(payment);
    assertThatThrownBy(() -> policy.attachPayment(UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class);
  }
}
