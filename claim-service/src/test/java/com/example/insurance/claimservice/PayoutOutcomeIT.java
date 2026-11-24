package com.example.insurance.claimservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.example.insurance.claimservice.repository.ClaimReservationRepository;
import com.example.insurance.claimservice.support.FakeDocumentStorageConfig;
import com.example.insurance.claimservice.support.TestSecurityConfig;
import com.example.insurance.claimservice.support.TestTokens;
import com.example.insurance.events.EventEnvelope;
import com.example.insurance.events.EventTypes;
import com.example.insurance.events.payment.PayoutFailed;
import com.example.insurance.events.payment.PayoutSucceeded;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The claim side of the payout saga (PLAN 10, M7): approval leaves the claim waiting for its
 * payout, and the payment events move it to PAID or PAYOUT_FAILED through the state machine, once
 * each, with a history row for every step and the limit reservation kept while a payout is failed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestSecurityConfig.class, FakeDocumentStorageConfig.class})
class PayoutOutcomeIT extends AbstractReviewIT {

  @Autowired ClaimReservationRepository reservations;

  /** A claim approved by an adjuster of {@link #INSURER}: it waits for its payout. */
  private record Approved(UUID holder, String customer, String staff, String claimId) {}

  private Approved approvedClaim() throws Exception {
    UUID holder = UUID.randomUUID();
    String customer = TestTokens.customer(holder);
    String staff = adjuster(UUID.randomUUID());
    String claimId = underReview(customer, policy(holder, unlimited()), "1500.00", staff);
    MvcResult approved = approve(staff, claimId, Map.of());
    assertThat(approved.getResponse().getStatus()).isEqualTo(200);
    return new Approved(holder, customer, staff, claimId);
  }

  private void publishPayment(UUID eventId, String type, UUID claimId, Object payload)
      throws Exception {
    String value =
        json.writeValueAsString(
            new EventEnvelope<>(
                eventId,
                type,
                1,
                Instant.now(),
                claimId.toString(),
                INSURER.toString(),
                "it-test",
                payload));
    kafka.send("payment.events", claimId.toString(), value).get(10, TimeUnit.SECONDS);
  }

  private static PayoutSucceeded succeeded(Approved claim) {
    return new PayoutSucceeded(
        UUID.randomUUID(),
        UUID.fromString(claim.claimId()),
        "CLM-TEST",
        claim.holder(),
        new BigDecimal("1500.00"),
        "KES",
        "mock-payout-1",
        Instant.now());
  }

  private static PayoutFailed failed(Approved claim) {
    return new PayoutFailed(
        UUID.randomUUID(),
        UUID.fromString(claim.claimId()),
        "CLM-TEST",
        claim.holder(),
        new BigDecimal("1500.00"),
        "KES",
        "PROVIDER_DECLINED",
        Instant.now());
  }

