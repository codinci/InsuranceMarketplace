package com.example.insurance.policyservice.catalogue.rating;

import com.example.insurance.policyservice.catalogue.domain.AgeRange;
import com.example.insurance.policyservice.catalogue.domain.PlanDefinition;
import com.example.insurance.policyservice.catalogue.domain.Violation;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/** {@code AGE_BAND_PER_MEMBER}: every member pays the amount of their age band (PLAN 5.3). */
@Component
public class AgeBandPerMemberStrategy implements RatingStrategy<AgeBandPerMemberStrategy.Config> {

  public static final String MODEL = "AGE_BAND_PER_MEMBER";

  /** The {@code rating} block. */
  public record Config(String model, List<AgeBand> ageBands) {

    public Config {
      ageBands = List.copyOf(ageBands);
    }
  }

  /** An inclusive band of rating ages and its annual amount. */
  public record AgeBand(int from, int to, BigDecimal annual) {}

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
    List<Integer> order = new ArrayList<>();
    for (int i = 0; i < config.ageBands().size(); i++) {
      AgeBand band = config.ageBands().get(i);
      if (band.from() > band.to()) {
        violations.add(
            new Violation(
                "AGE_RANGE_INVALID",
                path + "/ageBands/" + i,
                "A band starts after it ends (" + band.from() + " to " + band.to() + ")."));
      }
      order.add(i);
    }
    order.sort(Comparator.comparingInt(i -> config.ageBands().get(i).from()));
    for (int k = 1; k < order.size(); k++) {
      AgeBand previous = config.ageBands().get(order.get(k - 1));
      AgeBand current = config.ageBands().get(order.get(k));
      String at = path + "/ageBands/" + order.get(k);
      if (current.from() <= previous.to()) {
        violations.add(
            new Violation(
                "RATE_BANDS_OVERLAP",
                at,
                "Band from age " + current.from() + " overlaps another."));
      } else if (current.from() > previous.to() + 1) {
        violations.add(
            new Violation(
                "RATE_BANDS_GAP",
                at,
                "No band covers ages "
                    + (previous.to() + 1)
                    + " to "
                    + (current.from() - 1)
                    + "."));
      }
    }
    List<AgeRange> covered =
        config.ageBands().stream().map(b -> new AgeRange(b.from(), b.to())).toList();
    for (EligibleAges.Required required : EligibleAges.forAllMembers(plan.eligibility())) {
      int missing = EligibleAges.firstUncovered(required.range(), covered);
      if (missing >= 0) {
        violations.add(
            new Violation(
                "RATE_BANDS_DO_NOT_COVER_ELIGIBLE_AGES",
                path + "/ageBands",
                "No band covers the eligible " + required.who() + " age " + missing + "."));
      }
    }
    return violations;
  }

  @Override
  public BigDecimal price(Config config, RatingContext context) {
    BigDecimal total = BigDecimal.ZERO;
    for (RatedMember member : context.members()) {
      int age = context.ageOf(member);
      AgeBand band =
          config.ageBands().stream()
              .filter(b -> age >= b.from() && age <= b.to())
              .findFirst()
              .orElseThrow(() ->
                  new RatingException(
                      RatingException.AGE_OUTSIDE_RATING_BANDS,
                      "No rating band covers age " + age + "."));
      total = total.add(band.annual());
    }
    return total;
  }
}
