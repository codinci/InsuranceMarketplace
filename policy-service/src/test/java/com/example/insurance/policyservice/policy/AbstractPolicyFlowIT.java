package com.example.insurance.policyservice.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.example.insurance.events.EventTypes;
import com.example.insurance.events.payment.PremiumPaid;
import com.example.insurance.policyservice.AbstractPolicyIT;
import com.example.insurance.policyservice.policy.messaging.Topics;
import com.example.insurance.policyservice.support.TestTokens;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * A published Acacia STANDARD plan and a customer with a profile. Members are aged relative to the
 * start date so the premium matches the golden case (50,175.00) on any day: next-birthday ages 37,
 * 35 and 12.
 */
abstract class AbstractPolicyFlowIT extends AbstractPolicyIT {

  /** Today in the business calendar (D38), as the service sees it. */
  protected static final LocalDate TODAY = LocalDate.now(java.time.ZoneId.of("Africa/Nairobi"));

  protected static final LocalDate START = TODAY.plusDays(10);
  protected static final LocalDate PRINCIPAL_DOB = START.minusYears(37).plusDays(1);
  protected static final LocalDate SPOUSE_DOB = START.minusYears(35);
  protected static final LocalDate CHILD_DOB = START.minusYears(12).plusDays(1);

  protected String insurerCode;
  protected UUID insurerId;
  protected UUID planId;
  protected UUID customerId;
  protected String customer;

  @BeforeEach
  void planAndCustomer() throws Exception {
    insurerCode = uniqueCode("POL");
    insurerId = createInsurer(insurerCode);
    planId = publishedPlan(insurerId, planJson("acacia-standard.v1.json", insurerCode));
    customerId = UUID.randomUUID();
    customer = TestTokens.customer(customerId);
  }

  protected MvcResult saveProfile(String token, LocalDate dateOfBirth, String sex)
      throws Exception {
    String body =
        "{\"firstName\":\"Amina\",\"lastName\":\"Otieno\",\"dateOfBirth\":\""
            + dateOfBirth
            + "\",\"sex\":\""
            + sex
            + "\",\"nationalId\":\"A1234567\",\"phone\":\"+254700000000\"}";
    return mockMvc
        .perform(
            withToken(put("/api/v1/me/profile"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andReturn();
  }

  protected static String familyQuote(UUID plan, LocalDate start, String modules) {
    return "{\"planId\":\""
        + plan
        + "\",\"coverType\":\"FAMILY\",\"startDate\":\""
        + start
        + "\",\"members\":["
        + member("PRINCIPAL", "MALE", PRINCIPAL_DOB)
        + ","
        + member("SPOUSE", "FEMALE", SPOUSE_DOB)
        + ","
        + member("CHILD", "FEMALE", CHILD_DOB)
        + "],\"selectedModules\":"
        + modules
        + "}";
  }

  protected static String member(String relationship, String sex, LocalDate dob) {
    return "{\"relationship\":\""
        + relationship
        + "\",\"sex\":\""
        + sex
        + "\",\"dateOfBirth\":\""
        + dob
        + "\",\"student\":false}";
  }

  protected static final String IP_AND_OP = "{\"INPATIENT\":\"IP-200K\",\"OUTPATIENT\":\"OP-40K\"}";

  protected MvcResult quote(String token, String body) throws Exception {
    return mockMvc
        .perform(
            withToken(post("/api/v1/quotes"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andReturn();
  }

  protected UUID openQuote(String token) throws Exception {
    MvcResult result = quote(token, familyQuote(planId, START, IP_AND_OP));
    if (result.getResponse().getStatus() != 201) {
      throw new IllegalStateException(result.getResponse().getContentAsString());
    }
    return UUID.fromString(read(result, "$.id"));
  }

  protected static String purchaseBody(UUID quoteId, LocalDate principalDob, boolean accepted) {
    return "{\"quoteId\":\""
        + quoteId
        + "\",\"members\":["
        + named("PRINCIPAL", "MALE", principalDob, "Juma")
        + ","
        + named("SPOUSE", "FEMALE", SPOUSE_DOB, "Amina")
        + ","
        + named("CHILD", "FEMALE", CHILD_DOB, "Neema")
        + "],\"declaration\":{\"preExistingConditions\":false,\"accepted\":"
        + accepted
        + "}}";
  }

  protected static String named(String relationship, String sex, LocalDate dob, String first) {
    return "{\"relationship\":\""
        + relationship
        + "\",\"sex\":\""
        + sex
        + "\",\"dateOfBirth\":\""
        + dob
        + "\",\"student\":false,\"firstName\":\""
        + first
        + "\",\"lastName\":\"Otieno\"}";
  }

  protected MvcResult purchase(String token, String body) throws Exception {
    return mockMvc
        .perform(
            withToken(post("/api/v1/policies"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andReturn();
  }

  /** A policy bought by {@link #customer}: its id, premium payment and number. */
  protected record Bought(UUID policyId, UUID paymentId, String policyNumber) {}

  /** Buys a policy from a fresh quote; it awaits payment. Needs the customer's profile. */
  protected Bought buyPolicy() throws Exception {
    MvcResult result = purchase(customer, purchaseBody(openQuote(customer), PRINCIPAL_DOB, true));
    if (result.getResponse().getStatus() != 201) {
      throw new IllegalStateException(result.getResponse().getContentAsString());
    }
    return new Bought(
        UUID.fromString(read(result, "$.id")),
        UUID.fromString(read(result, "$.paymentId")),
        read(result, "$.policyNumber"));
  }

  /** Announces the premium as paid and waits until the policy is ACTIVE. */
  protected void pay(Bought policy) throws Exception {
    publish(
        Topics.PAYMENT_EVENTS,
        policy.policyId().toString(),
        EventTypes.PREMIUM_PAID,
        UUID.randomUUID(),
        insurerId,
        new PremiumPaid(
            policy.paymentId(),
            policy.policyId(),
            policy.policyNumber(),
            customerId,
            new BigDecimal("1.00"),
            "KES",
            "MOCK-1"));
    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    while (!"ACTIVE".equals(statusOf(policy.policyId())) && System.nanoTime() < deadline) {
      Thread.sleep(200);
    }
    assertThat(statusOf(policy.policyId())).isEqualTo("ACTIVE");
  }

  /** The policy's status as its holder sees it. */
  protected String statusOf(UUID policyId) throws Exception {
    return read(
        mockMvc.perform(withToken(get("/api/v1/policies/" + policyId), customer)).andReturn(),
        "$.status");
  }

  protected static void assertProblem(MvcResult result, int status, String code) throws Exception {
    assertThat(result.getResponse().getStatus())
        .as(result.getResponse().getContentAsString())
        .isEqualTo(status);
    assertThat((String) read(result, "$.code")).isEqualTo(code);
  }
}
