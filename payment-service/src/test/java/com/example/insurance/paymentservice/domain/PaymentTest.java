package com.example.insurance.paymentservice.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentTest {

  private static final Instant NOW = Instant.parse("2026-11-21T09:00:00Z");
  private static final UUID POLICY = UUID.fromString("0f8fad5b-d9cb-469f-a165-8c45e88e4a01");

  private Payment newPayment() {
    return Payment.create(
        POLICY.toString(),
        POLICY,
        "POL-ACACIA-2026-000001",
        UUID.randomUUID(),
        UUID.randomUUID(),
        new BigDecimal("12500.00"),
        "KES",
        NOW);
  }

  @Test
  void aNewPaymentWaitsForTheCustomer() {
    Payment payment = newPayment();

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    assertThat(payment.isPayable()).isTrue();
    assertThat(payment.getReferenceType()).isEqualTo(Payment.REFERENCE_POLICY);
  }

  @Test
  void aSuccessfulChargeMarksThePaymentPaidAndKeepsTheProviderReference() {
    Payment payment = newPayment();

    payment.recordSuccess("mock-1", NOW);

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    assertThat(payment.getProviderRef()).isEqualTo("mock-1");
    assertThat(payment.isPayable()).isFalse();
  }

  @Test
  void aFailedPaymentCanBePaidAgain() {
    Payment payment = newPayment();
    payment.recordFailure(NOW);

    assertThat(payment.isPayable()).isTrue();
    payment.recordSuccess("mock-2", NOW);

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
  }

  @Test
  void aPaidPaymentCannotFailOrExpire() {
    Payment payment = newPayment();
    payment.recordSuccess("mock-1", NOW);

    assertThatThrownBy(() -> payment.recordFailure(NOW)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> payment.expire(NOW)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void anExpiredPaymentCannotBePaid() {
    Payment payment = newPayment();
    payment.expire(NOW);

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
    assertThatThrownBy(() -> payment.recordSuccess("mock-3", NOW))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aRepeatedCreationIsTheSameRequestOnlyWhenPolicyAmountAndCurrencyMatch() {
    Payment payment = newPayment();

    assertThat(payment.sameRequestAs(POLICY, new BigDecimal("12500"), "KES")).isTrue();
    assertThat(payment.sameRequestAs(POLICY, new BigDecimal("12500.01"), "KES")).isFalse();
    assertThat(payment.sameRequestAs(UUID.randomUUID(), new BigDecimal("12500.00"), "KES"))
        .isFalse();
    assertThat(payment.sameRequestAs(POLICY, new BigDecimal("12500.00"), "USD")).isFalse();
  }
}
