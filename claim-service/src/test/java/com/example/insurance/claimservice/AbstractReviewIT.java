package com.example.insurance.claimservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.insurance.claimservice.support.TestTokens;
import com.example.insurance.events.policy.PolicyActivated;
import com.example.insurance.events.policy.TermsSnapshot;
import com.example.insurance.events.policy.TermsSnapshot.BenefitTerms;
import com.example.insurance.events.policy.TermsSnapshot.CopayTerms;
import com.example.insurance.events.policy.TermsSnapshot.LimitTerms;
import com.example.insurance.messaging.OutboxEvent;
import com.example.insurance.messaging.OutboxEventRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * What the review tests share: a policy whose terms carry the limits under test, and a claim walked
 * to any stage (drafted, submitted, under review). Staff tokens carry the insurer of {@link
 * #INSURER}.
 */
abstract class AbstractReviewIT extends AbstractClaimIT {

  protected static final byte[] PDF = "%PDF-1.4 receipt".getBytes(StandardCharsets.US_ASCII);

  @Autowired protected OutboxEventRepository outbox;

  /** One outpatient tree: OUTPATIENT (parent limit) over OP-CONSULT (leaf limit and co-pay). */
  protected static TermsSnapshot terms(
      LimitTerms parentLimit, LimitTerms leafLimit, CopayTerms leafCopay) {
    BenefitTerms consult =
        new BenefitTerms(
            "OP-CONSULT", "SERVICE", leafLimit, leafCopay, List.of("RECEIPT"), false, List.of());
    BenefitTerms outpatient =
        new BenefitTerms(
            "OUTPATIENT", "CATEGORY", parentLimit, null, List.of(), false, List.of(consult));
    return new TermsSnapshot(
        1,
        "ACACIA",
        UUID.fromString("00000000-0000-4000-8000-00000000cafe"),
        "STANDARD",
        1,
        "KES",
        "NEXT_BIRTHDAY",
        List.of(new TermsSnapshot.ModuleTerms("OUTPATIENT", "OP-40K", outpatient)),
        List.of(),
        List.of(),
        90,
        null);
  }

  /** A shared annual limit: PER_YEAR, SHARED, with an optional visit count. */
  protected static LimitTerms annualLimit(String amount, Integer maxCount) {
    return new LimitTerms(new BigDecimal(amount), "PER_YEAR", "SHARED", null, maxCount);
  }

  /** A cap on each visit that keeps no running total. */
  protected static LimitTerms perVisitCap(String maxPerVisit) {
    return new LimitTerms(null, "PER_CLAIM", "SHARED", new BigDecimal(maxPerVisit), null);
  }

  protected static CopayTerms fixedCopay(String value) {
    return new CopayTerms("FIXED_PER_VISIT", new BigDecimal(value));
  }

  /** No limits and no co-pay anywhere. */
  protected static TermsSnapshot unlimited() {
    return terms(null, null, null);
  }

  /** Activates a policy through Kafka and waits until the read model has it. */
  protected UUID policy(UUID holder, TermsSnapshot terms, LocalDate coverStart) throws Exception {
    return policy(holder, terms, coverStart, INSURER);
  }

  /** The same, for a policy sold by another insurer. */
  protected UUID policy(UUID holder, TermsSnapshot terms, LocalDate coverStart, UUID insurer)
      throws Exception {
    UUID policyId = UUID.randomUUID();
    PolicyActivated activation =
        new PolicyActivated(
            policyId,
            "POL-ACACIA-2026-" + policyId.toString().substring(0, 6),
            2,
            holder,
            coverStart,
            coverStart.plusYears(1).minusDays(1),
            List.of(
                new PolicyActivated.MemberTerms(
                    "M-1", "PRINCIPAL", "MALE", "Amina", "Test", LocalDate.of(1989, 1, 1), false)),
            terms);
    publish(UUID.randomUUID(), "PolicyActivated", policyId, activation, insurer);
    awaitStatus(TestTokens.customer(holder), policyId, "ACTIVE");
    return policyId;
  }

  protected UUID policy(UUID holder, TermsSnapshot terms) throws Exception {
    return policy(holder, terms, LocalDate.now().minusDays(100));
  }

  protected MvcResult as(String token, MockHttpServletRequestBuilder request) throws Exception {
    return mockMvc.perform(request.header("Authorization", "Bearer " + token)).andReturn();
  }

  protected Map<String, Object> claimBody(
      UUID policyId, String amount, String provider, int incidentDaysAgo) {
    Map<String, Object> details =
        Map.of(
            "memberId",
            "M-1",
            "moduleCode",
            "OUTPATIENT",
            "benefitCode",
            "OP-CONSULT",
            "cause",
            "ACCIDENT",
            "surgical",
            false,
            "conditionTags",
            List.of(),
            "incidentDate",
            LocalDate.now().minusDays(incidentDaysAgo).toString(),
            "providerName",
            provider,
            "description",
            "Outpatient consultation",
            "amountClaimed",
            new BigDecimal(amount));
    return Map.of("policyId", policyId.toString(), "claim", details);
  }

  /** A draft with its receipt uploaded, submitted, and checked to be SUBMITTED. Returns its id. */
  protected String submitted(String customer, UUID policyId, String amount) throws Exception {
    return submitted(customer, policyId, amount, "Test Clinic", 10);
  }

  protected String submitted(
      String customer, UUID policyId, String amount, String provider, int incidentDaysAgo)
      throws Exception {
    MvcResult created =
        as(
            customer,
            post("/api/v1/claims")
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        claimBody(policyId, amount, provider, incidentDaysAgo))));
    assertThat(created.getResponse().getStatus()).isEqualTo(201);
    String claimId = this.<String>read(created, "$.id");
    uploadReceipt(customer, claimId);
    MvcResult result = as(customer, post("/api/v1/claims/{id}/submit", claimId));
    assertThat(this.<String>read(result, "$.status")).isEqualTo("SUBMITTED");
    return claimId;
  }

  protected MvcResult uploadReceipt(String token, String claimId) throws Exception {
    return mockMvc
        .perform(
            multipart("/api/v1/claims/{id}/documents", claimId)
                .file(new MockMultipartFile("file", "receipt.pdf", "application/pdf", PDF))
                .param("docType", "RECEIPT")
                .header("Authorization", "Bearer " + token))
        .andReturn();
  }

  /** A claim that is SUBMITTED and then taken into review by {@code staff}. */
  protected String underReview(String customer, UUID policyId, String amount, String staff)
      throws Exception {
    String claimId = submitted(customer, policyId, amount);
    MvcResult started = as(staff, post("/api/v1/claims/{id}/start-review", claimId));
    assertThat(started.getResponse().getStatus()).isEqualTo(200);
    return claimId;
  }

  protected MvcResult approve(String staff, String claimId, Map<String, Object> body)
      throws Exception {
    return as(
        staff,
        post("/api/v1/claims/{id}/approve", claimId)
            .contentType("application/json")
            .content(json.writeValueAsString(body)));
  }

  protected MvcResult reject(String staff, String claimId, String reason) throws Exception {
    return as(
        staff,
        post("/api/v1/claims/{id}/reject", claimId)
            .contentType("application/json")
            .content(json.writeValueAsString(Map.of("reason", reason))));
  }

  protected MvcResult requestInfo(String staff, String claimId, String message) throws Exception {
    return as(
        staff,
        post("/api/v1/claims/{id}/request-info", claimId)
            .contentType("application/json")
            .content(json.writeValueAsString(Map.of("message", message))));
  }

  protected MvcResult claimAs(String token, String claimId) throws Exception {
    return as(token, get("/api/v1/claims/{id}", claimId));
  }

  protected void assertCode(MvcResult result, int status, String code) throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(status);
    assertThat(this.<String>read(result, "$.code")).isEqualTo(code);
  }

  protected List<String> outboxTypes() {
    return outbox.findAll().stream().map(OutboxEvent::getEventType).toList();
  }

  /** The amount in a JSON response, compared as money. */
  protected BigDecimal money(MvcResult result, String path) throws Exception {
    Object value = this.<Object>read(result, path);
    return value == null ? null : new BigDecimal(value.toString());
  }

  protected static String adjuster(UUID userId) {
    return TestTokens.adjuster(userId, INSURER);
  }
}
