package com.example.insurance.policyservice.catalogue.api;

import java.util.UUID;

/** A plan version that can be quoted. */
public record PlanForSale(
    UUID planId,
    UUID insurerId,
    String insurerCode,
    String planCode,
    int planVersion,
    String currency) {}
