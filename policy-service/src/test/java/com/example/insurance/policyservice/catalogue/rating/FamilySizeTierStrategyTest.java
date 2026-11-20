package com.example.insurance.policyservice.catalogue.rating;

import static com.example.insurance.policyservice.catalogue.rating.StrategyTestSupport.context;
import static com.example.insurance.policyservice.catalogue.rating.StrategyTestSupport.member;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.insurance.policyservice.catalogue.domain.AgeRange;
import com.example.insurance.policyservice.catalogue.domain.Relationship;
import com.example.insurance.policyservice.catalogue.domain.Sex;
import com.example.insurance.policyservice.catalogue.rating.FamilySizeTierStrategy.Config;
import com.example.insurance.policyservice.catalogue.rating.FamilySizeTierStrategy.Tier;
import com.example.insurance.policyservice.catalogue.rating.FamilySizeTierStrategy.TierTable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FamilySizeTierStrategyTest {

  private final FamilySizeTierStrategy strategy = new FamilySizeTierStrategy();

  private static List<Tier> tiers(int... amounts) {
    List<Tier> tiers = new ArrayList<>();
    for (int i = 0; i < amounts.length; i++) {
      tiers.add(new Tier(i + 1, BigDecimal.valueOf(amounts[i])));
    }
    return tiers;
  }

  private static RatedMember[] family(int principalAge, int size) {
    RatedMember[] members = new RatedMember[size];
    members[0] = member(Relationship.PRINCIPAL, Sex.FEMALE, principalAge);
    for (int i = 1; i < size; i++) {
      members[i] = member(Relationship.CHILD, Sex.MALE, i);
    }
    return members;
  }

  private final Config single =
      new Config(
          FamilySizeTierStrategy.MODEL,
          null,
          List.of(new TierTable(null, tiers(100, 180, 240), new BigDecimal("50"))));

  private final Config byPrincipalAge =
      new Config(
          FamilySizeTierStrategy.MODEL,
          "PRINCIPAL",
          List.of(
              new TierTable(new AgeRange(18, 39), tiers(100, 200), new BigDecimal("40")),
              new TierTable(new AgeRange(40, 65), tiers(150, 260), new BigDecimal("60"))));

  @ParameterizedTest(name = "a family of {0} pays {1}")
  @CsvSource({"1, 100", "2, 180", "3, 240", "4, 290", "6, 390"})
  void tiersAndOverflow(int size, String expected) {
    assertThat(strategy.price(single, context(family(30, size)))).isEqualByComparingTo(expected);
  }

  @ParameterizedTest(name = "principal aged {0}, family of {1}, pays {2}")
  @CsvSource({"39, 1, 100", "39, 3, 240", "40, 1, 150", "40, 3, 320", "65, 2, 260"})
  void theTableFollowsThePrincipalsAge(int age, int size, String expected) {
    assertThat(strategy.price(byPrincipalAge, context(family(age, size))))
        .isEqualByComparingTo(expected);
  }

  @Test
  void noTableForThePrincipalsAgeFails() {
    assertThatThrownBy(() -> strategy.price(byPrincipalAge, context(family(66, 1))))
        .isInstanceOfSatisfying(
            RatingException.class,
            e -> assertThat(e.code()).isEqualTo(RatingException.NO_TIER_TABLE_FOR_AGE));
  }

  @Test
  void tablesByAgeNeedAPrincipal() {
    assertThatThrownBy(() ->
            strategy.price(byPrincipalAge, context(member(Relationship.SPOUSE, Sex.MALE, 30))))
        .isInstanceOfSatisfying(
            RatingException.class,
            e -> assertThat(e.code()).isEqualTo(RatingException.PRINCIPAL_REQUIRED));
  }

  @Test
  void overflowWithoutAnExtraAmountFails() {
    Config noExtra =
        new Config(
            FamilySizeTierStrategy.MODEL,
            null,
            List.of(new TierTable(null, tiers(100, 180), null)));
    assertThatThrownBy(() -> strategy.price(noExtra, context(family(30, 3))))
        .isInstanceOfSatisfying(
            RatingException.class,
            e -> assertThat(e.code()).isEqualTo(RatingException.TIER_OVERFLOW_NOT_PRICED));
  }

  @Test
  void aMissingTierInsideTheRangeFails() {
    Config gap =
        new Config(
            FamilySizeTierStrategy.MODEL,
            null,
            List.of(
                new TierTable(
                    null,
                    List.of(new Tier(1, BigDecimal.ONE), new Tier(3, BigDecimal.TEN)),
                    null)));
    assertThatThrownBy(() -> strategy.price(gap, context(family(30, 2))))
        .isInstanceOfSatisfying(
            RatingException.class,
            e -> assertThat(e.code()).isEqualTo(RatingException.RATING_CONFIG_INVALID));
  }
}
