package com.example.insurance.policyservice.policy;

import com.example.insurance.policyservice.catalogue.api.CoverType;
import com.example.insurance.policyservice.policy.domain.Policy;
import com.example.insurance.policyservice.policy.domain.Quote;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A policy awaiting payment, built without the database, for unit tests. */
public final class PolicyFixtures {

  private PolicyFixtures() {}

  public static Policy pendingPolicy() {
    Instant now = Instant.parse("2026-11-21T08:00:00Z");
    Quote quote =
        Quote.open(
            new Quote.Terms(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ACACIA",
                UUID.randomUUID(),
                "STANDARD",
                1,
                CoverType.INDIVIDUAL,
                LocalDate.of(2026, 12, 1),
                "[]",
                "{}",
                new BigDecimal("12500.00"),
                "{}",
                "KES"),
            now,
            now.plusSeconds(3600));
    return Policy.pendingPayment(quote, "POL-ACACIA-2026-000001", "{}", "{}", now);
  }
}
