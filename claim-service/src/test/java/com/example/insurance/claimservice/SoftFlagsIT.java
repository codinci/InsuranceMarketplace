package com.example.insurance.claimservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.example.insurance.claimservice.support.FakeDocumentStorageConfig;
import com.example.insurance.claimservice.support.TestSecurityConfig;
import com.example.insurance.claimservice.support.TestTokens;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Soft flags (PLAN 9.1, M6) through the API: raised at submission, visible to reviewers, hidden
 * from the customer, and a flagged claim goes first in the queue. A flag never rejects a claim.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestSecurityConfig.class, FakeDocumentStorageConfig.class})
class SoftFlagsIT extends AbstractReviewIT {

  private final UUID insurer = UUID.randomUUID();
  private final String staff = TestTokens.adjuster(UUID.randomUUID(), insurer);

  @Test
  void aHighAmountIsFlaggedForReviewersAndHiddenFromTheCustomer() throws Exception {
    UUID holder = UUID.randomUUID();
    String customer = TestTokens.customer(holder);
    String claimId =
        submitted(customer, policy(holder, unlimited(), longAgo(), insurer), "150000.00");

    MvcResult seenByStaff = claimAs(staff, claimId);
    assertThat(this.<Boolean>read(seenByStaff, "$.flagged")).isTrue();
    assertThat(flagCodes(seenByStaff)).containsExactly("HIGH_AMOUNT");
    // A flag never rejects: the claim is still waiting for review.
    assertThat(this.<String>read(seenByStaff, "$.status")).isEqualTo("SUBMITTED");

    MvcResult seenByCustomer = claimAs(customer, claimId);
    assertThat(this.<Object>read(seenByCustomer, "$.flagged")).isNull();
    assertThat(seenByCustomer.getResponse().getContentAsString()).doesNotContain("HIGH_AMOUNT");
    assertThat(this.<String>read(seenByCustomer, "$.status")).isEqualTo("SUBMITTED");
  }

  @Test
  void aSecondIdenticalClaimIsFlaggedAsAPossibleDuplicate() throws Exception {
    UUID holder = UUID.randomUUID();
    String customer = TestTokens.customer(holder);
    UUID policyId = policy(holder, unlimited(), longAgo(), insurer);

    String first = submitted(customer, policyId, "1000.00", "Same Clinic", 10);
    String second = submitted(customer, policyId, "1000.00", "Same Clinic", 10);

    assertThat(this.<Boolean>read(claimAs(staff, first), "$.flagged")).isFalse();
    assertThat(flagCodes(claimAs(staff, second))).containsExactly("POSSIBLE_DUPLICATE");
  }

  @Test
  void anIncidentSoonAfterTheStartOfCoverIsFlagged() throws Exception {
    UUID holder = UUID.randomUUID();
    String customer = TestTokens.customer(holder);
    // Cover started twenty days ago and the incident was ten days ago: ten days into cover.
    UUID policyId = policy(holder, unlimited(), LocalDate.now().minusDays(20), insurer);

    String claimId = submitted(customer, policyId, "1000.00", "Clinic", 10);

    assertThat(flagCodes(claimAs(staff, claimId))).containsExactly("EARLY_IN_COVER");
  }

  @Test
  void theThirdClaimOfAMemberInAMonthIsFlaggedAsFrequent() throws Exception {
    UUID holder = UUID.randomUUID();
    String customer = TestTokens.customer(holder);
    UUID policyId = policy(holder, unlimited(), longAgo(), insurer);

    String first = submitted(customer, policyId, "1000.00", "Clinic A", 10);
    String second = submitted(customer, policyId, "2000.00", "Clinic B", 11);
    String third = submitted(customer, policyId, "3000.00", "Clinic C", 12);

    assertThat(this.<Boolean>read(claimAs(staff, first), "$.flagged")).isFalse();
    assertThat(this.<Boolean>read(claimAs(staff, second), "$.flagged")).isFalse();
    assertThat(flagCodes(claimAs(staff, third))).containsExactly("FREQUENT_CLAIMS");
  }

  @Test
  void flaggedClaimsComeFirstInTheQueueEvenWhenNewer() throws Exception {
    UUID holder = UUID.randomUUID();
    String customer = TestTokens.customer(holder);
    UUID policyId = policy(holder, unlimited(), longAgo(), insurer);
    String plain = submitted(customer, policyId, "1000.00", "Clinic A", 10);
    String flagged = submitted(customer, policyId, "150000.00", "Clinic B", 11);

    MvcResult queue = as(staff, get("/api/v1/claims/queue?size=10"));

    List<String> ids = this.read(queue, "$.content[*].id");
    assertThat(ids).containsExactly(flagged, plain);
  }

  private static LocalDate longAgo() {
    return LocalDate.now().minusDays(100);
  }

  private List<String> flagCodes(MvcResult result) throws Exception {
    return this.read(result, "$.assessments[?(@.result == 'FLAG')].ruleCode");
  }
}
