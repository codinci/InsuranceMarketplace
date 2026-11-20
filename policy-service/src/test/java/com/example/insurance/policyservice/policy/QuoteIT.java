package com.example.insurance.policyservice.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.example.insurance.policyservice.support.TestTokens;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** Quotes (PLAN 8.2): server-side price, eligibility, module selection and the start window. */
class QuoteIT extends AbstractPolicyFlowIT {

  @Test
  void aQuoteIsPricedOnTheServer() throws Exception {
    // A client-sent premium is ignored; the server computes it.
    String body =
        familyQuote(planId, START, IP_AND_OP)
            .replace("{\"planId\"", "{\"annualPremium\":1,\"planId\"");
    MvcResult result = quote(customer, body);
    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    assertThat(read(result, "$.premium.annualPremium").toString()).isEqualTo("50175.0");
    assertThat((String) read(result, "$.status")).isEqualTo("OPEN");
    assertThat((String) read(result, "$.insurerCode")).isEqualTo(insurerCode);
    List<String> modules = read(result, "$.premium.modules[*].module");
    assertThat(modules).containsExactly("INPATIENT", "OUTPATIENT");
  }

  @Test
  void anIneligibleMemberIsReportedWithItsIndex() throws Exception {
    String body =
        familyQuote(planId, START, IP_AND_OP)
            .replace(PRINCIPAL_DOB.toString(), START.minusYears(90).toString());
    MvcResult result = quote(customer, body);
    assertProblem(result, 422, "NOT_ELIGIBLE");
    assertThat((String) read(result, "$.errors[0].code")).isEqualTo("AGE_NOT_ELIGIBLE");
    assertThat((Integer) read(result, "$.errors[0].memberIndex")).isZero();
  }

  @Test
  void theMandatoryModuleMustBeSelected() throws Exception {
    MvcResult result = quote(customer, familyQuote(planId, START, "{\"OUTPATIENT\":\"OP-40K\"}"));
    assertProblem(result, 422, "MODULE_SELECTION_INVALID");
    assertThat((String) read(result, "$.errors[0].code")).isEqualTo("MANDATORY_MODULE_MISSING");
  }

  @Test
  void anUnknownOptionIsRefused() throws Exception {
    MvcResult result = quote(customer, familyQuote(planId, START, "{\"INPATIENT\":\"IP-9M\"}"));
    assertProblem(result, 422, "MODULE_SELECTION_INVALID");
    assertThat((String) read(result, "$.errors[0].code")).isEqualTo("OPTION_NOT_FOUND");
  }

  @Test
  void theStartDateMustBeFromTomorrowUpTo60Days() throws Exception {
    LocalDate today = TODAY;
    for (LocalDate start : List.of(today, today.minusDays(1), today.plusDays(61))) {
      assertProblem(
          quote(customer, familyQuote(planId, start, IP_AND_OP)), 422, "START_DATE_OUT_OF_RANGE");
    }
    assertThat(
            quote(customer, familyQuote(planId, today.plusDays(60), IP_AND_OP))
                .getResponse()
                .getStatus())
        .isEqualTo(201);
  }

  @Test
  void aPlanNotOnSaleCannotBeQuoted() throws Exception {
    UUID draft =
        importDraft(insurerId, planJson("acacia-standard.v1.json", insurerCode, version(2)));
    assertProblem(quote(customer, familyQuote(draft, START, IP_AND_OP)), 404, "PLAN_NOT_FOUND");
    assertProblem(
        quote(customer, familyQuote(UUID.randomUUID(), START, IP_AND_OP)), 404, "PLAN_NOT_FOUND");
  }

  @Test
  void aQuoteCanBeReadBackByItsOwnerOnly() throws Exception {
    UUID quoteId = openQuote(customer);
    MvcResult own =
        mockMvc.perform(withToken(get("/api/v1/quotes/" + quoteId), customer)).andReturn();
    assertThat(own.getResponse().getStatus()).isEqualTo(200);
    MvcResult other =
        mockMvc
            .perform(withToken(get("/api/v1/quotes/" + quoteId), TestTokens.customer()))
            .andReturn();
    assertProblem(other, 404, "QUOTE_NOT_FOUND");
  }

  @Test
  void staffCannotQuote() throws Exception {
    assertProblem(
        quote(TestTokens.platformAdmin(), familyQuote(planId, START, IP_AND_OP)), 403, "FORBIDDEN");
    assertProblem(
        quote(TestTokens.adjuster(insurerId), familyQuote(planId, START, IP_AND_OP)),
        403,
        "FORBIDDEN");
  }

  @Test
  void anInvalidRequestIsRefused() throws Exception {
    assertProblem(quote(customer, "{\"planId\":\"" + planId + "\"}"), 400, "VALIDATION_FAILED");
  }
}
