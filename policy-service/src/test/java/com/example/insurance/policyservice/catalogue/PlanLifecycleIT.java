package com.example.insurance.policyservice.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.example.insurance.policyservice.AbstractPolicyIT;
import com.example.insurance.policyservice.support.TestTokens;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/** Publishing and retiring (ADR-010, D33) and immutability after publish (CLAUDE.md, M3b). */
class PlanLifecycleIT extends AbstractPolicyIT {

  private static final String BAOBAB = "baobab-family.v1.json";

  @Autowired JdbcTemplate jdbc;

  private String code;
  private UUID insurerId;

  @BeforeEach
  void newInsurer() throws Exception {
    code = uniqueCode("LIFE");
    insurerId = createInsurer(code);
  }

  private String statusOf(UUID planId) {
    return jdbc.queryForObject("select status from plans where id = ?", String.class, planId);
  }

  @Test
  void publishingADraftPutsItOnSale() throws Exception {
    UUID planId = importDraft(insurerId, planJson(BAOBAB, code));
    MvcResult result = planAction(TestTokens.platformAdmin(), planId, "publish");
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat((String) read(result, "$.status")).isEqualTo("PUBLISHED");
    assertThat((String) read(result, "$.publishedAt")).isNotBlank();
  }

  @Test
  void aPublishedVersionCannotBePublishedAgain() throws Exception {
    UUID planId = publishedPlan(insurerId, planJson(BAOBAB, code));
    MvcResult again = planAction(TestTokens.platformAdmin(), planId, "publish");
    assertThat(again.getResponse().getStatus()).isEqualTo(409);
    assertThat((String) read(again, "$.code")).isEqualTo("PLAN_NOT_DRAFT");
  }

  @Test
  void publishingANewVersionRetiresTheOldOne() throws Exception {
    UUID v1 = publishedPlan(insurerId, planJson(BAOBAB, code));
    UUID v2 = publishedPlan(insurerId, planJson(BAOBAB, code, version(2)));
    assertThat(statusOf(v1)).isEqualTo("RETIRED");
    assertThat(statusOf(v2)).isEqualTo("PUBLISHED");
  }

  @Test
  void anOlderVersionCannotReplaceANewerPublishedOne() throws Exception {
    UUID v1 = importDraft(insurerId, planJson(BAOBAB, code));
    publishedPlan(insurerId, planJson(BAOBAB, code, version(2)));
    MvcResult result = planAction(TestTokens.platformAdmin(), v1, "publish");
    assertThat(result.getResponse().getStatus()).isEqualTo(409);
    assertThat((String) read(result, "$.code")).isEqualTo("NEWER_VERSION_PUBLISHED");
    assertThat(statusOf(v1)).isEqualTo("DRAFT");
  }

  @Test
  void retiringTakesAPlanOffSale() throws Exception {
    UUID planId = publishedPlan(insurerId, planJson(BAOBAB, code));
    MvcResult result = planAction(TestTokens.platformAdmin(), planId, "retire");
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat((String) read(result, "$.status")).isEqualTo("RETIRED");
  }

  @Test
  void onlyAPublishedPlanCanBeRetired() throws Exception {
    UUID planId = importDraft(insurerId, planJson(BAOBAB, code));
    MvcResult result = planAction(TestTokens.platformAdmin(), planId, "retire");
    assertThat(result.getResponse().getStatus()).isEqualTo(409);
    assertThat((String) read(result, "$.code")).isEqualTo("PLAN_NOT_PUBLISHED");
  }

  @Test
  void anUnknownPlanIsNotFound() throws Exception {
    MvcResult result = planAction(TestTokens.platformAdmin(), UUID.randomUUID(), "publish");
    assertThat(result.getResponse().getStatus()).isEqualTo(404);
    assertThat((String) read(result, "$.code")).isEqualTo("PLAN_NOT_FOUND");
  }

  @Test
  void theDatabaseRefusesToChangeAPublishedPlan() throws Exception {
    UUID planId = publishedPlan(insurerId, planJson(BAOBAB, code));
    assertThatThrownBy(
            () -> jdbc.update("update plans set definition = '{}'::jsonb where id = ?", planId))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("immutable");
    assertThatThrownBy(() -> jdbc.update("delete from plans where id = ?", planId))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("cannot be deleted");
    assertThatThrownBy(() -> jdbc.update("update plans set status = 'DRAFT' where id = ?", planId))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void theDatabaseRefusesToRepublishARetiredPlan() throws Exception {
    UUID planId = publishedPlan(insurerId, planJson(BAOBAB, code));
    planAction(TestTokens.platformAdmin(), planId, "retire");
    assertThatThrownBy(
            () -> jdbc.update("update plans set status = 'PUBLISHED' where id = ?", planId))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("retired");
  }

  @Test
  void theListCanBeFilteredByStatus() throws Exception {
    publishedPlan(insurerId, planJson(BAOBAB, code));
    importDraft(insurerId, planJson(BAOBAB, code, version(2)));
    MvcResult drafts =
        mockMvc
            .perform(
                withToken(
                    get("/api/v1/admin/plans")
                        .param("insurerId", insurerId.toString())
                        .param("status", "DRAFT"),
                    TestTokens.platformAdmin()))
            .andReturn();
    assertThat(((Number) read(drafts, "$.totalElements")).longValue()).isEqualTo(1);
    assertThat((Integer) read(drafts, "$.content[0].version")).isEqualTo(2);
  }

  @Test
  void badPagingIsRefused() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                withToken(
                    get("/api/v1/admin/plans").param("size", "500"), TestTokens.platformAdmin()))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat((String) read(result, "$.code")).isEqualTo("VALIDATION_FAILED");
  }
}
