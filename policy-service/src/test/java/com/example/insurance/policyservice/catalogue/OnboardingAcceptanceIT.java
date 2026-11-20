package com.example.insurance.policyservice.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.insurance.policyservice.AbstractPolicyIT;
import com.example.insurance.policyservice.catalogue.domain.PlanDocument;
import com.example.insurance.policyservice.catalogue.domain.Relationship;
import com.example.insurance.policyservice.catalogue.domain.Sex;
import com.example.insurance.policyservice.catalogue.rating.PremiumBreakdown;
import com.example.insurance.policyservice.catalogue.rating.PremiumCalculator;
import com.example.insurance.policyservice.catalogue.rating.RatedMember;
import com.example.insurance.policyservice.catalogue.repository.PlanVersionRepository;
import com.example.insurance.policyservice.catalogue.service.StoredPlans;
import com.example.insurance.policyservice.support.TestTokens;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * The M3b acceptance test (PLAN 17): a third insurer is onboarded with only a JSON file. Everything
 * below comes from {@code catalogue/onboarding/mvule-care.v1.json} and its expected premium; no
 * production code knows this insurer.
 */
class OnboardingAcceptanceIT extends AbstractPolicyIT {

  private static final String DOCUMENT = "onboarding/mvule-care.v1.json";

  @Autowired PlanVersionRepository plans;
  @Autowired StoredPlans stored;
  @Autowired PremiumCalculator calculator;

  @Test
  void aThirdInsurerIsOnboardedWithOnlyAJsonFile() throws Exception {
    String document = CatalogueFixtures.resource(DOCUMENT);
    JsonNode tree = CatalogueFixtures.MAPPER.readTree(document);
    String code = tree.at("/insurer/code").asString();
    String name = tree.at("/insurer/name").asString();

    // 1. The platform admin creates the insurer named in the file.
    MvcResult created =
        mockMvc
            .perform(
                withToken(post("/api/v1/admin/insurers"), TestTokens.platformAdmin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        CatalogueFixtures.MAPPER
                            .createObjectNode()
                            .put("code", code)
                            .put("name", name)
                            .toString()))
            .andReturn();
    assertThat(created.getResponse().getStatus()).isEqualTo(201);
    UUID insurerId = UUID.fromString(read(created, "$.id"));

    // 2. A dry run passes, then the file is imported and published.
    assertThat(
            importPlan(TestTokens.platformAdmin(), insurerId, document, true)
                .getResponse()
                .getStatus())
        .isEqualTo(200);
    UUID planId = publishedPlan(insurerId, document);

    // 3. Customers can browse it.
    MvcResult listed = mockMvc.perform(get("/api/v1/insurers/" + code + "/plans")).andReturn();
    List<String> ids = read(listed, "$.content[*].id");
    assertThat(ids).containsExactly(planId.toString());
    MvcResult detail = mockMvc.perform(get("/api/v1/plans/" + planId)).andReturn();
    assertThat(detail.getResponse().getStatus()).isEqualTo(200);
    assertThat((String) read(detail, "$.name")).isEqualTo("Mvule Care");

    // 4. The stored plan prices to the expected premium.
    JsonNode expected =
        CatalogueFixtures.MAPPER.readTree(
            CatalogueFixtures.resource("onboarding/mvule-care.expected.json"));
    PlanDocument storedPlan = stored.document(plans.findById(planId).orElseThrow());
    PremiumBreakdown breakdown =
        calculator.price(
            storedPlan.plan(),
            selection(expected.get("selection")),
            members(expected.get("members")),
            LocalDate.parse(expected.get("startDate").asString()));
    assertThat(breakdown.annualPremium())
        .isEqualTo(new BigDecimal(expected.at("/expected/annualPremium").asString()));
  }

  private static Map<String, String> selection(JsonNode node) {
    Map<String, String> selection = new LinkedHashMap<>();
    node.properties().forEach(e -> selection.put(e.getKey(), e.getValue().asString()));
    return selection;
  }

  private static List<RatedMember> members(JsonNode node) {
    return node.valueStream()
        .map(m ->
            new RatedMember(
                Relationship.valueOf(m.get("relationship").asString()),
                Sex.valueOf(m.get("sex").asString()),
                LocalDate.parse(m.get("dateOfBirth").asString())))
        .toList();
  }
}
