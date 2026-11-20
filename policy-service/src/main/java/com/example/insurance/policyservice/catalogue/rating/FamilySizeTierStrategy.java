package com.example.insurance.policyservice.catalogue.rating;

import com.example.insurance.policyservice.catalogue.domain.AgeRange;
import com.example.insurance.policyservice.catalogue.domain.PlanDefinition;
import com.example.insurance.policyservice.catalogue.domain.Violation;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * {@code FAMILY_SIZE_TIER}: one amount for the whole family, by the number of members (PLAN 5.3).
 * With {@code ageSubject: PRINCIPAL} the table is chosen by the principal's age. Members beyond the
 * largest tier add {@code additionalMemberAnnual} each.
 */
@Component
public class FamilySizeTierStrategy implements RatingStrategy<FamilySizeTierStrategy.Config> {

  public static final String MODEL = "FAMILY_SIZE_TIER";
  private static final String PRINCIPAL = "PRINCIPAL";

  /** The {@code rating} block. */
  public record Config(String model, String ageSubject, List<TierTable> tables) {

    public Config {
      tables = List.copyOf(tables);
    }
  }

  /** One table of tiers, optionally for a range of the principal's age. */
  public record TierTable(AgeRange age, List<Tier> tiers, BigDecimal additionalMemberAnnual) {

    public TierTable {
      tiers = List.copyOf(tiers);
    }

    Tier largest() {
      return tiers.stream().max(Comparator.comparingInt(Tier::members)).orElseThrow();
    }
  }

  /** The annual amount for a family of exactly {@code members}. */
  public record Tier(int members, BigDecimal annual) {}

  @Override
  public String model() {
    return MODEL;
  }

  @Override
  public Class<Config> configType() {
    return Config.class;
  }

  @Override
  public List<Violation> validate(Config config, PlanDefinition plan, String path) {
    List<Violation> violations = new ArrayList<>();
    boolean byPrincipalAge = PRINCIPAL.equals(config.ageSubject());
    for (int t = 0; t < config.tables().size(); t++) {
      TierTable table = config.tables().get(t);
      String at = path + "/tables/" + t;
      if (byPrincipalAge == (table.age() == null)) {
        violations.add(
            new Violation(
                "TIER_TABLES_AMBIGUOUS",
                at,
                byPrincipalAge
                    ? "Every table needs an age range when the age subject is PRINCIPAL."
                    : "A table has an age range but no age subject is set."));
      }
      checkTiers(table, plan, at, violations);
    }
    if (!byPrincipalAge && config.tables().size() > 1) {
      violations.add(
          new Violation(
              "TIER_TABLES_AMBIGUOUS",
              path + "/tables",
              "Several tables need an age subject to choose between them."));
    }
    if (byPrincipalAge && config.tables().stream().allMatch(t -> t.age() != null)) {
      checkAgeTables(config, plan, path, violations);
    }
    return violations;
  }

  private static void checkTiers(
      TierTable table, PlanDefinition plan, String at, List<Violation> violations) {
    List<Integer> sizes = table.tiers().stream().map(Tier::members).sorted().toList();
    for (int i = 0; i < sizes.size(); i++) {
      if (sizes.get(i) != i + 1) {
        violations.add(
            new Violation(
                "TIERS_NOT_CONTIGUOUS",
                at + "/tiers",
                "Tiers must cover family sizes 1 to " + sizes.size() + " with no gap or repeat."));
        return;
      }
    }
    if (table.largest().members() < plan.eligibility().maxMembers()
        && table.additionalMemberAnnual() == null) {
      violations.add(
          new Violation(
              "TIER_OVERFLOW_NOT_PRICED",
              at,
              "The plan allows "
                  + plan.eligibility().maxMembers()
                  + " members but the largest tier is "
                  + table.largest().members()
                  + " and there is no additionalMemberAnnual."));
    }
  }

  private static void checkAgeTables(
      Config config, PlanDefinition plan, String path, List<Violation> violations) {
    List<Integer> order = new ArrayList<>();
    for (int i = 0; i < config.tables().size(); i++) {
      AgeRange age = config.tables().get(i).age();
      if (age.min() > age.max()) {
        violations.add(
            new Violation(
                "AGE_RANGE_INVALID",
                path + "/tables/" + i + "/age",
                "A table's age range starts after it ends."));
      }
      order.add(i);
    }
    order.sort(Comparator.comparingInt(i -> config.tables().get(i).age().min()));
    for (int k = 1; k < order.size(); k++) {
      AgeRange previous = config.tables().get(order.get(k - 1)).age();
      AgeRange current = config.tables().get(order.get(k)).age();
      String at = path + "/tables/" + order.get(k) + "/age";
      if (current.min() <= previous.max()) {
        violations.add(
            new Violation("TIER_AGE_TABLES_OVERLAP", at, "Two tables cover the same ages."));
      } else if (current.min() > previous.max() + 1) {
        violations.add(
            new Violation(
                "TIER_AGE_TABLES_GAP",
                at,
                "No table covers principal ages "
                    + (previous.max() + 1)
                    + " to "
                    + (current.min() - 1)
                    + "."));
      }
    }
    List<AgeRange> covered = config.tables().stream().map(TierTable::age).toList();
    int missing = EligibleAges.firstUncovered(plan.eligibility().principalAge(), covered);
    if (missing >= 0) {
      violations.add(
          new Violation(
              "TIER_AGE_TABLES_DO_NOT_COVER_ELIGIBLE_AGES",
              path + "/tables",
              "No table covers the eligible principal age " + missing + "."));
    }
  }

  @Override
  public BigDecimal price(Config config, RatingContext context) {
    TierTable table = chooseTable(config, context);
    int familySize = context.members().size();
    Tier largest = table.largest();
    if (familySize <= largest.members()) {
      return table.tiers().stream()
          .filter(t -> t.members() == familySize)
          .findFirst()
          .map(Tier::annual)
          .orElseThrow(() ->
              new RatingException(
                  RatingException.RATING_CONFIG_INVALID,
                  "No tier for a family of " + familySize + "."));
    }
    if (table.additionalMemberAnnual() == null) {
      throw new RatingException(
          RatingException.TIER_OVERFLOW_NOT_PRICED,
          "A family of " + familySize + " is larger than every tier.");
    }
    BigDecimal extra =
        table.additionalMemberAnnual().multiply(BigDecimal.valueOf(familySize - largest.members()));
    return largest.annual().add(extra);
  }

  private static TierTable chooseTable(Config config, RatingContext context) {
    if (!PRINCIPAL.equals(config.ageSubject())) {
      return config.tables().get(0);
    }
    RatedMember principal =
        context
            .principal()
            .orElseThrow(() ->
                new RatingException(
                    RatingException.PRINCIPAL_REQUIRED,
                    "Tiers chosen by the principal's age need a principal."));
    int age = context.ageOf(principal);
    return config.tables().stream()
        .filter(t -> t.age().contains(age))
        .findFirst()
        .orElseThrow(() ->
            new RatingException(
                RatingException.NO_TIER_TABLE_FOR_AGE,
                "No tier table covers a principal aged " + age + "."));
  }
}
