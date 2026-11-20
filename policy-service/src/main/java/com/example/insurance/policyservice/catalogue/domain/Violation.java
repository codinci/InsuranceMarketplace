package com.example.insurance.policyservice.catalogue.domain;

/**
 * One problem in a plan document. {@code code} is stable and documented in
 * docs/catalogue-schema.md; {@code path} is a JSON Pointer into the document.
 */
public record Violation(String code, String path, String message) {}
