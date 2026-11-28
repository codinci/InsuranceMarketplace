package com.example.insurance.policyservice.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.insurance.events.EventTypes;
import com.example.insurance.policyservice.policy.messaging.Topics;
import com.example.insurance.policyservice.policy.service.PolicyExpiryJob;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.LocalDate;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Active policies expire the day after their last day of cover, once (PLAN 7, M8b). */
class PolicyExpiryIT extends AbstractPolicyFlowIT {

  private static final LocalDate END = START.plusYears(1).minusDays(1);

  @Autowired PolicyExpiryJob job;

  @BeforeEach
  void profile() throws Exception {
    PAYMENTS.up();
    saveProfile(customer, PRINCIPAL_DOB, "MALE");
  }

  @Test
  void aPolicyIsStillActiveOnItsLastDayAndExpiresTheDayAfter() throws Exception {
    Bought policy = buyPolicy();
    pay(policy);

    try (KafkaConsumer<String, String> policyEvents = consumerOf(Topics.POLICY_EVENTS)) {
      job.expireDue(END);
      assertThat(statusOf(policy.policyId())).isEqualTo("ACTIVE");

      job.expireDue(END.plusDays(1));
      assertThat(statusOf(policy.policyId())).isEqualTo("EXPIRED");

      String expired =
          awaitMessage(policyEvents, policy.policyId().toString(), EventTypes.POLICY_EXPIRED);
      assertThat(JsonPath.<String>read(expired, "$.payload.expiredOn")).isEqualTo(END.toString());
      assertThat(JsonPath.<Integer>read(expired, "$.payload.policyVersion")).isEqualTo(3);
      assertThat(JsonPath.<String>read(expired, "$.payload.holderUserId"))
          .isEqualTo(customerId.toString());
      assertThat(JsonPath.<String>read(expired, "$.insurerId")).isEqualTo(insurerId.toString());
    }
  }

  @Test
  void aSecondRunSendsNoSecondEvent() throws Exception {
    Bought policy = buyPolicy();
    pay(policy);

    try (KafkaConsumer<String, String> policyEvents = consumerOf(Topics.POLICY_EVENTS)) {
      job.expireDue(END.plusDays(1));
      job.expireDue(END.plusDays(2));

      assertThat(
              messages(
                  policyEvents,
                  policy.policyId().toString(),
                  EventTypes.POLICY_EXPIRED,
                  Duration.ofSeconds(5)))
          .hasSize(1);
    }
  }

  @Test
  void cancelledAndUnpaidPoliciesNeverExpire() throws Exception {
    Bought cancelled = buyPolicy();
    pay(cancelled);
    mockMvc
        .perform(withToken(post("/api/v1/policies/" + cancelled.policyId() + "/cancel"), customer))
        .andReturn();
    Bought unpaid = buyPolicy();

    job.expireDue(END.plusDays(1));

    assertThat(statusOf(cancelled.policyId())).isEqualTo("CANCELLED");
    assertThat(statusOf(unpaid.policyId())).isEqualTo("PENDING_PAYMENT");
  }
}
