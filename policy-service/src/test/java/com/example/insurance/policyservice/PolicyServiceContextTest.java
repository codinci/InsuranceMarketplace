package com.example.insurance.policyservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.insurance.policyservice.catalogue.rating.AgeBandPerMemberStrategy;
import com.example.insurance.policyservice.catalogue.rating.FamilySizeTierStrategy;
import com.example.insurance.policyservice.catalogue.rating.FlatPerEligibleMemberStrategy;
import com.example.insurance.policyservice.catalogue.rating.RatingStrategyRegistry;
import com.example.insurance.policyservice.catalogue.rules.PlanValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** The application starts and Spring registers every rating strategy (ADR-003). */
@SpringBootTest
class PolicyServiceContextTest {

  @Autowired RatingStrategyRegistry registry;
  @Autowired PlanValidator validator;

  @Test
  void everyStrategyIsRegistered() {
    assertThat(registry.find(AgeBandPerMemberStrategy.MODEL)).isPresent();
    assertThat(registry.find(FamilySizeTierStrategy.MODEL)).isPresent();
    assertThat(registry.find(FlatPerEligibleMemberStrategy.MODEL)).isPresent();
    assertThat(validator).isNotNull();
  }
}
