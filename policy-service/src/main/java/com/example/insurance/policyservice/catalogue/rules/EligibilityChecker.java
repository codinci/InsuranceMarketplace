package com.example.insurance.policyservice.catalogue.rules;

import com.example.insurance.policyservice.catalogue.domain.AgeRange;
import com.example.insurance.policyservice.catalogue.domain.ChildAge;
import com.example.insurance.policyservice.catalogue.domain.CoverType;
import com.example.insurance.policyservice.catalogue.domain.Eligibility;
import com.example.insurance.policyservice.catalogue.domain.PlanDefinition;
import com.example.insurance.policyservice.catalogue.domain.Relationship;
import com.example.insurance.policyservice.catalogue.domain.Sex;
import com.example.insurance.policyservice.catalogue.rating.AgeCalculator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Checks members against a plan's eligibility (PLAN 5.3, 8.1, docs/catalogue-schema.md). Ages are
 * rating ages in the plan's age basis on the start date. Every problem is reported.
 */
@Component
public class EligibilityChecker {

  public static final String COVER_TYPE_NOT_OFFERED = "COVER_TYPE_NOT_OFFERED";
  public static final String PRINCIPAL_REQUIRED = "PRINCIPAL_REQUIRED";
  public static final String INDIVIDUAL_COVER_HAS_DEPENDANTS = "INDIVIDUAL_COVER_HAS_DEPENDANTS";
  public static final String TOO_MANY_SPOUSES = "TOO_MANY_SPOUSES";
  public static final String TOO_MANY_CHILDREN = "TOO_MANY_CHILDREN";
  public static final String STUDENT_ONLY_FOR_CHILDREN = "STUDENT_ONLY_FOR_CHILDREN";
  public static final String AGE_NOT_ELIGIBLE = "AGE_NOT_ELIGIBLE";

  /** A member to check. */
  public record Applicant(
      Relationship relationship, Sex sex, LocalDate dateOfBirth, boolean student) {}

  /** One problem; {@code memberIndex} is null for the whole group. */
  public record Ineligibility(String code, Integer memberIndex, String message) {}

  public List<Ineligibility> check(
      PlanDefinition plan, CoverType coverType, List<Applicant> members, LocalDate startDate) {
    Eligibility rules = plan.eligibility();
    List<Ineligibility> problems = new ArrayList<>();
    if (!rules.coverTypes().contains(coverType)) {
      problems.add(
          new Ineligibility(
              COVER_TYPE_NOT_OFFERED, null, "The plan does not offer " + coverType + " cover."));
    }
    long principals =
        members.stream().filter(m -> m.relationship() == Relationship.PRINCIPAL).count();
    if (principals != 1) {
      problems.add(
          new Ineligibility(PRINCIPAL_REQUIRED, null, "Exactly one member must be the principal."));
    }
    int spouses = 0;
    int children = 0;
    for (int i = 0; i < members.size(); i++) {
      Applicant member = members.get(i);
      if (coverType == CoverType.INDIVIDUAL && member.relationship() != Relationship.PRINCIPAL) {
        problems.add(
            new Ineligibility(
                INDIVIDUAL_COVER_HAS_DEPENDANTS, i, "Individual cover is for the principal only."));
      }
      if (member.relationship() == Relationship.SPOUSE && ++spouses > 1) {
        problems.add(new Ineligibility(TOO_MANY_SPOUSES, i, "Only one spouse can be covered."));
      }
      if (member.relationship() == Relationship.CHILD) {
        children++;
      }
      if (member.student() && member.relationship() != Relationship.CHILD) {
        problems.add(
            new Ineligibility(
                STUDENT_ONLY_FOR_CHILDREN, i, "Only a child can be covered as a student."));
      }
      checkAge(plan, member, i, startDate, problems);
    }
    if (children > rules.maxChildren()) {
      problems.add(
          new Ineligibility(
              TOO_MANY_CHILDREN,
              null,
              "The plan covers at most " + rules.maxChildren() + " children."));
    }
    return problems;
  }

  private static void checkAge(
      PlanDefinition plan,
      Applicant member,
      int index,
      LocalDate startDate,
      List<Ineligibility> problems) {
    if (member.dateOfBirth().isAfter(startDate)) {
      problems.add(
          new Ineligibility(AGE_NOT_ELIGIBLE, index, "The member is born after the start date."));
      return;
    }
    int age = AgeCalculator.ratingAge(member.dateOfBirth(), startDate, plan.ageBasis());
    Eligibility rules = plan.eligibility();
    boolean eligible =
        switch (member.relationship()) {
          case PRINCIPAL -> rules.principalAge().contains(age);
          case SPOUSE -> rules.effectiveSpouseAge().contains(age);
          case CHILD -> childEligible(rules.childAge(), age, member.student());
        };
    if (!eligible) {
      problems.add(
          new Ineligibility(
              AGE_NOT_ELIGIBLE,
              index,
              "A "
                  + member.relationship().name().toLowerCase(java.util.Locale.ROOT)
                  + " rated at age "
                  + age
                  + " is not eligible on this plan."));
    }
  }

  private static boolean childEligible(ChildAge childAge, int age, boolean student) {
    if (childAge == null) {
      return false;
    }
    if (new AgeRange(childAge.min(), childAge.max()).contains(age)) {
      return true;
    }
    return student
        && childAge.maxIfStudent() != null
        && age > childAge.max()
        && age <= childAge.maxIfStudent();
  }
}
