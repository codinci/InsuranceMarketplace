package com.example.insurance.policyservice.catalogue.domain;

import java.time.LocalDate;

/** When a plan version can be sold. {@code to} is open-ended when null. */
public record SalePeriod(LocalDate from, LocalDate to) {}
