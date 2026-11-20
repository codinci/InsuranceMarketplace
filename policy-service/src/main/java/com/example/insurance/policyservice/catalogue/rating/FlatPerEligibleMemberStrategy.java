package com.example.insurance.policyservice.catalogue.rating;

import com.example.insurance.policyservice.catalogue.domain.PlanDefinition;
import com.example.insurance.policyservice.catalogue.domain.Relationship;
import com.example.insurance.policyservice.catalogue.domain.Sex;
import com.example.insurance.policyservice.catalogue.domain.Violation;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * {@code FLAT_PER_ELIGIBLE_MEMBER}: a flat amount for each member who matches the relationships
 * and, if set, the sex (PLAN 5.3), for example maternity for a female principal or spouse. No
 * eligible member means a premium of zero.
 */
@Component
public class FlatPerEligibleMemberStrategy
    implements RatingStrategy<FlatPerEligibleMemberStrategy.Config> {

  public static final String MODEL = "FLAT_PER_ELIGIBLE_MEMBER";

  /** The {@code rating} block. */
  public record Config(String model, BigDecimal annual, Eligible eligible) {}

  /** Who pays the flat amount. */
  public record Eligible(List<Relationship> relationships, Sex sex) {

    public Eligible {
      relationships = List.copyOf(relationships);
    }

    boolean matches(RatedMember member) {
      return relationships.contains(member.relationship()) && (sex == null || sex == member.sex());
    }
  }

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
    boolean familyAllowed = plan.eligibility().allowsFamily();
    boolean anyPossible =
        config.eligible().relationships().stream()
            .anyMatch(r -> r == Relationship.PRINCIPAL || familyAllowed);
    if (anyPossible) {
      return List.of();
    }
    return List.of(
        new Violation(
            "FLAT_RATING_NO_ELIGIBLE_RELATIONSHIP",
            path + "/eligible/relationships",
            "The plan sells individual cover only, so no listed relationship can be covered."));
  }

  @Override
  public BigDecimal price(Config config, RatingContext context) {
    long eligible = context.members().stream().filter(config.eligible()::matches).count();
    return config.annual().multiply(BigDecimal.valueOf(eligible));
  }
}
