package com.example.insurance.events.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.insurance.events.policy.TermsSnapshot.BenefitTerms;
import com.example.insurance.events.policy.TermsSnapshot.CancellationTerms;
import com.example.insurance.events.policy.TermsSnapshot.CopayTerms;
import com.example.insurance.events.policy.TermsSnapshot.LimitTerms;
import com.example.insurance.events.policy.TermsSnapshot.ModuleTerms;
import com.example.insurance.events.policy.TermsSnapshot.WaitingRuleTerms;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Locks the snapshot's JSON shape (ADR-011): claim-service reads exactly these names. */
class TermsSnapshotTest {

  private final ObjectMapper mapper =
      JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

  private TermsSnapshot sample() {
    BenefitTerms consultation =
        new BenefitTerms(
            "CONSULTATION",
            "REIMBURSEMENT",
            new LimitTerms(
                new BigDecimal("40000"), "PER_YEAR", "PER_MEMBER", new BigDecimal("2500"), null),
            null,
            List.of("RECEIPT"),
            false,
            List.of());
    BenefitTerms outpatient =
        new BenefitTerms(
            "OUTPATIENT",
            "REIMBURSEMENT",
            new LimitTerms(new BigDecimal("40000"), "PER_YEAR", "PER_MEMBER", null, null),
            new CopayTerms("PERCENT", new BigDecimal("0.1")),
            List.of(),
            false,
            List.of(consultation));
    return new TermsSnapshot(
        TermsSnapshot.CURRENT_VERSION,
        "ACACIA",
        UUID.fromString("5b1c2f0e-7a8d-4c3b-9e2f-1a2b3c4d5e6f"),
        "STANDARD",
        1,
        "KES",
        "NEXT_BIRTHDAY",
        List.of(new ModuleTerms("OUTPATIENT", "OP-40K", outpatient)),
        List.of(
            new WaitingRuleTerms("GENERAL_ILLNESS", 30, 0, "ILLNESS", null, List.of(), List.of())),
        List.of("Cosmetic treatment"),
        90,
        new CancellationTerms(30, "PRO_RATA", 6, true));
  }

  @Test
  void roundTripKeepsEveryField() {
    TermsSnapshot original = sample();
    TermsSnapshot readBack =
        mapper.readValue(mapper.writeValueAsString(original), TermsSnapshot.class);
    assertThat(readBack).isEqualTo(original);
  }

  @Test
  void jsonUsesTheContractFieldNames() {
    String json = mapper.writeValueAsString(sample());
    assertThat(json)
        .contains("\"snapshotVersion\":1")
        .contains("\"insurerCode\":\"ACACIA\"")
        .contains("\"planId\":\"5b1c2f0e-7a8d-4c3b-9e2f-1a2b3c4d5e6f\"")
        .contains("\"planCode\":\"STANDARD\"")
        .contains("\"planVersion\":1")
        .contains("\"ageBasis\":\"NEXT_BIRTHDAY\"")
        .contains("\"modules\":[{\"module\":\"OUTPATIENT\",\"option\":\"OP-40K\",\"benefits\":")
        .contains("\"limit\":{\"amount\":40000,\"period\":\"PER_YEAR\",\"scope\":\"PER_MEMBER\"")
        .contains("\"maxPerVisit\":2500")
        .contains("\"copay\":{\"type\":\"PERCENT\",\"value\":0.1}")
        .contains("\"requiredDocuments\":[\"RECEIPT\"]")
        .contains("\"preAuthRequired\":false")
        .contains("\"children\":[")
        .contains("\"waitingRules\":[{\"code\":\"GENERAL_ILLNESS\",\"days\":30,\"precedence\":0")
        .contains("\"exclusions\":[\"Cosmetic treatment\"]")
        .contains("\"filingDeadlineDays\":90")
        .contains(
            "\"cancellation\":{\"noticeDays\":30,\"refundModel\":\"PRO_RATA\","
                + "\"noRefundAfterMonths\":6,\"noRefundIfClaims\":true}");
  }

  @Test
  void unknownFieldsAreIgnoredAndMissingListsAreEmpty() {
    String json =
        """
        {"snapshotVersion": 1, "insurerCode": "ACACIA", "planVersion": 1, "currency": "KES",
         "modules": [{"module": "INPATIENT", "option": "IP-1",
                      "benefits": {"code": "INPATIENT", "kind": "REIMBURSEMENT",
                                   "preAuthRequired": false, "future": 1}}],
         "filingDeadlineDays": 90, "someFutureField": true}
        """;
    TermsSnapshot snapshot = mapper.readValue(json, TermsSnapshot.class);
    assertThat(snapshot.modules().get(0).benefits().children()).isEmpty();
    assertThat(snapshot.waitingRules()).isEmpty();
    assertThat(snapshot.exclusions()).isEmpty();
  }
}
