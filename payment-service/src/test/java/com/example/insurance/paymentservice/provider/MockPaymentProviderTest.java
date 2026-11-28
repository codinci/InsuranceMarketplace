package com.example.insurance.paymentservice.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MockPaymentProviderTest {

  private final MockPaymentProvider provider = new MockPaymentProvider(List.of(), List.of());

  @Test
  void byDefaultTheChargeSucceedsWithAReference() {
    MockPaymentProvider.Charge charge = provider.charge(new BigDecimal("100.00"), "KES", false);

    assertThat(charge.succeeded()).isTrue();
    assertThat(charge.providerRef()).startsWith("mock-");
    assertThat(charge.failureReason()).isNull();
  }

  @Test
  void aDeclineFailsWithAStableReason() {
    MockPaymentProvider.Charge charge = provider.charge(new BigDecimal("100.00"), "KES", true);

    assertThat(charge.succeeded()).isFalse();
    assertThat(charge.failureReason()).isEqualTo(MockPaymentProvider.DECLINED);
    assertThat(charge.providerRef()).isNull();
  }

  @Test
  void aPayoutSucceedsWithAPayoutReferenceByDefault() {
    MockPaymentProvider.Charge payout =
        provider.payout(UUID.randomUUID(), new BigDecimal("1200.00"), "KES");

    assertThat(payout.succeeded()).isTrue();
    assertThat(payout.providerRef()).startsWith("mock-payout-");
    assertThat(payout.failureReason()).isNull();
  }

  @Test
  void aPayoutToAConfiguredPayeeIsDeclinedAndOthersAreNot() {
    UUID declined = UUID.randomUUID();
    MockPaymentProvider configured = new MockPaymentProvider(List.of(declined), List.of());

    MockPaymentProvider.Charge refused = configured.payout(declined, new BigDecimal("1.00"), "KES");
    MockPaymentProvider.Charge paid =
        configured.payout(UUID.randomUUID(), new BigDecimal("1.00"), "KES");

    assertThat(refused.succeeded()).isFalse();
    assertThat(refused.failureReason()).isEqualTo(MockPaymentProvider.DECLINED);
    assertThat(paid.succeeded()).isTrue();
  }

  @Test
  void aRefundSucceedsByDefaultAndIsDeclinedOnlyForAConfiguredHolder() {
    UUID declined = UUID.randomUUID();
    MockPaymentProvider configured = new MockPaymentProvider(List.of(), List.of(declined));

    MockPaymentProvider.Charge refused = configured.refund(declined, new BigDecimal("1.00"), "KES");
    MockPaymentProvider.Charge refunded =
        configured.refund(UUID.randomUUID(), new BigDecimal("1.00"), "KES");

    assertThat(refused.succeeded()).isFalse();
    assertThat(refused.failureReason()).isEqualTo(MockPaymentProvider.DECLINED);
    assertThat(refunded.succeeded()).isTrue();
    assertThat(refunded.providerRef()).startsWith("mock-refund-");
    assertThat(configured.payout(declined, BigDecimal.ONE, "KES").succeeded())
        .as("a refund decline does not decline payouts")
        .isTrue();
  }
}
