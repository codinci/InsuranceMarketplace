package com.example.insurance.claimservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.insurance.claimservice.support.TestSecurityConfig;
import com.example.insurance.claimservice.support.TestTokens;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The policy read model end to end (PLAN 11, M5a): events from Kafka, the duplicate and
 * out-of-order rules, ownership, and the fallback to policy-service with its 503 and Retry-After.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
class ClaimPolicyIT extends AbstractClaimIT {

  @Test
  void anActivatedPolicyAppearsWithItsTermsForItsHolderOnly() throws Exception {
    UUID policyId = UUID.randomUUID();
    UUID holder = UUID.randomUUID();
    publish(UUID.randomUUID(), "PolicyActivated", policyId, activated(policyId, holder, 2));

    String mine = TestTokens.customer(holder);
    awaitStatus(mine, policyId, "ACTIVE");

    MvcResult one = getWith(mine, "/api/v1/claims/policies/" + policyId);
    assertThat(this.<Object>read(one, "$.policyId")).isEqualTo(policyId.toString());
    assertThat(this.<Object>read(one, "$.termsSnapshot.planCode")).isEqualTo("STANDARD");
    assertThat(this.<Object>read(one, "$.members[0].relationship")).isEqualTo("PRINCIPAL");

    MvcResult list = getWith(mine, "/api/v1/claims/policies");
    List<String> ids = read(list, "$.content[*].policyId");
    assertThat(ids).contains(policyId.toString());
  }

  @Test
  void aRedeliveredActivationIsAppliedOnce() throws Exception {
    UUID policyId = UUID.randomUUID();
    UUID holder = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    publish(eventId, "PolicyActivated", policyId, activated(policyId, holder, 2));
    publish(eventId, "PolicyActivated", policyId, activated(policyId, holder, 2));

    String mine = TestTokens.customer(holder);
    awaitStatus(mine, policyId, "ACTIVE");
    assertThat(
            this.<Object>read(
                getWith(mine, "/api/v1/claims/policies/" + policyId), "$.policyVersion"))
        .isEqualTo(2);
  }

  @Test
  void aCancellationThatArrivesBeforeTheActivationLeavesTheCancellation() throws Exception {
    UUID policyId = UUID.randomUUID();
    UUID holder = UUID.randomUUID();
    publish(UUID.randomUUID(), "PolicyCancelled", policyId, cancelled(policyId, 2));
    publish(UUID.randomUUID(), "PolicyActivated", policyId, activated(policyId, holder, 2));

    // The row has no holder while cancelled-only, so the customer gets 404. This is a negative
    // check:
    // the wait gives both events time to be consumed before the answer is read.
    Thread.sleep(2_000);
    MvcResult asHolder =
        getWith(TestTokens.customer(holder), "/api/v1/claims/policies/" + policyId);
    assertThat(asHolder.getResponse().getStatus()).isEqualTo(404);
  }

  @Test
  void aStaleActivationAfterACancellationIsIgnored() throws Exception {
    UUID policyId = UUID.randomUUID();
    UUID holder = UUID.randomUUID();
    publish(UUID.randomUUID(), "PolicyActivated", policyId, activated(policyId, holder, 2));
    publish(UUID.randomUUID(), "PolicyCancelled", policyId, cancelled(policyId, 3));
    String mine = TestTokens.customer(holder);
    awaitStatus(mine, policyId, "CANCELLED");

    publish(UUID.randomUUID(), "PolicyActivated", policyId, activated(policyId, holder, 2));
    Thread.sleep(1_000);

    assertThat(this.<Object>read(getWith(mine, "/api/v1/claims/policies/" + policyId), "$.status"))
        .isEqualTo("CANCELLED");
  }

  @Test
  void anotherCustomerGetsNotFoundAndDoesNotSeeThePolicy() throws Exception {
    UUID policyId = UUID.randomUUID();
    UUID holder = UUID.randomUUID();
    publish(UUID.randomUUID(), "PolicyActivated", policyId, activated(policyId, holder, 2));
    awaitStatus(TestTokens.customer(holder), policyId, "ACTIVE");

    String intruder = TestTokens.customer(UUID.randomUUID());
    MvcResult one = getWith(intruder, "/api/v1/claims/policies/" + policyId);
    assertThat(one.getResponse().getStatus()).isEqualTo(404);
    assertThat(this.<Object>read(one, "$.code")).isEqualTo("POLICY_NOT_FOUND");

    List<String> ids = read(getWith(intruder, "/api/v1/claims/policies"), "$.content[*].policyId");
    assertThat(ids).doesNotContain(policyId.toString());
  }

  @Test
  void aPolicyNotYetInTheReadModelIsFetchedFromPolicyService() throws Exception {
    UUID policyId = UUID.randomUUID();
    UUID holder = UUID.randomUUID();
    POLICY_SERVICE.givenPolicy(policyId, fallbackPolicyJson(policyId, holder));

    MvcResult result = getWith(TestTokens.customer(holder), "/api/v1/claims/policies/" + policyId);

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(this.<Object>read(result, "$.status")).isEqualTo("PENDING_PAYMENT");
  }

  @Test
  void theFallbackAlsoAnswersNotFoundForAnotherCustomersPolicy() throws Exception {
    UUID policyId = UUID.randomUUID();
    POLICY_SERVICE.givenPolicy(policyId, fallbackPolicyJson(policyId, UUID.randomUUID()));

    MvcResult result =
        getWith(TestTokens.customer(UUID.randomUUID()), "/api/v1/claims/policies/" + policyId);

    assertThat(result.getResponse().getStatus()).isEqualTo(404);
  }

  @Test
  void anUnknownPolicyIsNotFound() throws Exception {
    MvcResult result =
        getWith(
            TestTokens.customer(UUID.randomUUID()), "/api/v1/claims/policies/" + UUID.randomUUID());

    assertThat(result.getResponse().getStatus()).isEqualTo(404);
  }

  @Test
  void aServiceTokenCannotUseTheCustomerEndpoints() throws Exception {
    MvcResult result = getWith(TestTokens.service("policy-service"), "/api/v1/claims/policies");

    assertThat(result.getResponse().getStatus()).isEqualTo(403);
  }

  @Test
  void whenPolicyServiceIsDownTheCustomerGets503WithRetryAfter() throws Exception {
    UUID policyId = UUID.randomUUID();
    UUID holder = UUID.randomUUID();
    POLICY_SERVICE.down(true);
    try {
      MvcResult result =
          getWith(TestTokens.customer(holder), "/api/v1/claims/policies/" + policyId);

      assertThat(result.getResponse().getStatus()).isEqualTo(503);
      assertThat(result.getResponse().getHeader("Retry-After")).isEqualTo("30");
      assertThat(this.<Object>read(result, "$.code")).isEqualTo("POLICY_UNAVAILABLE");
    } finally {
      POLICY_SERVICE.down(false);
    }
  }
}