  private void awaitClaim(Approved claim, String status) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    String current = null;
    while (System.nanoTime() < deadline) {
      current = read(claimAs(claim.customer(), claim.claimId()), "$.status");
      if (status.equals(current)) {
        return;
      }
      Thread.sleep(200);
    }
    throw new AssertionError("Claim " + claim.claimId() + " stayed " + current + ", not " + status);
  }

  private MvcResult history(Approved claim) throws Exception {
    return as(claim.customer(), get("/api/v1/claims/{id}/history", claim.claimId()));
  }

  @Test
  void approvalLeavesTheClaimWaitingForItsPayoutAndASuccessfulPayoutMakesItPaid() throws Exception {
    Approved claim = approvedClaim();
    assertThat(read(claimAs(claim.customer(), claim.claimId()), "$.status").toString())
        .isEqualTo("PAYOUT_PENDING");

    UUID eventId = UUID.randomUUID();
    publishPayment(
        eventId, EventTypes.PAYOUT_SUCCEEDED, UUID.fromString(claim.claimId()), succeeded(claim));
    awaitClaim(claim, "PAID");

    MvcResult history = history(claim);
    assertThat(this.<Integer>read(history, "$.totalElements")).isEqualTo(6);
    assertThat(this.<String>read(history, "$.content[5].from")).isEqualTo("PAYOUT_PENDING");
    assertThat(this.<String>read(history, "$.content[5].to")).isEqualTo("PAID");
    assertThat(this.<String>read(history, "$.content[5].actorRole")).isEqualTo("SERVICE");

    // The same event delivered again changes nothing.
    publishPayment(
        eventId, EventTypes.PAYOUT_SUCCEEDED, UUID.fromString(claim.claimId()), succeeded(claim));
    Thread.sleep(1500);
    assertThat(this.<Integer>read(history(claim), "$.totalElements")).isEqualTo(6);
  }

  @Test
  void aFailedPayoutKeepsTheReservationAndARetrySucceedingPassesThroughPending() throws Exception {
    Approved claim = approvedClaim();
    UUID claimId = UUID.fromString(claim.claimId());
    int reserved = reservations.findByClaimIdAndReleasedAtIsNull(claimId).size();

    publishPayment(UUID.randomUUID(), EventTypes.PAYOUT_FAILED, claimId, failed(claim));
    awaitClaim(claim, "PAYOUT_FAILED");

    // Staff can find it, and only staff of this insurer.
    String listed =
        as(claim.staff(), get("/api/v1/claims/queue?status=PAYOUT_FAILED&size=100"))
            .getResponse()
            .getContentAsString();
    assertThat(listed).contains(claim.claimId());
    String otherInsurer = TestTokens.adjuster(UUID.randomUUID(), UUID.randomUUID());
    assertThat(
            as(otherInsurer, get("/api/v1/claims/queue?status=PAYOUT_FAILED&size=100"))
                .getResponse()
                .getContentAsString())
        .doesNotContain(claim.claimId());
    // The money is still owed, so the limit stays reserved.
    assertThat(reservations.findByClaimIdAndReleasedAtIsNull(claimId)).hasSize(reserved);

    // Failing again is not a second failure: the claim is already PAYOUT_FAILED.
    publishPayment(UUID.randomUUID(), EventTypes.PAYOUT_FAILED, claimId, failed(claim));
    // The retry succeeds: PAYOUT_FAILED -> PAYOUT_PENDING -> PAID.
    publishPayment(UUID.randomUUID(), EventTypes.PAYOUT_SUCCEEDED, claimId, succeeded(claim));
    awaitClaim(claim, "PAID");

    MvcResult history = history(claim);
    assertThat(this.<Integer>read(history, "$.totalElements")).isEqualTo(8);
    assertThat(this.<String>read(history, "$.content[5].to")).isEqualTo("PAYOUT_FAILED");
    assertThat(this.<String>read(history, "$.content[6].to")).isEqualTo("PAYOUT_PENDING");
    assertThat(this.<String>read(history, "$.content[7].to")).isEqualTo("PAID");
    assertThat(reservations.findByClaimIdAndReleasedAtIsNull(claimId)).hasSize(reserved);
  }

  @Test
  void aPaidClaimIgnoresALateFailureEvent() throws Exception {
    Approved claim = approvedClaim();
    UUID claimId = UUID.fromString(claim.claimId());
    publishPayment(UUID.randomUUID(), EventTypes.PAYOUT_SUCCEEDED, claimId, succeeded(claim));
    awaitClaim(claim, "PAID");

    publishPayment(UUID.randomUUID(), EventTypes.PAYOUT_FAILED, claimId, failed(claim));
    // A later event on the same key is consumed after the failure, so waiting for it is enough.
    Approved other = approvedClaim();
    publishPayment(
        UUID.randomUUID(),
        EventTypes.PAYOUT_SUCCEEDED,
        UUID.fromString(other.claimId()),
        succeeded(other));
    awaitClaim(other, "PAID");

    assertThat(read(claimAs(claim.customer(), claim.claimId()), "$.status").toString())
        .isEqualTo("PAID");
  }
}
