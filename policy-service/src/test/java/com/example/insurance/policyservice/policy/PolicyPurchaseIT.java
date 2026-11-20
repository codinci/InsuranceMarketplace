package com.example.insurance.policyservice.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.example.insurance.policyservice.support.TestTokens;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/** Buying a policy from a quote (PLAN 8.2, D35, D36). */
class PolicyPurchaseIT extends AbstractPolicyFlowIT {

  @Autowired JdbcTemplate jdbc;

  private UUID quoteId;

  @BeforeEach
  void profileAndQuote() throws Exception {
    saveProfile(customer, PRINCIPAL_DOB, "MALE");
    quoteId = openQuote(customer);
  }

  @Test
  void aPolicyIsCreatedAwaitingPaymentWithFrozenTerms() throws Exception {
    MvcResult result = purchase(customer, purchaseBody(quoteId, PRINCIPAL_DOB, true));
    assertThat(result.getResponse().getStatus())
        .as(result.getResponse().getContentAsString())
        .isEqualTo(201);
    assertThat((String) read(result, "$.status")).isEqualTo("PENDING_PAYMENT");
    assertThat((String) read(result, "$.policyNumber"))
        .matches("POL-" + insurerCode + "-\\d{4}-\\d{6}");
    assertThat((String) read(result, "$.endDate"))
        .isEqualTo(START.plusYears(1).minusDays(1).toString());
    assertThat(read(result, "$.premium.annualPremium").toString()).isEqualTo("50175.0");
    List<String> numbers = read(result, "$.members[*].memberNumber");
    assertThat(numbers).hasSize(3).allMatch(n -> n.matches("[A-Z0-9]{1,3}-\\d{6}-0[1-3]"));
    List<String> names = read(result, "$.members[*].firstName");
    assertThat(names).containsExactly("Juma", "Amina", "Neema");
    List<String> modules = read(result, "$.termsSnapshot.modules[*].option");
    assertThat(modules).containsExactly("IP-200K", "OP-40K");
    assertThat((Integer) read(result, "$.termsSnapshot.snapshotVersion")).isEqualTo(1);

    MvcResult quote =
        mockMvc.perform(withToken(get("/api/v1/quotes/" + quoteId), customer)).andReturn();
    assertThat((String) read(quote, "$.status")).isEqualTo("USED");
  }

  @Test
  void aQuoteCanBeBoughtOnlyOnce() throws Exception {
    purchase(customer, purchaseBody(quoteId, PRINCIPAL_DOB, true));
    assertProblem(
        purchase(customer, purchaseBody(quoteId, PRINCIPAL_DOB, true)), 409, "QUOTE_NOT_OPEN");
  }

  @Test
  void aProfileIsRequired() throws Exception {
    UUID noProfile = UUID.randomUUID();
    String token = TestTokens.customer(noProfile);
    UUID theirQuote = openQuote(token);
    assertProblem(
        purchase(token, purchaseBody(theirQuote, PRINCIPAL_DOB, true)), 409, "PROFILE_REQUIRED");
  }

  @Test
  void theMembersMustBeTheQuotedOnes() throws Exception {
    String changed =
        purchaseBody(quoteId, PRINCIPAL_DOB, true)
            .replace(CHILD_DOB.toString(), CHILD_DOB.minusDays(1).toString());
    assertProblem(purchase(customer, changed), 422, "MEMBERS_DIFFER_FROM_QUOTE");
  }

  @Test
  void thePrincipalMustBeTheProfileHolder() throws Exception {
    saveProfile(customer, PRINCIPAL_DOB.minusYears(1), "MALE");
    assertProblem(
        purchase(customer, purchaseBody(quoteId, PRINCIPAL_DOB, true)),
        422,
        "PRINCIPAL_NOT_HOLDER");
  }

  @Test
  void theDeclarationMustBeAccepted() throws Exception {
    assertProblem(
        purchase(customer, purchaseBody(quoteId, PRINCIPAL_DOB, false)),
        422,
        "DECLARATION_REQUIRED");
  }

  @Test
  void anExpiredQuoteCannotBeBought() throws Exception {
    jdbc.update(
        "update quotes set valid_until = now() - interval '1 minute' where id = ?", quoteId);
    assertProblem(
        purchase(customer, purchaseBody(quoteId, PRINCIPAL_DOB, true)), 409, "QUOTE_EXPIRED");
    MvcResult quote =
        mockMvc.perform(withToken(get("/api/v1/quotes/" + quoteId), customer)).andReturn();
    assertThat((String) read(quote, "$.status")).isEqualTo("EXPIRED");
  }

  @Test
  void aStartDateThatHasArrivedNeedsANewQuote() throws Exception {
    jdbc.update("update quotes set start_date = ? where id = ?", TODAY, quoteId);
    assertProblem(
        purchase(customer, purchaseBody(quoteId, PRINCIPAL_DOB, true)),
        422,
        "START_DATE_OUT_OF_RANGE");
  }

  @Test
  void aQuoteStaysBuyableAfterItsPlanVersionIsRetired() throws Exception {
    planAction(TestTokens.platformAdmin(), planId, "retire");
    MvcResult result = purchase(customer, purchaseBody(quoteId, PRINCIPAL_DOB, true));
    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    assertThat((Integer) read(result, "$.planVersion")).isEqualTo(1);
  }

  @Test
  void myPoliciesAreListedAndReadable() throws Exception {
    UUID policyId =
        UUID.fromString(
            read(purchase(customer, purchaseBody(quoteId, PRINCIPAL_DOB, true)), "$.id"));
    MvcResult list = mockMvc.perform(withToken(get("/api/v1/policies"), customer)).andReturn();
    List<String> ids = read(list, "$.content[*].id");
    assertThat(ids).containsExactly(policyId.toString());
    MvcResult one =
        mockMvc.perform(withToken(get("/api/v1/policies/" + policyId), customer)).andReturn();
    assertThat(one.getResponse().getStatus()).isEqualTo(200);
    assertThat((String) read(one, "$.members[0].relationship")).isEqualTo("PRINCIPAL");
  }
}
