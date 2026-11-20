package com.example.insurance.policyservice.catalogue.rating;

import com.example.insurance.policyservice.catalogue.domain.AmountType;
import com.example.insurance.policyservice.catalogue.domain.Charge;
import com.example.insurance.policyservice.catalogue.domain.ModuleDef;
import com.example.insurance.policyservice.catalogue.domain.ModuleOption;
import com.example.insurance.policyservice.catalogue.domain.PlanDefinition;
import com.example.insurance.policyservice.catalogue.rating.PremiumBreakdown.ChargeAmount;
import com.example.insurance.policyservice.catalogue.rating.PremiumBreakdown.ModulePremium;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Prices a plan for a set of members (PLAN 8.1): each selected option through its strategy, then
 * the adjustments in order, then the levies on the adjusted subtotal.
 */
@Component
public class PremiumCalculator {

  private final RatingStrategyRegistry registry;

  public PremiumCalculator(RatingStrategyRegistry registry) {
    this.registry = registry;
  }

  /**
   * @param selection module code to option code; every mandatory module must be present
   */
  public PremiumBreakdown price(
      PlanDefinition plan,
      Map<String, String> selection,
      List<RatedMember> members,
      LocalDate startDate) {
    if (members.isEmpty()) {
      throw new RatingException(RatingException.NO_MEMBERS, "A quote needs at least one member.");
    }
    for (String moduleCode : selection.keySet()) {
      if (plan.modules().stream().noneMatch(m -> m.code().equals(moduleCode))) {
        throw new RatingException(
            RatingException.MODULE_NOT_FOUND, "The plan has no module " + moduleCode + ".");
      }
    }
    RatingContext context = new RatingContext(members, startDate, plan.ageBasis());
    List<ModulePremium> modules = new ArrayList<>();
    BigDecimal subtotal = BigDecimal.ZERO;
    for (ModuleDef module : plan.modules()) {
      String optionCode = selection.get(module.code());
      if (optionCode == null) {
        if (module.mandatory()) {
          throw new RatingException(
              RatingException.MANDATORY_MODULE_MISSING,
              "The module " + module.code() + " is mandatory.");
        }
        continue;
      }
      ModuleOption option = findOption(module, optionCode);
      BigDecimal amount = round(registry.price(option.rating(), context));
      modules.add(new ModulePremium(module.code(), option.code(), amount));
      subtotal = subtotal.add(amount);
    }

    List<ChargeAmount> adjustments = new ArrayList<>();
    BigDecimal running = subtotal;
    for (Charge adjustment : plan.adjustments()) {
      BigDecimal amount = round(chargeOn(running, adjustment));
      adjustments.add(new ChargeAmount(adjustment.code(), amount));
      running = running.add(amount);
    }

    List<ChargeAmount> levies = new ArrayList<>();
    BigDecimal leviesTotal = BigDecimal.ZERO;
    for (Charge levy : plan.levies()) {
      BigDecimal amount = round(chargeOn(running, levy));
      levies.add(new ChargeAmount(levy.code(), amount));
      leviesTotal = leviesTotal.add(amount);
    }

    return new PremiumBreakdown(
        modules, subtotal, adjustments, running, levies, running.add(leviesTotal), plan.currency());
  }

  private static ModuleOption findOption(ModuleDef module, String optionCode) {
    return module.options().stream()
        .filter(o -> o.code().equals(optionCode))
        .findFirst()
        .orElseThrow(() ->
            new RatingException(
                RatingException.OPTION_NOT_FOUND,
                "The module " + module.code() + " has no option " + optionCode + "."));
  }

  private static BigDecimal chargeOn(BigDecimal base, Charge charge) {
    return charge.type() == AmountType.PERCENT ? base.multiply(charge.value()) : charge.value();
  }

  private static BigDecimal round(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }
}
