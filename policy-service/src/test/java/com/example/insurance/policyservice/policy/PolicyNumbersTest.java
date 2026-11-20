package com.example.insurance.policyservice.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.insurance.policyservice.policy.domain.PolicyNumbers;
import org.junit.jupiter.api.Test;

class PolicyNumbersTest {

  @Test
  void policyNumbersCarryTheInsurerYearAndSequence() {
    assertThat(PolicyNumbers.policyNumber("ACACIA", 2026, 123)).isEqualTo("POL-ACACIA-2026-000123");
  }

  @Test
  void memberNumbersUseThreeLettersAndCountFromOne() {
    assertThat(PolicyNumbers.memberNumber("ACACIA", 123, 0)).isEqualTo("ACA-000123-01");
    assertThat(PolicyNumbers.memberNumber("BAOBAB", 7, 2)).isEqualTo("BAO-000007-03");
    assertThat(PolicyNumbers.memberNumber("SMOKE-17", 7, 0)).isEqualTo("SMO-000007-01");
    assertThat(PolicyNumbers.memberNumber("AB", 7, 0)).isEqualTo("AB-000007-01");
  }
}
