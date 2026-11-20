package com.example.insurance.policyservice.catalogue.domain;

import java.math.BigDecimal;

/** A limit on a benefit node (PLAN 5.3). {@code maxPerVisit} and {@code maxCount} are optional. */
public record Limit(
    BigDecimal amount,
    LimitPeriod period,
    LimitScope scope,
    BigDecimal maxPerVisit,
    Integer maxCount) {}
