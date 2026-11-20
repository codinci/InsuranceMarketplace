package com.example.insurance.policyservice.policy.service;

import com.example.insurance.policyservice.catalogue.api.QuotedPrice;
import com.example.insurance.policyservice.policy.dto.PremiumView;

/** Maps the catalogue's price to the stored and returned premium view. */
final class Premiums {

  private Premiums() {}

  static PremiumView of(QuotedPrice price) {
    return new PremiumView(
        price.modules().stream()
            .map(m -> new PremiumView.ModuleLine(m.module(), m.option(), m.amount()))
            .toList(),
        price.subtotal(),
        price.adjustments().stream()
            .map(a -> new PremiumView.ChargeLine(a.code(), a.amount()))
            .toList(),
        price.adjustedSubtotal(),
        price.levies().stream().map(l -> new PremiumView.ChargeLine(l.code(), l.amount())).toList(),
        price.annualPremium(),
        price.currency());
  }
}
