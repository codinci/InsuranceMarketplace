package com.example.insurance.policyservice.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.insurance.events.EventTypes;
import com.example.insurance.events.claim.ClaimApproved;
import com.example.insurance.events.claim.ClaimSubmitted;
import com.example.insurance.policyservice.policy.messaging.Topics;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/**
 * policy-service counts claims per policy from {@code claim.events}, with no call to claim-service
 * (M8b, ADR-021). Each claim counts once, however often it is announced.
 */
class ClaimCounterIT extends AbstractPolicyFlowIT {

  @Autowired JdbcTemplate jdbc;

  private UUID policyId;

  @BeforeEach
  void aPolicy() throws Exception {
    PAYMENTS.up();
    saveProfile(customer, PRINCIPAL_DOB, "MALE");
    MvcResult result = purchase(customer, purchaseBody(openQuote(customer), PRINCIPAL_DOB, true));
    policyId = UUID.fromString(read(result, "$.id"));
  }

  @Test
  void aClaimCountsOnceWhetherRedeliveredOrAnnouncedAgain() throws Exception {
    UUID claimId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();

    submitted(eventId, claimId);
    submitted(eventId, claimId);
    submitted(UUID.randomUUID(), claimId);
    UUID second = UUID.randomUUID();
    submitted(UUID.randomUUID(), second);
    awaitClaim(second);

    assertThat(claims()).isEqualTo(2);
  }

  @Test
  void otherClaimEventsAndUnknownPoliciesChangeNothing() throws Exception {
    publish(
        Topics.CLAIM_EVENTS,
        policyId.toString(),
        EventTypes.CLAIM_APPROVED,
        UUID.randomUUID(),
        insurerId,
        new ClaimApproved(
            UUID.randomUUID(),
            "CLM-TEST-2",
            policyId,
            customerId,
            "m1",
            "OP-CONSULT",
            new BigDecimal("1200.00"),
            "KES",
            Instant.now()));
    UUID strayClaim = UUID.randomUUID();
    publish(
        Topics.CLAIM_EVENTS,
        policyId.toString(),
        EventTypes.CLAIM_SUBMITTED,
        UUID.randomUUID(),
        insurerId,
        claim(strayClaim, UUID.randomUUID()));
    UUID marker = UUID.randomUUID();
    submitted(UUID.randomUUID(), marker);
    awaitClaim(marker);

    assertThat(claims()).as("only the marker claim").isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from policy_claims where claim_id = ?", Integer.class, strayClaim))
        .isZero();
  }

  private void submitted(UUID eventId, UUID claimId) throws Exception {
    publish(
        Topics.CLAIM_EVENTS,
        policyId.toString(),
        EventTypes.CLAIM_SUBMITTED,
        eventId,
        insurerId,
        claim(claimId, policyId));
  }

  private ClaimSubmitted claim(UUID claimId, UUID policy) {
    return new ClaimSubmitted(
        claimId,
        "CLM-TEST-1",
        policy,
        customerId,
        "m1",
        "OUTPATIENT",
        "OP-CONSULT",
        "ILLNESS",
        TODAY,
        new BigDecimal("1500.00"),
        "KES",
        Instant.now());
  }

  /**
   * Every message in these tests uses the policy id as its key, so they share a partition and are
   * applied in order: once the last one has landed, the earlier ones have been applied too.
   */
  private void awaitClaim(UUID claimId) throws InterruptedException {
    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    while (System.nanoTime() < deadline) {
      Integer found =
          jdbc.queryForObject(
              "select count(*) from policy_claims where claim_id = ?", Integer.class, claimId);
      if (found != null && found == 1) {
        return;
      }
      Thread.sleep(200);
    }
    throw new AssertionError("Claim " + claimId + " was never counted.");
  }

  private int claims() {
    Integer count =
        jdbc.queryForObject(
            "select count(*) from policy_claims where policy_id = ?", Integer.class, policyId);
    return count == null ? 0 : count;
  }
}
