package com.example.insurance.policyservice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.insurance.policyservice.catalogue.CatalogueFixtures;
import com.example.insurance.policyservice.support.TestJwtConfig;
import com.example.insurance.policyservice.support.TestTokens;
import com.jayway.jsonpath.JsonPath;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Base for integration tests: one Postgres 17 container for the whole run (the compose major
 * version), MockMvc, and helpers that call the API as a client would. Each test creates its own
 * insurers with unique codes, so tests never see each other's data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
public abstract class AbstractPolicyIT {

  @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.12-alpine");

  static {
    POSTGRES.start();
  }

  @Autowired protected MockMvc mockMvc;

  protected static String uniqueCode(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
  }

  /** Creates an insurer through the API as a platform admin and returns its id. */
  protected UUID createInsurer(String code) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                withToken(post("/api/v1/admin/insurers"), TestTokens.platformAdmin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"" + code + "\",\"name\":\"Test insurer\"}"))
            .andReturn();
    if (result.getResponse().getStatus() != 201) {
      throw new IllegalStateException(result.getResponse().getContentAsString());
    }
    return UUID.fromString(read(result, "$.id"));
  }

  /** A valid fixture plan, re-labelled for {@code insurerCode}, with optional edits. */
  protected static String planJson(String fixture, String insurerCode, Consumer<JsonNode> edits) {
    JsonNode plan = CatalogueFixtures.validPlanJson(fixture);
    ((ObjectNode) plan.get("insurer")).put("code", insurerCode);
    edits.accept(plan);
    return plan.toString();
  }

  protected static String planJson(String fixture, String insurerCode) {
    return planJson(fixture, insurerCode, plan -> {});
  }

  protected static Consumer<JsonNode> version(int version) {
    return plan -> ((ObjectNode) plan.get("plan")).put("version", version);
  }

  protected MvcResult importPlan(String token, UUID insurerId, String json, boolean dryRun)
      throws Exception {
    return mockMvc
        .perform(
            withToken(
                    post("/api/v1/admin/insurers/" + insurerId + "/plans/import")
                        .param("dryRun", String.valueOf(dryRun)),
                    token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andReturn();
  }

  /** Imports as a platform admin and returns the new plan id. */
  protected UUID importDraft(UUID insurerId, String json) throws Exception {
    MvcResult result = importPlan(TestTokens.platformAdmin(), insurerId, json, false);
    if (result.getResponse().getStatus() != 201) {
      throw new IllegalStateException(result.getResponse().getContentAsString());
    }
    return UUID.fromString(read(result, "$.plan.id"));
  }

  protected MvcResult planAction(String token, UUID planId, String action) throws Exception {
    return mockMvc
        .perform(withToken(post("/api/v1/admin/plans/" + planId + "/" + action), token))
        .andReturn();
  }

  protected UUID publishedPlan(UUID insurerId, String json) throws Exception {
    UUID planId = importDraft(insurerId, json);
    MvcResult result = planAction(TestTokens.platformAdmin(), planId, "publish");
    if (result.getResponse().getStatus() != 200) {
      throw new IllegalStateException(result.getResponse().getContentAsString());
    }
    return planId;
  }

  protected static MockHttpServletRequestBuilder withToken(
      MockHttpServletRequestBuilder request, String token) {
    return token == null ? request : request.header("Authorization", "Bearer " + token);
  }

  protected static <T> T read(MvcResult result, String path) throws Exception {
    return JsonPath.read(result.getResponse().getContentAsString(), path);
  }
}
