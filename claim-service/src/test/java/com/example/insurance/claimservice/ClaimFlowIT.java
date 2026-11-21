package com.example.insurance.claimservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.example.insurance.claimservice.support.FakeDocumentStorageConfig;
import com.example.insurance.claimservice.support.TestSecurityConfig;
import com.example.insurance.claimservice.support.TestTokens;
import com.example.insurance.messaging.OutboxEvent;
import com.example.insurance.messaging.OutboxEventRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The claim journey through the API (PLAN 7, 9, 12, M5b): drafting, the documents, submission with
 * its automatic checks, withdrawal, the status rules, ownership and the upload checks.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestSecurityConfig.class, FakeDocumentStorageConfig.class})
class ClaimFlowIT extends AbstractClaimIT {

  private static final byte[] PDF = "%PDF-1.4 receipt body".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] PNG =
      new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

  @Autowired private OutboxEventRepository outbox;

  /** Activates a policy whose cover began long ago, so an incident ten days back is in force. */
  private UUID activePolicy(UUID holder) throws Exception {
    UUID policyId = UUID.randomUUID();
    LocalDate today = LocalDate.now();
    publish(
        UUID.randomUUID(),
        "PolicyActivated",
        policyId,
        activatedWithTerms(policyId, holder, 2, today.minusDays(400), today.plusDays(365)));
    awaitStatus(TestTokens.customer(holder), policyId, "ACTIVE");
    return policyId;
  }

  private static Map<String, Object> newClaim(UUID policyId, BigDecimal amount) {
    return Map.of("policyId", policyId.toString(), "claim", details(amount));
  }

  private static Map<String, Object> details(BigDecimal amount) {
    return Map.of(
        "memberId", "M-1",
        "moduleCode", "OUTPATIENT",
        "benefitCode", "OP-CONSULT",
        "cause", "ACCIDENT",
        "surgical", false,
        "conditionTags", List.of(),
        "incidentDate", LocalDate.now().minusDays(10).toString(),
        "providerName", "Test Clinic",
        "description", "Outpatient consultation",
        "amountClaimed", amount);
  }

  private MvcResult send(MockHttpServletRequestBuilder request, String token) throws Exception {
    return mockMvc.perform(request.header("Authorization", "Bearer " + token)).andReturn();
  }

  private MvcResult createDraft(String token, UUID policyId) throws Exception {
    return send(
        post("/api/v1/claims")
            .contentType("application/json")
            .content(json.writeValueAsString(newClaim(policyId, new BigDecimal("3000.00")))),
        token);
  }

  /** A new draft's id, for a test that only needs a claim to work on. */
  private String draftFor(String token, UUID policyId) throws Exception {
    return this.<String>read(createDraft(token, policyId), "$.id");
  }

  private MvcResult submit(String token, String claimId) throws Exception {
    return send(post("/api/v1/claims/{id}/submit", claimId), token);
  }

  private MvcResult withdraw(String token, String claimId) throws Exception {
    return send(post("/api/v1/claims/{id}/withdraw", claimId), token);
  }

  private MvcResult edit(String token, String claimId, BigDecimal amount) throws Exception {
    return send(
        put("/api/v1/claims/{id}", claimId)
            .contentType("application/json")
            .content(json.writeValueAsString(details(amount))),
        token);
  }

  private MvcResult uploadPdf(String token, String claimId, String docType) throws Exception {
    return upload(token, claimId, docType, "receipt.pdf", "application/pdf", PDF);
  }

  private MvcResult upload(
      String token,
      String claimId,
      String docType,
      String fileName,
      String contentType,
      byte[] content)
      throws Exception {
    return mockMvc
        .perform(
            multipart("/api/v1/claims/{id}/documents", claimId)
                .file(new MockMultipartFile("file", fileName, contentType, content))
                .param("docType", docType)
                .header("Authorization", "Bearer " + token))
        .andReturn();
  }

  @Test
  void aClaimThatPassesIsSubmittedAndAnnouncedThroughTheOutbox() throws Exception {
    UUID holder = UUID.randomUUID();
    String token = TestTokens.customer(holder);
    UUID policyId = activePolicy(holder);

    MvcResult created = createDraft(token, policyId);
    assertThat(created.getResponse().getStatus()).isEqualTo(201);
    String claimId = this.<String>read(created, "$.id");
    assertThat(this.<String>read(created, "$.status")).isEqualTo("DRAFT");
    assertThat(this.<String>read(created, "$.claimNumber")).startsWith("CLM-ACACIA-");

    assertThat(uploadPdf(token, claimId, "RECEIPT").getResponse().getStatus()).isEqualTo(201);
    MvcResult submitted = submit(token, claimId);

    assertThat(submitted.getResponse().getStatus()).isEqualTo(200);
    assertThat(this.<String>read(submitted, "$.status")).isEqualTo("SUBMITTED");
    List<Object> checks = this.read(submitted, "$.assessments");
    assertThat(checks).hasSize(8);
    assertThat(outboxEventTypes()).contains("ClaimSubmitted");
  }

  @Test
  void aClaimWithoutItsRequiredDocumentIsRejectedAtOnceWithTheReason() throws Exception {
    UUID holder = UUID.randomUUID();
    String token = TestTokens.customer(holder);
    String claimId = draftFor(token, activePolicy(holder));

    MvcResult submitted = submit(token, claimId);

    assertThat(this.<String>read(submitted, "$.status")).isEqualTo("REJECTED");
    assertThat(this.<String>read(submitted, "$.rejectionReason")).isEqualTo("REQUIRED_DOCUMENTS");
    MvcResult history = send(get("/api/v1/claims/{id}/history", claimId), token);
    assertThat(this.<Integer>read(history, "$.totalElements")).isEqualTo(3);
    assertThat(this.<String>read(history, "$.content[1].to")).isEqualTo("SUBMITTED");
    assertThat(this.<String>read(history, "$.content[2].to")).isEqualTo("REJECTED");
    assertThat(this.<String>read(history, "$.content[2].actorRole")).isEqualTo("SYSTEM");
  }

  @Test
  void aClaimAgainstAPolicyThatIsNotOursIsNotFound() throws Exception {
    UUID policyId = activePolicy(UUID.randomUUID());

    MvcResult result = createDraft(TestTokens.customer(UUID.randomUUID()), policyId);

    assertThat(result.getResponse().getStatus()).isEqualTo(404);
    assertThat(this.<String>read(result, "$.code")).isEqualTo("POLICY_NOT_FOUND");
  }

  @Test
  void aNonPositiveAmountIsRejectedBeforeAnythingIsSaved() throws Exception {
    UUID holder = UUID.randomUUID();
    MvcResult result =
        createDraftWithAmount(TestTokens.customer(holder), activePolicy(holder), BigDecimal.ZERO);

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(this.<String>read(result, "$.code")).isEqualTo("VALIDATION_FAILED");
  }

  private MvcResult createDraftWithAmount(String token, UUID policyId, BigDecimal amount)
      throws Exception {
    return send(
        post("/api/v1/claims")
            .contentType("application/json")
            .content(json.writeValueAsString(newClaim(policyId, amount))),
        token);
  }

  @Test
  void aSubmittedClaimCanBeWithdrawnOnceAndNotEditedAfterwards() throws Exception {
    UUID holder = UUID.randomUUID();
    String token = TestTokens.customer(holder);
    String claimId = draftFor(token, activePolicy(holder));
    uploadPdf(token, claimId, "RECEIPT");
    submit(token, claimId);

    MvcResult late = edit(token, claimId, new BigDecimal("50.00"));
    assertThat(this.<String>read(late, "$.code")).isEqualTo("CLAIM_NOT_EDITABLE");

    MvcResult withdrawn = withdraw(token, claimId);
    assertThat(this.<String>read(withdrawn, "$.status")).isEqualTo("WITHDRAWN");

    MvcResult again = withdraw(token, claimId);
    assertThat(again.getResponse().getStatus()).isEqualTo(409);
    assertThat(this.<String>read(again, "$.code")).isEqualTo("INVALID_TRANSITION");
  }

  @Test
  void aDraftCanBeEditedWithoutAStatusChange() throws Exception {
    UUID holder = UUID.randomUUID();
    String token = TestTokens.customer(holder);
    String claimId = draftFor(token, activePolicy(holder));

    MvcResult edited = edit(token, claimId, new BigDecimal("1200.50"));

    assertThat(edited.getResponse().getContentAsString()).contains("\"amountClaimed\":1200.50");
    assertThat(this.<String>read(edited, "$.status")).isEqualTo("DRAFT");
    MvcResult history = send(get("/api/v1/claims/{id}/history", claimId), token);
    assertThat(this.<Integer>read(history, "$.totalElements")).isEqualTo(1);
  }

  @Test
  void aPdfAndAPngAreStoredAndTheDocumentIsDownloadableThroughAPresignedLink() throws Exception {
    UUID holder = UUID.randomUUID();
    String token = TestTokens.customer(holder);
    String claimId = draftFor(token, activePolicy(holder));

    MvcResult pdf = upload(token, claimId, "RECEIPT", "invoice.pdf", "application/pdf", PDF);
    assertThat(pdf.getResponse().getStatus()).isEqualTo(201);
    assertThat(this.<String>read(pdf, "$.fileName")).isEqualTo("invoice.pdf");
    String documentId = this.<String>read(pdf, "$.id");
    assertThat(
            upload(token, claimId, "PHOTO", "photo.png", "image/png", PNG)
                .getResponse()
                .getStatus())
        .isEqualTo(201);

    MvcResult link =
        send(get("/api/v1/claims/{id}/documents/{doc}/download-url", claimId, documentId), token);
    assertThat(link.getResponse().getStatus()).isEqualTo(200);
    assertThat(this.<String>read(link, "$.url")).startsWith("https://storage.example.test/claims/");
    assertThat(this.<String>read(link, "$.expiresAt")).isNotBlank();

    MvcResult listed = send(get("/api/v1/claims/{id}/documents", claimId), token);
    assertThat(this.<Integer>read(listed, "$.totalElements")).isEqualTo(2);
  }

  @Test
  void uploadsAreCheckedForTypeContentSizeAndTheTypeName() throws Exception {
    UUID holder = UUID.randomUUID();
    String token = TestTokens.customer(holder);
    String claimId = draftFor(token, activePolicy(holder));

    assertCode(
        upload(token, claimId, "RECEIPT", "a.txt", "text/plain", PDF),
        415,
        "DOCUMENT_TYPE_NOT_ALLOWED");
    assertCode(
        upload(token, claimId, "RECEIPT", "a.pdf", "application/pdf", PNG),
        422,
        "DOCUMENT_CONTENT_MISMATCH");
    assertCode(
        upload(token, claimId, "RECEIPT", "big.pdf", "application/pdf", bigPdf()),
        413,
        "DOCUMENT_TOO_LARGE");
    assertCode(uploadPdf(token, claimId, "receipt"), 400, "VALIDATION_FAILED");
  }

  @Test
  void aClaimHoldsAtMostTenDocuments() throws Exception {
    UUID holder = UUID.randomUUID();
    String token = TestTokens.customer(holder);
    String claimId = draftFor(token, activePolicy(holder));
    for (int i = 0; i < 10; i++) {
      assertThat(
              upload(token, claimId, "RECEIPT", "r" + i + ".pdf", "application/pdf", PDF)
                  .getResponse()
                  .getStatus())
          .isEqualTo(201);
    }

    assertCode(
        upload(token, claimId, "RECEIPT", "r10.pdf", "application/pdf", PDF),
        409,
        "DOCUMENT_LIMIT_REACHED");
  }

  @Test
  void anotherCustomerGetsNotFoundOnEveryClaimEndpoint() throws Exception {
    UUID holder = UUID.randomUUID();
    String owner = TestTokens.customer(holder);
    String claimId = draftFor(owner, activePolicy(holder));
    uploadPdf(owner, claimId, "RECEIPT");
    String documentId =
        this.<String>read(
            send(get("/api/v1/claims/{id}/documents", claimId), owner), "$.content[0].id");
    String intruder = TestTokens.customer(UUID.randomUUID());

    assertThat(send(get("/api/v1/claims/{id}", claimId), intruder).getResponse().getStatus())
        .isEqualTo(404);
    assertThat(
            send(get("/api/v1/claims/{id}/history", claimId), intruder).getResponse().getStatus())
        .isEqualTo(404);
    assertThat(
            send(get("/api/v1/claims/{id}/documents", claimId), intruder).getResponse().getStatus())
        .isEqualTo(404);
    assertThat(
            send(
                    get("/api/v1/claims/{id}/documents/{doc}/download-url", claimId, documentId),
                    intruder)
                .getResponse()
                .getStatus())
        .isEqualTo(404);
    assertThat(edit(intruder, claimId, BigDecimal.ONE).getResponse().getStatus()).isEqualTo(404);
    assertThat(submit(intruder, claimId).getResponse().getStatus()).isEqualTo(404);
    assertThat(withdraw(intruder, claimId).getResponse().getStatus()).isEqualTo(404);
    assertThat(uploadPdf(intruder, claimId, "RECEIPT").getResponse().getStatus()).isEqualTo(404);

    MvcResult list = send(get("/api/v1/claims"), intruder);
    assertThat(list.getResponse().getContentAsString()).doesNotContain(claimId);
  }

  @Test
  void staffCannotUseTheCustomerClaimEndpoints() throws Exception {
    MvcResult result = send(get("/api/v1/claims"), TestTokens.service("policy-service"));

    assertThat(result.getResponse().getStatus()).isEqualTo(403);
  }

  private void assertCode(MvcResult result, int status, String code) throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(status);
    assertThat(this.<String>read(result, "$.code")).isEqualTo(code);
  }

  private static byte[] bigPdf() {
    byte[] content = new byte[10 * 1024 * 1024 + 1];
    System.arraycopy(PDF, 0, content, 0, PDF.length);
    return content;
  }

  private List<String> outboxEventTypes() {
    return outbox.findAll().stream().map(OutboxEvent::getEventType).toList();
  }
}
