package com.example.insurance.policyservice.catalogue.rating;

import static com.example.insurance.policyservice.catalogue.rating.StrategyTestSupport.context;
import static com.example.insurance.policyservice.catalogue.rating.StrategyTestSupport.member;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.insurance.policyservice.catalogue.domain.Relationship;
import com.example.insurance.policyservice.catalogue.domain.Sex;
import com.example.insurance.policyservice.catalogue.rating.AgeBandPerMemberStrategy.AgeBand;
import com.example.insurance.policyservice.catalogue.rating.AgeBandPerMemberStrategy.Config;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AgeBandPerMemberStrategyTest {

  private final AgeBandPerMemberStrategy strategy = new AgeBandPerMemberStrategy();
  private final Config config =
      new Config(
          AgeBandPerMemberStrategy.MODEL,
          List.of(
              new AgeBand(0, 17, new BigDecimal("1000")),
              new AgeBand(18, 40, new BigDecimal("2000")),
              new AgeBand(41, 60, new BigDecimal("3000"))));

  @ParameterizedTest(name = "age {0} pays {1}")
  @CsvSource({"0, 1000", "17, 1000", "18, 2000", "40, 2000", "41, 3000", "60, 3000"})
  void eachEdgeOfEachBand(int age, String expected) {
    BigDecimal price =
        strategy.price(config, context(member(Relationship.PRINCIPAL, Sex.MALE, age)));
    assertThat(price).isEqualByComparingTo(expected);
  }

  @Test
  void membersAreAddedUp() {
    BigDecimal price =
        strategy.price(
            config,
            context(
                member(Relationship.PRINCIPAL, Sex.MALE, 45),
                member(Relationship.SPOUSE, Sex.FEMALE, 39),
                member(Relationship.CHILD, Sex.MALE, 3)));
    assertThat(price).isEqualByComparingTo("6000");
  }

  @Test
  void anAgeOutsideEveryBandFails() {
    assertThatThrownBy(
            () -> strategy.price(config, context(member(Relationship.PRINCIPAL, Sex.MALE, 61))))
        .isInstanceOfSatisfying(
            RatingException.class,
            e -> assertThat(e.code()).isEqualTo(RatingException.AGE_OUTSIDE_RATING_BANDS));
  }
}
