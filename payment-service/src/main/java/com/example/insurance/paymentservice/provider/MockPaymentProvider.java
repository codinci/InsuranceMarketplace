package com.example.insurance.paymentservice.provider;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Stands in for a card or mobile-money provider (D3, D12). It is not a real payment and moves no
 * money. The customer can ask it to decline, so the failure path can be tested through the API.
 */
@Component
public class MockPaymentProvider {

  /** The provider's answer to one charge. */
  public record Charge(boolean succeeded, String providerRef, String failureReason) {}

  public static final String DECLINED = "PROVIDER_DECLINED";

  public Charge charge(BigDecimal amount, String currency, boolean decline) {
    if (decline) {
      return new Charge(false, null, DECLINED);
    }
    return new Charge(true, "mock-" + UUID.randomUUID(), null);
  }
}
