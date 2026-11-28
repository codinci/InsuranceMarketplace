package com.example.insurance.policyservice.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.insurance.policyservice.policy.domain.Policy;
import com.example.insurance.policyservice.policy.domain.PolicyStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
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
  void anActivePolicyCanBeCancelledByItsHolderWithARefund() {
    Policy policy = PolicyFixtures.pendingPolicy();
    policy.activate();
    LocalDate effective = LocalDate.of(2027, 1, 31);

    policy.cancelByCustomer(effective, new BigDecimal("33500.00"));

    assertThat(policy.getStatus()).isEqualTo(PolicyStatus.CANCELLED);
    assertThat(policy.getCancelEffectiveDate()).isEqualTo(effective);
    assertThat(policy.getCancelReason()).isEqualTo("CUSTOMER");
    assertThat(policy.getRefundAmount()).isEqualByComparingTo("33500.00");
    assertThat(policy.getPolicyVersion()).isEqualTo(3);
  }

  @Test
  void anUnpaidPolicyCanBeCancelledByItsHolder() {
    Policy policy = PolicyFixtures.pendingPolicy();

    policy.cancelByCustomer(LocalDate.of(2026, 12, 1), BigDecimal.ZERO);

    assertThat(policy.getStatus()).isEqualTo(PolicyStatus.CANCELLED);
    assertThat(policy.getPolicyVersion()).isEqualTo(2);
  }

  @Test
  void aCancelledPolicyCannotBeCancelledAgainOrExpired() {
    Policy policy = PolicyFixtures.pendingPolicy();
    policy.cancelUnpaid();

    assertThat(policy.getCancelReason()).isEqualTo("UNPAID");
    assertThatThrownBy(() -> policy.cancelByCustomer(LocalDate.of(2026, 12, 1), BigDecimal.ZERO))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(policy::expire).isInstanceOf(IllegalStateException.class);
    assertThat(policy.getPolicyVersion()).isEqualTo(2);
  }

  @Test
  void anActivePolicyExpiresOnceAndThenIsFinal() {
    Policy policy = PolicyFixtures.pendingPolicy();
    assertThatThrownBy(policy::expire).isInstanceOf(IllegalStateException.class);
    policy.activate();

    policy.expire();

    assertThat(policy.getStatus()).isEqualTo(PolicyStatus.EXPIRED);
    assertThat(policy.isActive()).isFalse();
    assertThat(policy.getPolicyVersion()).isEqualTo(3);
    assertThatThrownBy(policy::expire).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> policy.cancelByCustomer(LocalDate.of(2027, 1, 1), BigDecimal.ZERO))
        .isInstanceOf(IllegalStateException.class);
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
