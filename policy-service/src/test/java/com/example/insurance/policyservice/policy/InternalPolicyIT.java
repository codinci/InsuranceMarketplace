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
 * The internal policy read (PLAN 11, M5a) admits only claim-service's SERVICE token (ADR-014). Any
 * other caller gets 403, a missing token gets 401, and the customer's own token is refused too.
 */
class InternalPolicyIT extends AbstractPolicyFlowIT {

  private UUID policyId;

  @BeforeEach
  void aPendingPolicy() throws Exception {
    saveProfile(customer, PRINCIPAL_DOB, "MALE");
    policyId =
        UUID.fromString(
            read(
                purchase(customer, purchaseBody(openQuote(customer), PRINCIPAL_DOB, true)),
                "$.id"));
  }

  @Test
  void claimServiceReadsThePolicyWithItsFrozenTerms() throws Exception {
    MvcResult result = internal(TestTokens.service("claim-service"), policyId);

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    String returnedId = read(result, "$.policyId");
    String holder = read(result, "$.holderUserId");
    String returnedInsurer = read(result, "$.insurerId");
    String status = read(result, "$.status");
    Integer version = read(result, "$.policyVersion");
    Integer snapshotVersion = read(result, "$.termsSnapshot.snapshotVersion");
    List<Object> members = read(result, "$.members");
    assertThat(returnedId).isEqualTo(policyId.toString());
    assertThat(holder).isEqualTo(customerId.toString());
    assertThat(returnedInsurer).isEqualTo(insurerId.toString());
    assertThat(status).isEqualTo("PENDING_PAYMENT");
    assertThat(version).isEqualTo(1);
    assertThat(members).isNotEmpty();
    assertThat(snapshotVersion).isEqualTo(1);
  }

  @Test
  void anotherServiceIsForbidden() throws Exception {
    assertProblem(internal(TestTokens.service("payment-service"), policyId), 403, "FORBIDDEN");
    assertProblem(internal(TestTokens.service("policy-service"), policyId), 403, "FORBIDDEN");
  }

  @Test
  void aCustomerTokenIsForbidden() throws Exception {
    assertProblem(internal(customer, policyId), 403, "FORBIDDEN");
  }

  @Test
  void noTokenIsUnauthenticated() throws Exception {
    assertProblem(
        mockMvc.perform(get("/internal/policies/" + policyId)).andReturn(), 401, "UNAUTHENTICATED");
  }

  @Test
  void anUnknownPolicyIsNotFound() throws Exception {
    assertProblem(
        internal(TestTokens.service("claim-service"), UUID.randomUUID()), 404, "POLICY_NOT_FOUND");
  }

  private MvcResult internal(String token, UUID id) throws Exception {
    return mockMvc.perform(withToken(get("/internal/policies/" + id), token)).andReturn();
  }
}
