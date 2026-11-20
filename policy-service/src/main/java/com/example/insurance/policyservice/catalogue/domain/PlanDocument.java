package com.example.insurance.policyservice.catalogue.domain;

/** A whole plan document, version 1 of the schema (PLAN 5.1). */
public record PlanDocument(
    int schemaVersion, Party insurer, Product product, PlanDefinition plan) {}
