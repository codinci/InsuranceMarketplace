package com.example.insurance.policyservice.catalogue.domain;

/** The cancellation refund model (D7). */
public record Refund(String model, Integer noRefundAfterMonths, Boolean noRefundIfClaims) {}
