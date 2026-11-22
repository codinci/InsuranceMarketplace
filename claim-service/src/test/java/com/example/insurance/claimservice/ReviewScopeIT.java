package com.example.insurance.claimservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.insurance.claimservice.support.FakeDocumentStorageConfig;
import com.example.insurance.claimservice.support.TestSecurityConfig;
import com.example.insurance.claimservice.support.TestTokens;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Staff see only their own insurer's claims (CLAUDE.md, PLAN 4): a cross-insurer isolation test for
 * every staff endpoint. A claim of another insurer answers exactly like a claim that does not
 * exist.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestSecurityConfig.class, FakeDocumentStorageConfig.class})
class ReviewScopeIT extends AbstractReviewIT {

  private static final UUID OTHER_INSURER = UUID.fromString("b4d2f8a1-2222-4333-8444-555566667777");

  @Test
  void staffOfAnotherInsurerFindNothingOnEveryStaffEndpoint() throws Exception {
    UUID holder = UUID.randomUUID();
    String customer = TestTokens.customer(holder);
    UUID insurer = UUID.randomUUID();
    UUID policyId = policy(holder, unlimited(), LocalDate.now().minusDays(100), insurer);
    String claimId = submitted(customer, policyId, "1000.00");
    String documentId =
        this.<String>read(
            as(customer, get("/api/v1/claims/{id}/documents", claimId)), "$.content[0].id");

    for (String stranger :
        new String[] {
          TestTokens.adjuster(UUID.randomUUID(), OTHER_INSURER),
          TestTokens.insurerAdmin(UUID.randomUUID(), OTHER_INSURER)
        }) {
      assertCode(claimAs(stranger, claimId), 404, "CLAIM_NOT_FOUND");
      assertCode(as(stranger, get("/api/v1/claims/{id}/history", claimId)), 404, "CLAIM_NOT_FOUND");
      assertCode(
          as(stranger, get("/api/v1/claims/{id}/documents", claimId)), 404, "CLAIM_NOT_FOUND");
      assertCode(
          as(
              stranger,
              get("/api/v1/claims/{id}/documents/{doc}/download-url", claimId, documentId)),
          404,
          "CLAIM_NOT_FOUND");
      assertCode(
          as(stranger, post("/api/v1/claims/{id}/start-review", claimId)), 404, "CLAIM_NOT_FOUND");
      assertCode(requestInfo(stranger, claimId, "More please"), 404, "CLAIM_NOT_FOUND");
      assertCode(approve(stranger, claimId, Map.of()), 404, "CLAIM_NOT_FOUND");
      assertCode(reject(stranger, claimId, "No"), 404, "CLAIM_NOT_FOUND");
      assertThat(
              as(stranger, get("/api/v1/claims/queue?size=100")).getResponse().getContentAsString())
          .doesNotContain(claimId);
    }
  }

  @Test
  void refusedStaffActionsLeaveAnotherInsurersClaimUntouched() throws Exception {
    UUID holder = UUID.randomUUID();
    String customer = TestTokens.customer(holder);
    UUID insurer = UUID.randomUUID();
    String claimId =
        submitted(
            customer,
            policy(holder, unlimited(), LocalDate.now().minusDays(100), insurer),
            "500.00");
    String stranger = TestTokens.adjuster(UUID.randomUUID(), OTHER_INSURER);

    as(stranger, post("/api/v1/claims/{id}/start-review", claimId));
    reject(stranger, claimId, "No");

    assertThat(this.<String>read(claimAs(customer, claimId), "$.status")).isEqualTo("SUBMITTED");
  }

  @Test
  void staffOfTheSameInsurerSeeTheClaimDocumentsAndHistory() throws Exception {
    UUID holder = UUID.randomUUID();
    String customer = TestTokens.customer(holder);
    UUID insurer = UUID.randomUUID();
    String claimId =
        submitted(
            customer,
            policy(holder, unlimited(), LocalDate.now().minusDays(100), insurer),
            "500.00");
    String documentId =
        this.<String>read(
            as(customer, get("/api/v1/claims/{id}/documents", claimId)), "$.content[0].id");

    for (String staff :
        new String[] {
          TestTokens.adjuster(UUID.randomUUID(), insurer),
          TestTokens.insurerAdmin(UUID.randomUUID(), insurer)
        }) {
      assertThat(claimAs(staff, claimId).getResponse().getStatus()).isEqualTo(200);
      assertThat(as(staff, get("/api/v1/claims/{id}/history", claimId)).getResponse().getStatus())
          .isEqualTo(200);
      assertThat(as(staff, get("/api/v1/claims/{id}/documents", claimId)).getResponse().getStatus())
          .isEqualTo(200);
      MvcResult link =
          as(staff, get("/api/v1/claims/{id}/documents/{doc}/download-url", claimId, documentId));
      assertThat(link.getResponse().getStatus()).isEqualTo(200);
      assertThat(this.<String>read(link, "$.url"))
          .startsWith("https://storage.example.test/claims/");
    }
  }

  @Test
  void theQueueIsScopedToTheTokensInsurerAndAPlatformAdminSeesEveryInsurer() throws Exception {
    UUID insurerA = UUID.randomUUID();
    UUID insurerB = UUID.randomUUID();
    UUID holderA = UUID.randomUUID();
    UUID holderB = UUID.randomUUID();
    String claimA =
        submitted(
            TestTokens.customer(holderA),
            policy(holderA, unlimited(), LocalDate.now().minusDays(100), insurerA),
            "100.00");
    String claimB =
        submitted(
            TestTokens.customer(holderB),
            policy(holderB, unlimited(), LocalDate.now().minusDays(100), insurerB),
            "100.00");

    String queueA =
        as(TestTokens.adjuster(UUID.randomUUID(), insurerA), get("/api/v1/claims/queue?size=100"))
            .getResponse()
            .getContentAsString();
    String queueB =
        as(
                TestTokens.insurerAdmin(UUID.randomUUID(), insurerB),
                get("/api/v1/claims/queue?size=100"))
            .getResponse()
            .getContentAsString();
    String queuePlatform =
        as(TestTokens.platformAdmin(UUID.randomUUID()), get("/api/v1/claims/queue?size=100"))
            .getResponse()
            .getContentAsString();

    assertThat(queueA).contains(claimA).doesNotContain(claimB);
    assertThat(queueB).contains(claimB).doesNotContain(claimA);
    assertThat(queuePlatform).contains(claimA).contains(claimB);
  }

  @Test
  void aPlatformAdminCanReadAndReviewAnyInsurersClaim() throws Exception {
    UUID holder = UUID.randomUUID();
    UUID insurer = UUID.randomUUID();
    String claimId =
        submitted(
            TestTokens.customer(holder),
            policy(holder, unlimited(), LocalDate.now().minusDays(100), insurer),
            "100.00");
    String platform = TestTokens.platformAdmin(UUID.randomUUID());

    assertThat(claimAs(platform, claimId).getResponse().getStatus()).isEqualTo(200);
    assertThat(
            as(platform, post("/api/v1/claims/{id}/start-review", claimId))
                .getResponse()
                .getStatus())
        .isEqualTo(200);
    assertThat(approve(platform, claimId, Map.of()).getResponse().getStatus()).isEqualTo(200);
  }
}
