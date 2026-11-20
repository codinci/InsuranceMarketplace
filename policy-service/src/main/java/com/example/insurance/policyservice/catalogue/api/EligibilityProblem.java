package com.example.insurance.policyservice.catalogue.api;

/**
 * One reason the members cannot be covered. {@code memberIndex} points into the request's member
 * list, or is null when the problem is about the whole group (docs/catalogue-schema.md).
 */
public record EligibilityProblem(String code, Integer memberIndex, String message) {}
