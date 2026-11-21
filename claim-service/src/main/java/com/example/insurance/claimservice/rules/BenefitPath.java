package com.example.insurance.claimservice.rules;

import com.example.insurance.events.policy.TermsSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Finds a benefit in the terms a policy was sold with and returns the path from the module's root
 * down to it (PLAN 9). The path is what descendant matching needs: a rule on a parent benefit
 * applies to every benefit under it (docs/catalogue-schema.md, D31). Generic: no benefit code is
 * known here.
 */
public final class BenefitPath {

  private BenefitPath() {
    // Static helpers only; not instantiable.
  }

  /** The nodes from the module root to {@code benefitCode}, inclusive, or empty if not covered. */
  public static Optional<List<TermsSnapshot.BenefitTerms>> find(
      TermsSnapshot terms, String moduleCode, String benefitCode) {
    if (terms == null) {
      return Optional.empty();
    }
    for (TermsSnapshot.ModuleTerms module : terms.modules()) {
      if (!moduleCode.equals(module.module()) || module.benefits() == null) {
        continue;
      }
      List<TermsSnapshot.BenefitTerms> path = new ArrayList<>();
      if (search(module.benefits(), benefitCode, path)) {
        return Optional.of(List.copyOf(path));
      }
    }
    return Optional.empty();
  }

  /** The codes on a path, for matching waiting rules. */
  public static Set<String> codes(List<TermsSnapshot.BenefitTerms> path) {
    return path.stream()
        .map(TermsSnapshot.BenefitTerms::code)
        .collect(Collectors.toUnmodifiableSet());
  }

  private static boolean search(
      TermsSnapshot.BenefitTerms node, String code, List<TermsSnapshot.BenefitTerms> path) {
    path.add(node);
    if (code.equals(node.code())) {
      return true;
    }
    for (TermsSnapshot.BenefitTerms child : node.children()) {
      if (search(child, code, path)) {
        return true;
      }
    }
    path.remove(path.size() - 1);
    return false;
  }
}
