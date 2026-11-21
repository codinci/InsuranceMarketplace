package com.example.insurance.paymentservice.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MockPaymentProviderTest {

  private final MockPaymentProvider provider = new MockPaymentProvider();

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
}
