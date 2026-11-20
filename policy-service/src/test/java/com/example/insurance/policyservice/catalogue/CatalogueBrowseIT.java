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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

/** The public catalogue (PLAN 13): no token, only plans on sale, no rating tables. */
class CatalogueBrowseIT extends AbstractPolicyIT {

  private static final String ACACIA = "acacia-standard.v1.json";

  private String code;
  private UUID insurerId;

  @BeforeEach
  void newInsurer() throws Exception {
    code = uniqueCode("BRW");
    insurerId = createInsurer(code);
  }

  private MvcResult anonymousGet(String path, String... params) throws Exception {
    var request = get(path);
    for (int i = 0; i < params.length; i += 2) {
      request.param(params[i], params[i + 1]);
    }
    return mockMvc.perform(request).andReturn();
  }

  private static java.util.function.Consumer<JsonNode> planCode(String planCode) {
    return plan -> ((ObjectNode) plan.get("plan")).put("code", planCode);
  }

  private static java.util.function.Consumer<JsonNode> sale(String from, String to) {
    return plan -> {
      ObjectNode sale = (ObjectNode) plan.at("/plan/sale");
      sale.set("from", StringNode.valueOf(from));
      sale.set("to", StringNode.valueOf(to));
    };
  }

  @Test
  void insurersAreListedWithoutAToken() throws Exception {
    MvcResult result = anonymousGet("/api/v1/insurers", "size", "100");
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    List<String> codes = read(result, "$.content[*].code");
    assertThat(codes).contains(code);
    assertThat((Integer) read(result, "$.size")).isEqualTo(100);
  }

  @Test
  void onlyPublishedPlansOnSaleAreListed() throws Exception {
    UUID onSale = publishedPlan(insurerId, planJson(ACACIA, code, planCode("ON-SALE")));
    UUID draft = importDraft(insurerId, planJson(ACACIA, code, planCode("DRAFT")));
    UUID retired = publishedPlan(insurerId, planJson(ACACIA, code, planCode("RETIRED")));
    planAction(TestTokens.platformAdmin(), retired, "retire");
    UUID ended =
        publishedPlan(
            insurerId,
            planJson(ACACIA, code, planCode("ENDED").andThen(sale("2020-01-01", "2021-12-31"))));
    UUID future =
        publishedPlan(
            insurerId,
            planJson(ACACIA, code, planCode("FUTURE").andThen(sale("2099-01-01", null))));

    MvcResult result = anonymousGet("/api/v1/insurers/" + code + "/plans");
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    List<String> ids = read(result, "$.content[*].id");
    assertThat(ids).containsExactly(onSale.toString());
    assertThat(ids)
        .doesNotContain(draft.toString(), retired.toString(), ended.toString(), future.toString());
    assertThat((String) read(result, "$.content[0].productCode")).isEqualTo("MEDICAL");
    List<String> modules = read(result, "$.content[0].modules[*].code");
    assertThat(modules).containsExactly("INPATIENT", "OUTPATIENT", "MATERNITY");
  }

  @Test
  void plansArePaged() throws Exception {
    publishedPlan(insurerId, planJson(ACACIA, code, planCode("A-PLAN")));
    publishedPlan(insurerId, planJson(ACACIA, code, planCode("B-PLAN")));
    MvcResult first = anonymousGet("/api/v1/insurers/" + code + "/plans", "size", "1");
    assertThat((Integer) read(first, "$.content.length()")).isEqualTo(1);
    assertThat((Integer) read(first, "$.totalElements")).isEqualTo(2);
    assertThat((Integer) read(first, "$.totalPages")).isEqualTo(2);
  }

  @Test
  void aPlansTermsAreShownWithoutRatingTables() throws Exception {
    UUID planId = publishedPlan(insurerId, planJson(ACACIA, code));
    MvcResult result = anonymousGet("/api/v1/plans/" + planId);
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat((String) read(result, "$.insurerCode")).isEqualTo(code);
    assertThat((String) read(result, "$.ageBasis")).isEqualTo("NEXT_BIRTHDAY");
    assertThat((String) read(result, "$.modules[0].options[0].benefits.limit.scope"))
        .isEqualTo("PER_MEMBER");
    assertThat(
            (String) read(result, "$.modules[0].options[0].benefits.children[1].children[0].code"))
        .isEqualTo("MENTAL_HEALTH");
    assertThat((String) read(result, "$.modules[1].options[0].benefits.copay.type"))
        .isEqualTo("PERCENT");
    assertThat((Integer) read(result, "$.eligibility.spouseAge.max")).isEqualTo(70);
    assertThat((Integer) read(result, "$.waitingRules[3].days")).isEqualTo(280);
    assertThat(result.getResponse().getContentAsString()).doesNotContain("ageBands", "rating");
  }

  @Test
  void draftsAndRetiredPlansAreNotFound() throws Exception {
    UUID draft = importDraft(insurerId, planJson(ACACIA, code, planCode("HIDDEN")));
    UUID retired = publishedPlan(insurerId, planJson(ACACIA, code));
    planAction(TestTokens.platformAdmin(), retired, "retire");
    for (UUID id : List.of(draft, retired, UUID.randomUUID())) {
      MvcResult result = anonymousGet("/api/v1/plans/" + id);
      assertThat(result.getResponse().getStatus()).isEqualTo(404);
      assertThat((String) read(result, "$.code")).isEqualTo("PLAN_NOT_FOUND");
    }
  }

  @Test
  void anUnknownInsurerIsNotFound() throws Exception {
    MvcResult result = anonymousGet("/api/v1/insurers/NO-SUCH-INSURER/plans");
    assertThat(result.getResponse().getStatus()).isEqualTo(404);
    assertThat((String) read(result, "$.code")).isEqualTo("INSURER_NOT_FOUND");
  }

  @Test
  void aMalformedPlanIdIsABadRequest() throws Exception {
    MvcResult result = anonymousGet("/api/v1/plans/not-a-uuid");
    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat((String) read(result, "$.code")).isEqualTo("VALIDATION_FAILED");
  }
}
