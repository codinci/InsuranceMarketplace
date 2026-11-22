package com.example.insurance.claimservice.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The review thresholds (PLAN 4, 9.1, D13), configuration and never code. Amounts are KES.
 *
 * @param approvalThreshold an amount above this can be approved only by an ADMIN
 * @param highAmountFlag a claim above this amount is flagged for closer review
 * @param earlyCoverDays an incident within this many days of the cover start is flagged
 * @param frequentClaimsCount a member filing this many claims (including this one) in {@code
 *     frequentClaimsDays} days is flagged
 * @param frequentClaimsDays the window for the frequent-claims flag
 */
@ConfigurationProperties(prefix = "claim.review")
public record ReviewProperties(
    @DefaultValue("50000") BigDecimal approvalThreshold,
    @DefaultValue("100000") BigDecimal highAmountFlag,
    @DefaultValue("30") int earlyCoverDays,
    @DefaultValue("3") int frequentClaimsCount,
    @DefaultValue("30") int frequentClaimsDays) {}
