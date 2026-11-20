package com.example.insurance.policyservice.catalogue.domain;

import java.math.BigDecimal;

/** A co-pay on a benefit node. A PERCENT value is a fraction (D29). */
public record Copay(CopayType type, BigDecimal value) {}
