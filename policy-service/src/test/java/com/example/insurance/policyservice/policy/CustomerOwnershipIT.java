package com.example.insurance.policyservice.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.example.insurance.policyservice.support.TestTokens;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Ownership on every customer resource (CLAUDE.md): another customer gets 404, so they cannot even
 * learn that the resource exists. One test per endpoint.
 */
class CustomerOwnershipIT extends AbstractPolicyFlowIT {

  private UUID quoteId;
  private UUID policyId;
  private UUID intruderId;
  private String intruder;

  @BeforeEach
  void ownerAndIntruder() throws Exception {
    saveProfile(customer, PRINCIPAL_DOB, "MALE");
    quoteId = openQuote(customer);
    policyId =
        UUID.fromString(
            read(
                purchase(customer, purchaseBody(openQuote(customer), PRINCIPAL_DOB, true)),
                "$.id"));
    intruderId = UUID.randomUUID();
    intruder = TestTokens.customer(intruderId);
    saveProfile(intruder, PRINCIPAL_DOB, "MALE");
  }

  @Test
  void anotherCustomerCannotReadMyQuote() throws Exception {
    assertProblem(
        mockMvc.perform(withToken(get("/api/v1/quotes/" + quoteId), intruder)).andReturn(),
        404,
        "QUOTE_NOT_FOUND");
  }

  @Test
  void anotherCustomerCannotBuyFromMyQuote() throws Exception {
    assertProblem(
        purchase(intruder, purchaseBody(quoteId, PRINCIPAL_DOB, true)), 404, "QUOTE_NOT_FOUND");
  }

  @Test
  void anotherCustomerCannotReadMyPolicy() throws Exception {
    assertProblem(
        mockMvc.perform(withToken(get("/api/v1/policies/" + policyId), intruder)).andReturn(),
        404,
        "POLICY_NOT_FOUND");
  }

  @Test
  void anotherCustomerDoesNotSeeMyPoliciesInTheirList() throws Exception {
    MvcResult list = mockMvc.perform(withToken(get("/api/v1/policies"), intruder)).andReturn();
    List<String> ids = read(list, "$.content[*].id");
    assertThat(ids).doesNotContain(policyId.toString());
  }

  @Test
  void staffCannotReadCustomerPolicies() throws Exception {
    assertProblem(
        mockMvc
            .perform(withToken(get("/api/v1/policies/" + policyId), TestTokens.platformAdmin()))
            .andReturn(),
        403,
        "FORBIDDEN");
    assertProblem(
        mockMvc
            .perform(withToken(get("/api/v1/policies"), TestTokens.adjuster(insurerId)))
            .andReturn(),
        403,
        "FORBIDDEN");
  }
}
