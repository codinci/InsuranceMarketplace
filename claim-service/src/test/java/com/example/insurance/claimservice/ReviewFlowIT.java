package com.example.insurance.claimservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.insurance.claimservice.support.FakeDocumentStorageConfig;
import com.example.insurance.claimservice.support.TestSecurityConfig;
import com.example.insurance.claimservice.support.TestTokens;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

/**
 * A reviewer's journey (PLAN 7, 9.2, M6): the queue, starting a review, asking for information and
 * the customer's answer, rejecting, approving, the role rules, and the rule that nobody reviews
 * their own claim.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestSecurityConfig.class, FakeDocumentStorageConfig.class})
class ReviewFlowIT extends AbstractReviewIT {

  @Test
  void anAdjusterTakesAClaimThroughReviewToApproval() throws Exception {
    UUID holder = UUID.randomUUID();
    String customer = TestTokens.customer(holder);
    String staff = adjuster(UUID.randomUUID());
    String claimId = submitted(customer, policy(holder, unlimited()), "3000.00");

    MvcResult queue = as(staff, get("/api/v1/claims/queue?size=100"));
    assertThat(queue.getResponse().getContentAsString()).contains(claimId);

    MvcResult started = as(staff, post("/api/v1/claims/{id}/start-review", claimId));
    assertThat(this.<String>read(started, "$.status")).isEqualTo("UNDER_REVIEW");
    assertThat(money(started, "$.amountEligible")).isEqualByComparingTo("3000.00");

    MvcResult approved = approve(staff, claimId, Map.of());
    assertThat(approved.getResponse().getStatus()).isEqualTo(200);
    assertThat(this.<String>read(approved, "$.status")).isEqualTo("APPROVED");
    assertThat(money(approved, "$.amountApproved")).isEqualByComparingTo("3000.00");
    assertThat(outboxTypes()).contains("ClaimApproved");

    MvcResult seenByCustomer = claimAs(customer, claimId);
    assertThat(this.<String>read(seenByCustomer, "$.status")).isEqualTo("APPROVED");
    assertThat(money(seenByCustomer, "$.amountApproved")).isEqualByComparingTo("3000.00");
    // Reviewer-only fields are not shown to the customer.
    assertThat(this.<Object>read(seenByCustomer, "$.flagged")).isNull();
    assertThat(this.<Object>read(seenByCustomer, "$.amountEligible")).isNull();
    assertThat(this.<Object>read(seenByCustomer, "$.reviewerId")).isNull();

    MvcResult history = as(customer, get("/api/v1/claims/{id}/history", claimId));
    assertThat(this.<Integer>read(history, "$.totalElements")).isEqualTo(4);
    assertThat(this.<String>read(history, "$.content[3].to")).isEqualTo("APPROVED");
    assertThat(this.<String>read(history, "$.content[3].actorRole")).isEqualTo("ADJUSTER");
  }

  @Test
  void aClaimAlreadyApprovedCannotBeApprovedAgain() throws Exception {
    UUID holder = UUID.randomUUID();
    String staff = adjuster(UUID.randomUUID());
    String claimId =
        underReview(TestTokens.customer(holder), policy(holder, unlimited()), "1000.00", staff);
    approve(staff, claimId, Map.of());

    assertCode(approve(staff, claimId, Map.of()), 409, "INVALID_TRANSITION");
  }

  @Test
  void reviewCannotStartOnADraft() throws Exception {
    UUID holder = UUID.randomUUID();
    String customer = TestTokens.customer(holder);
    MvcResult draft =
        as(
            customer,
            post("/api/v1/claims")
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        claimBody(policy(holder, unlimited()), "100.00", "Clinic", 10))));
    String claimId = this.<String>read(draft, "$.id");

    assertCode(
        as(adjuster(UUID.randomUUID()), post("/api/v1/claims/{id}/start-review", claimId)),
        409,
        "INVALID_TRANSITION");
  }

  @Test
  void anInformationRequestReachesTheCustomerWhoAnswersAndReturnsItToReview() throws Exception {
    UUID holder = UUID.randomUUID();
    String customer = TestTokens.customer(holder);
    String staff = adjuster(UUID.randomUUID());
    String claimId = underReview(customer, policy(holder, unlimited()), "2000.00", staff);

    MvcResult asked = requestInfo(staff, claimId, "Please attach the discharge summary.");
    assertThat(this.<String>read(asked, "$.status")).isEqualTo("INFO_REQUESTED");
    assertThat(outboxTypes()).contains("ClaimInfoRequested");
    assertThat(this.<String>read(claimAs(customer, claimId), "$.decisionReason"))
        .contains("discharge summary");

    // The customer may add documents while the claim waits for them.
    assertThat(uploadReceipt(customer, claimId).getResponse().getStatus()).isEqualTo(201);
    MvcResult answered = as(customer, post("/api/v1/claims/{id}/respond", claimId));
    assertThat(this.<String>read(answered, "$.status")).isEqualTo("UNDER_REVIEW");

    assertCode(
        as(customer, post("/api/v1/claims/{id}/respond", claimId)), 409, "INVALID_TRANSITION");
  }

  @Test
  void anInformationRequestNeedsAMessage() throws Exception {
    UUID holder = UUID.randomUUID();
    String staff = adjuster(UUID.randomUUID());
    String claimId =
        underReview(TestTokens.customer(holder), policy(holder, unlimited()), "2000.00", staff);

    assertCode(requestInfo(staff, claimId, "  "), 400, "VALIDATION_FAILED");
  }

  @Test
  void aRejectionCarriesTheReviewersReasonToTheCustomer() throws Exception {
    UUID holder = UUID.randomUUID();
    String customer = TestTokens.customer(holder);
    String staff = adjuster(UUID.randomUUID());
    String claimId = underReview(customer, policy(holder, unlimited()), "2000.00", staff);

    MvcResult rejected = reject(staff, claimId, "The invoice does not match the provider.");

    assertThat(this.<String>read(rejected, "$.status")).isEqualTo("REJECTED");
    assertThat(this.<String>read(rejected, "$.rejectionReason")).isEqualTo("REVIEWER_REJECTED");
    assertThat(this.<String>read(claimAs(customer, claimId), "$.decisionReason"))
        .isEqualTo("The invoice does not match the provider.");
    assertThat(outboxTypes()).contains("ClaimRejected");
    assertCode(reject(staff, claimId, "Again"), 409, "INVALID_TRANSITION");
  }

  @Test
  void aRejectionNeedsAReason() throws Exception {
    UUID holder = UUID.randomUUID();
    String staff = adjuster(UUID.randomUUID());
    String claimId =
        underReview(TestTokens.customer(holder), policy(holder, unlimited()), "2000.00", staff);

    assertCode(reject(staff, claimId, ""), 400, "VALIDATION_FAILED");
  }

  @Test
  void aCustomerCannotUseAnyStaffEndpoint() throws Exception {
    UUID holder = UUID.randomUUID();
    String customer = TestTokens.customer(holder);
    String claimId = submitted(customer, policy(holder, unlimited()), "100.00");

    assertThat(as(customer, get("/api/v1/claims/queue")).getResponse().getStatus()).isEqualTo(403);
    assertThat(
            as(customer, post("/api/v1/claims/{id}/start-review", claimId))
                .getResponse()
                .getStatus())
        .isEqualTo(403);
    assertThat(approve(customer, claimId, Map.of()).getResponse().getStatus()).isEqualTo(403);
    assertThat(reject(customer, claimId, "x").getResponse().getStatus()).isEqualTo(403);
    assertThat(requestInfo(customer, claimId, "x").getResponse().getStatus()).isEqualTo(403);
  }

  @Test
  void staffCannotFileOrChangeAClaimAsACustomer() throws Exception {
    UUID holder = UUID.randomUUID();
    String claimId = submitted(TestTokens.customer(holder), policy(holder, unlimited()), "100.00");
    String staff = adjuster(UUID.randomUUID());

    assertThat(
            as(staff, post("/api/v1/claims").contentType("application/json").content("{}"))
                .getResponse()
                .getStatus())
        .isEqualTo(403);
    assertThat(as(staff, post("/api/v1/claims/{id}/submit", claimId)).getResponse().getStatus())
        .isEqualTo(403);
    assertThat(as(staff, post("/api/v1/claims/{id}/withdraw", claimId)).getResponse().getStatus())
        .isEqualTo(403);
    assertThat(as(staff, post("/api/v1/claims/{id}/respond", claimId)).getResponse().getStatus())
        .isEqualTo(403);
    assertThat(as(staff, get("/api/v1/claims")).getResponse().getStatus()).isEqualTo(403);
  }

  @Test
  void anAdjusterTokenWithoutAnInsurerIsRefusedEverywhere() throws Exception {
    String malformed = TestTokens.adjusterWithoutInsurer(UUID.randomUUID());

    assertCode(as(malformed, get("/api/v1/claims/queue")), 403, "STAFF_SCOPE_MISSING");
    assertCode(
        as(malformed, post("/api/v1/claims/{id}/start-review", UUID.randomUUID())),
        403,
        "STAFF_SCOPE_MISSING");
  }

  @Test
  void nobodyReviewsAClaimTheyFiledThemselves() throws Exception {
    // One person holds the policy as a customer and works as an adjuster for the same insurer.
    UUID person = UUID.randomUUID();
    String asCustomer = TestTokens.customer(person);
    String asAdjuster = adjuster(person);
    String claimId = submitted(asCustomer, policy(person, unlimited()), "100.00");

    assertCode(
        as(asAdjuster, post("/api/v1/claims/{id}/start-review", claimId)),
        403,
        "SELF_REVIEW_NOT_ALLOWED");
    assertCode(approve(asAdjuster, claimId, Map.of()), 403, "SELF_REVIEW_NOT_ALLOWED");
    assertCode(reject(asAdjuster, claimId, "no"), 403, "SELF_REVIEW_NOT_ALLOWED");
    assertCode(requestInfo(asAdjuster, claimId, "no"), 403, "SELF_REVIEW_NOT_ALLOWED");
  }

  @Test
  void theQueueIsPaged() throws Exception {
    UUID insurer = UUID.randomUUID();
    String staff = TestTokens.adjuster(UUID.randomUUID(), insurer);
    UUID holder = UUID.randomUUID();
    String customer = TestTokens.customer(holder);
    UUID policy = policy(holder, unlimited(), LocalDate.now().minusDays(100), insurer);
    submitted(customer, policy, "100.00", "Clinic A", 10);
    submitted(customer, policy, "200.00", "Clinic B", 11);

    MvcResult page = as(staff, get("/api/v1/claims/queue?page=0&size=1"));

    assertThat(this.<Integer>read(page, "$.totalElements")).isEqualTo(2);
    assertThat(this.<Integer>read(page, "$.totalPages")).isEqualTo(2);
    List<Object> content = this.read(page, "$.content");
    assertThat(content).hasSize(1);
    assertThat(as(staff, get("/api/v1/claims/queue?size=0")).getResponse().getStatus())
        .isEqualTo(400);
  }
}
