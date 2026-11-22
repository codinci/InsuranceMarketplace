package com.example.insurance.paymentservice.provider;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stands in for a card or mobile-money provider (D3, D12). It is not a real payment and moves no
 * money. A customer can ask it to decline a charge, so the premium failure path can be tested
 * through the API. A payout has no request to carry that choice, so the payees it declines come
 * from configuration instead (M7): empty by default, set only by tests and the smoke run.
 */
@Component
public class MockPaymentProvider {

  /** The provider's answer to one charge or payout. */
  public record Charge(boolean succeeded, String providerRef, String failureReason) {}

  public static final String DECLINED = "PROVIDER_DECLINED";

  private final Set<UUID> decliningPayees;

  public MockPaymentProvider(
      @Value("${payments.mock.payout-decline-payees:}") List<UUID> decliningPayees) {
    this.decliningPayees = Set.copyOf(decliningPayees);
  }

  public Charge charge(BigDecimal amount, String currency, boolean decline) {
    if (decline) {
      return new Charge(false, null, DECLINED);
    }
    return new Charge(true, "mock-" + UUID.randomUUID(), null);
  }

  /** Pays {@code amount} to the payee. The mock treats the holder's user id as the payee. */
  public Charge payout(UUID payee, BigDecimal amount, String currency) {
    if (decliningPayees.contains(payee)) {
      return new Charge(false, null, DECLINED);
    }
    return new Charge(true, "mock-payout-" + UUID.randomUUID(), null);
  }
}
