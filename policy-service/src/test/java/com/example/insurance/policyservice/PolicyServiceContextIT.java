package com.example.insurance.policyservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.insurance.policyservice.catalogue.rating.AgeBandPerMemberStrategy;
import com.example.insurance.policyservice.catalogue.rating.FamilySizeTierStrategy;
import com.example.insurance.policyservice.catalogue.rating.FlatPerEligibleMemberStrategy;
import com.example.insurance.policyservice.catalogue.rating.RatingStrategyRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The application starts, Flyway migrates, and Spring registers every rating strategy. */
class PolicyServiceContextIT extends AbstractPolicyIT {

  @Autowired RatingStrategyRegistry registry;

  @Test
  void everyStrategyIsRegistered() {
    assertThat(registry.find(AgeBandPerMemberStrategy.MODEL)).isPresent();
    assertThat(registry.find(FamilySizeTierStrategy.MODEL)).isPresent();
    assertThat(registry.find(FlatPerEligibleMemberStrategy.MODEL)).isPresent();
  }
}
