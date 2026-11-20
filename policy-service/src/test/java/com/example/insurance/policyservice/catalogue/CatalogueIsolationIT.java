package com.example.insurance.policyservice.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.insurance.policyservice.AbstractPolicyIT;
import com.example.insurance.policyservice.support.TestTokens;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Every staff endpoint is scoped by the token's insurerId (CLAUDE.md): an admin of insurer A never
 * sees or changes insurer B's data, and gets 404 so B's data is not revealed.
 */
class CatalogueIsolationIT extends AbstractPolicyIT {

  private static final String ACACIA = "acacia-standard.v1.json";

  private UUID insurerA;
  private UUID insurerB;
  private String codeB;
  private UUID planOfA;
  private UUID planOfB;
  private String adminOfA;

  @BeforeEach
  void twoInsurers() throws Exception {
    String codeA = uniqueCode("ISO-A");
    codeB = uniqueCode("ISO-B");
    insurerA = createInsurer(codeA);
    insurerB = createInsurer(codeB);
    planOfA = importDraft(insurerA, planJson(ACACIA, codeA));
    planOfB = publishedPlan(insurerB, planJson(ACACIA, codeB, version(1)));
    adminOfA = TestTokens.insurerAdmin(insurerA);
  }

  private MvcResult list(String token, String insurerFilter) throws Exception {
    var request = get("/api/v1/admin/plans");
    if (insurerFilter != null) {
      request.param("insurerId", insurerFilter);
    }
    return mockMvc.perform(withToken(request, token)).andReturn();
  }

  private static void assertProblem(MvcResult result, int status, String code) throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(status);
    assertThat((String) read(result, "$.code")).isEqualTo(code);
  }

  @Test
  void anInsurerAdminCannotCreateInsurers() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                withToken(post("/api/v1/admin/insurers"), adminOfA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"" + uniqueCode("NOPE") + "\",\"name\":\"X\"}"))
            .andReturn();
    assertProblem(result, 403, "PLATFORM_ADMIN_REQUIRED");
  }

  @Test
  void anInsurerAdminCannotImportIntoAnotherInsurer() throws Exception {
    assertProblem(
        importPlan(adminOfA, insurerB, planJson(ACACIA, codeB), true), 404, "INSURER_NOT_FOUND");
  }

  @Test
  void anInsurerAdminCanImportIntoTheirOwnInsurer() throws Exception {
    MvcResult result = importPlan(adminOfA, insurerA, planJson(ACACIA, "IGNORED"), true);
    // The insurer is visible, so the request reaches validation of the document's insurer code.
    assertProblem(result, 422, "INSURER_MISMATCH");
  }

  @Test
  void anInsurerAdminCannotPublishAnotherInsurersPlan() throws Exception {
    assertProblem(planAction(adminOfA, planOfB, "publish"), 404, "PLAN_NOT_FOUND");
  }

  @Test
  void anInsurerAdminCannotRetireAnotherInsurersPlan() throws Exception {
    assertProblem(planAction(adminOfA, planOfB, "retire"), 404, "PLAN_NOT_FOUND");
  }

  @Test
  void anInsurerAdminListsOnlyTheirOwnPlans() throws Exception {
    MvcResult result = list(adminOfA, null);
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    List<String> insurers = read(result, "$.content[*].insurerId");
    List<String> ids = read(result, "$.content[*].id");
    assertThat(insurers).isNotEmpty().allMatch(insurerA.toString()::equals);
    assertThat(ids).contains(planOfA.toString()).doesNotContain(planOfB.toString());
  }

  @Test
  void anInsurerAdminCannotFilterByAnotherInsurer() throws Exception {
    assertProblem(list(adminOfA, insurerB.toString()), 404, "INSURER_NOT_FOUND");
  }

  @Test
  void aPlatformAdminCanFilterByAnyInsurer() throws Exception {
    MvcResult result = list(TestTokens.platformAdmin(), insurerB.toString());
    List<String> ids = read(result, "$.content[*].id");
    assertThat(ids).containsExactly(planOfB.toString());
  }

  @Test
  void customersAndAdjustersCannotUseAdminEndpoints() throws Exception {
    for (String token : List.of(TestTokens.customer(), TestTokens.adjuster(insurerA))) {
      assertProblem(list(token, null), 403, "FORBIDDEN");
      assertProblem(planAction(token, planOfA, "publish"), 403, "FORBIDDEN");
      assertProblem(planAction(token, planOfA, "retire"), 403, "FORBIDDEN");
      assertProblem(importPlan(token, insurerA, "{}", true), 403, "FORBIDDEN");
    }
  }

  @Test
  void anonymousCallersAreUnauthenticated() throws Exception {
    assertProblem(list(null, null), 401, "UNAUTHENTICATED");
    assertProblem(planAction(null, planOfA, "publish"), 401, "UNAUTHENTICATED");
  }
}
