package com.example.insurance.policyservice.catalogue.rating;

import static com.example.insurance.policyservice.catalogue.rating.StrategyTestSupport.context;
import static com.example.insurance.policyservice.catalogue.rating.StrategyTestSupport.member;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.insurance.policyservice.catalogue.domain.Relationship;
import com.example.insurance.policyservice.catalogue.domain.Sex;
import com.example.insurance.policyservice.catalogue.rating.FlatPerEligibleMemberStrategy.Config;
import com.example.insurance.policyservice.catalogue.rating.FlatPerEligibleMemberStrategy.Eligible;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class FlatPerEligibleMemberStrategyTest {

  private final FlatPerEligibleMemberStrategy strategy = new FlatPerEligibleMemberStrategy();

  private static Config config(Sex sex, Relationship... relationships) {
    return new Config(
        FlatPerEligibleMemberStrategy.MODEL,
        new BigDecimal("500"),
        new Eligible(List.of(relationships), sex));
  }

  @Test
  void countsMembersWithAMatchingRelationshipAndSex() {
    RatingContext family =
        context(
            member(Relationship.PRINCIPAL, Sex.FEMALE, 30),
            member(Relationship.SPOUSE, Sex.MALE, 31),
            member(Relationship.CHILD, Sex.FEMALE, 5));
    assertThat(
            strategy.price(config(Sex.FEMALE, Relationship.PRINCIPAL, Relationship.SPOUSE), family))
        .isEqualByComparingTo("500");
  }

  @Test
  void withoutASexEveryListedRelationshipCounts() {
    RatingContext family =
        context(
            member(Relationship.PRINCIPAL, Sex.FEMALE, 30),
            member(Relationship.SPOUSE, Sex.MALE, 31),
            member(Relationship.CHILD, Sex.FEMALE, 5));
    assertThat(strategy.price(config(null, Relationship.PRINCIPAL, Relationship.SPOUSE), family))
        .isEqualByComparingTo("1000");
  }

  @Test
  void noEligibleMemberCostsNothing() {
    RatingContext family = context(member(Relationship.PRINCIPAL, Sex.MALE, 30));
    assertThat(strategy.price(config(Sex.FEMALE, Relationship.PRINCIPAL), family))
        .isEqualByComparingTo("0");
  }
}
