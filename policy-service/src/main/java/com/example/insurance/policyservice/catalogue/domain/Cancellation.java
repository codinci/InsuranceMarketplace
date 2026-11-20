package com.example.insurance.policyservice.catalogue.domain;

/** The cancellation rule (PLAN 5.3). */
public record Cancellation(int noticeDays, Refund refund) {}
