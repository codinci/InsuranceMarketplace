package com.example.insurance.policyservice.catalogue.domain;

import java.math.BigDecimal;

/**
 * An adjustment or a levy (PLAN 5.3, 8.1). A PERCENT value is a fraction (D29); a negative value is
 * a discount.
 */
public record Charge(String code, AmountType type, BigDecimal value) {}
