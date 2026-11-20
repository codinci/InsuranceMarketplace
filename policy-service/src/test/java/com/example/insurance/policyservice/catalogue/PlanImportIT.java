package com.example.insurance.policyservice.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.example.insurance.policyservice.AbstractPolicyIT;
import com.example.insurance.policyservice.support.TestTokens;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

/** Importing plan documents (PLAN 5.4, 17 M3b): dry runs, drafts, replacement and refusals. */
class PlanImportIT extends AbstractPolicyIT {

  private static final String ACACIA = "acacia-standard.v1.json";

  private String code;
  private UUID insurerId;

  @BeforeEach
  void newInsurer() throws Exception {
    code = uniqueCode("IMP");
    insurerId = createInsurer(code);
  }

  private long storedPlans() throws Exception {
    MvcResult list =
        mockMvc
            .perform(
                withToken(
                    get("/api/v1/admin/plans").param("insurerId", insurerId.toString()),
                    TestTokens.platformAdmin()))
            .andReturn();
    return ((Number) read(list, "$.totalElements")).longValue();
  }

  @Test
  void aDryRunOfAValidPlanStoresNothing() throws Exception {
    MvcResult result =
        importPlan(TestTokens.platformAdmin(), insurerId, planJson(ACACIA, code), true);
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat((String) read(result, "$.outcome")).isEqualTo("VALIDATED");
    assertThat(storedPlans()).isZero();
  }

  @Test
  void anInvalidPlanListsEveryViolation() throws Exception {
    String json =
        planJson(
            ACACIA,
            code,
            plan -> {
              ((ObjectNode) plan.get("plan")).put("currency", "USD");
              ((ObjectNode) plan.at("/plan/modules/0")).put("mandatory", false);
            });
    MvcResult result = importPlan(TestTokens.platformAdmin(), insurerId, json, true);
    assertThat(result.getResponse().getStatus()).isEqualTo(422);
    assertThat((String) read(result, "$.code")).isEqualTo("PLAN_INVALID");
    List<String> codes = read(result, "$.errors[*].code");
    assertThat(codes).contains("CURRENCY_NOT_SUPPORTED", "NO_MANDATORY_MODULE");
    List<String> paths = read(result, "$.errors[*].path");
    assertThat(paths).contains("/plan/currency", "/plan/modules");
  }

  @Test
  void aFixedSumBenefitIsAnUnsupportedFeature() throws Exception {
    String json =
        planJson(
            ACACIA,
            code,
            plan ->
                ((ObjectNode) plan.at("/plan/modules/0/options/0/benefits/children/0"))
                    .set("kind", StringNode.valueOf("FIXED_SUM")));
    MvcResult result = importPlan(TestTokens.platformAdmin(), insurerId, json, false);
    assertThat(result.getResponse().getStatus()).isEqualTo(422);
    List<String> codes = read(result, "$.errors[*].code");
    assertThat(codes).containsExactly("UNSUPPORTED_FEATURE");
    assertThat(storedPlans()).isZero();
  }

  @Test
  void textThatIsNotJsonIsAnInvalidPlan() throws Exception {
    MvcResult result = importPlan(TestTokens.platformAdmin(), insurerId, "{ not json", false);
    assertThat(result.getResponse().getStatus()).isEqualTo(422);
    List<String> codes = read(result, "$.errors[*].code");
    assertThat(codes).containsExactly("MALFORMED_JSON");
  }

  @Test
  void anImportCreatesADraft() throws Exception {
    MvcResult result =
        importPlan(TestTokens.platformAdmin(), insurerId, planJson(ACACIA, code), false);
    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    assertThat((String) read(result, "$.outcome")).isEqualTo("CREATED");
    assertThat((String) read(result, "$.plan.status")).isEqualTo("DRAFT");
    assertThat((String) read(result, "$.plan.insurerId")).isEqualTo(insurerId.toString());
    assertThat((Integer) read(result, "$.plan.version")).isEqualTo(1);
  }

  @Test
  void reImportingADraftReplacesIt() throws Exception {
    UUID planId = importDraft(insurerId, planJson(ACACIA, code));
    String renamed =
        planJson(ACACIA, code, plan -> ((ObjectNode) plan.get("plan")).put("name", "Renamed"));
    MvcResult result = importPlan(TestTokens.platformAdmin(), insurerId, renamed, false);
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat((String) read(result, "$.outcome")).isEqualTo("REPLACED");
    assertThat((String) read(result, "$.plan.id")).isEqualTo(planId.toString());
    assertThat((String) read(result, "$.plan.name")).isEqualTo("Renamed");
    assertThat(storedPlans()).isEqualTo(1);
  }

  @Test
  void aPublishedVersionCannotBeReImported() throws Exception {
    publishedPlan(insurerId, planJson(ACACIA, code));
    MvcResult result =
        importPlan(TestTokens.platformAdmin(), insurerId, planJson(ACACIA, code), false);
    assertThat(result.getResponse().getStatus()).isEqualTo(409);
    assertThat((String) read(result, "$.code")).isEqualTo("PLAN_VERSION_IMMUTABLE");
  }

  @Test
  void theDocumentMustNameTheTargetInsurer() throws Exception {
    MvcResult result =
        importPlan(TestTokens.platformAdmin(), insurerId, planJson(ACACIA, "SOMEONE-ELSE"), false);
    assertThat(result.getResponse().getStatus()).isEqualTo(422);
    assertThat((String) read(result, "$.code")).isEqualTo("INSURER_MISMATCH");
  }

  @Test
  void anOversizedDocumentIsRefusedBeforeParsing() throws Exception {
    String huge = "{\"pad\":\"" + "x".repeat(300 * 1024) + "\"}";
    MvcResult result = importPlan(TestTokens.platformAdmin(), insurerId, huge, false);
    assertThat(result.getResponse().getStatus()).isEqualTo(413);
    assertThat((String) read(result, "$.code")).isEqualTo("PLAN_TOO_LARGE");
  }

  @Test
  void anUnknownInsurerIsNotFound() throws Exception {
    MvcResult result =
        importPlan(TestTokens.platformAdmin(), UUID.randomUUID(), planJson(ACACIA, code), false);
    assertThat(result.getResponse().getStatus()).isEqualTo(404);
    assertThat((String) read(result, "$.code")).isEqualTo("INSURER_NOT_FOUND");
  }
}
