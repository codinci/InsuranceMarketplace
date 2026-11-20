package com.example.insurance.policyservice.catalogue.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.example.insurance.policyservice.catalogue.CatalogueFixtures;
import com.example.insurance.policyservice.catalogue.domain.CoverType;
import com.example.insurance.policyservice.catalogue.domain.PlanDefinition;
import com.example.insurance.policyservice.catalogue.domain.Relationship;
import com.example.insurance.policyservice.catalogue.domain.Sex;
import com.example.insurance.policyservice.catalogue.rules.EligibilityChecker.Applicant;
import com.example.insurance.policyservice.catalogue.rules.EligibilityChecker.Ineligibility;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Eligibility rules (PLAN 5.3, docs/catalogue-schema.md). Acacia rates ages at the next birthday
 * (principal 18-70, children 0-21, five children). Baobab rates ages at the start date (principal
 * and spouse 18-65, children 0-18 or students to 24, five children).
 */
class EligibilityCheckerTest {

  private static final LocalDate START = LocalDate.of(2027, 1, 1);

  private final EligibilityChecker checker = new EligibilityChecker();
  private final PlanDefinition acacia = CatalogueFixtures.validPlan("acacia-standard.v1.json");
  private final PlanDefinition baobab = CatalogueFixtures.validPlan("baobab-family.v1.json");

  /** A member who is exactly {@code age} on the start date (so AT_START age is {@code age}). */
  private static Applicant member(Relationship relationship, int age) {
    return new Applicant(relationship, Sex.FEMALE, START.minusYears(age), false);
  }

  private static Applicant student(int age) {
    return new Applicant(Relationship.CHILD, Sex.MALE, START.minusYears(age), true);
  }

  private List<Ineligibility> check(PlanDefinition plan, CoverType cover, Applicant... members) {
    return checker.check(plan, cover, List.of(members), START);
  }

  @Test
  void aTypicalFamilyIsEligible() {
    assertThat(
            check(
                baobab,
                CoverType.FAMILY,
                member(Relationship.PRINCIPAL, 40),
                member(Relationship.SPOUSE, 38),
                member(Relationship.CHILD, 10),
                student(22)))
        .isEmpty();
  }

  @ParameterizedTest(name = "Baobab principal aged {0} eligible: {1}")
  @CsvSource({"17, false", "18, true", "65, true", "66, false"})
  void principalAgeEdgesAtStart(int age, boolean eligible) {
    assertThat(check(baobab, CoverType.INDIVIDUAL, member(Relationship.PRINCIPAL, age)))
        .hasSize(eligible ? 0 : 1);
  }

  @ParameterizedTest(name = "Acacia principal aged {0} on the start date eligible: {1}")
  @CsvSource({
    // NEXT_BIRTHDAY: someone who is 69 on the start date is rated 70 (allowed), 70 is rated 71.
    "69, true",
    "70, false",
    // 17 on the start date is rated 18.
    "17, true",
    "16, false"
  })
  void principalAgeEdgesAtNextBirthday(int ageOnStart, boolean eligible) {
    // Born the day after the start date this many years ago, so the next birthday is tomorrow.
    Applicant principal =
        new Applicant(
            Relationship.PRINCIPAL, Sex.MALE, START.minusYears(ageOnStart + 1).plusDays(1), false);
    assertThat(check(acacia, CoverType.INDIVIDUAL, principal)).hasSize(eligible ? 0 : 1);
  }

  @ParameterizedTest(name = "Baobab child aged {0}, student {1}, eligible: {2}")
  @CsvSource({
    "0, false, true",
    "18, false, true",
    "19, false, false",
    "19, true, true",
    "24, true, true",
    "25, true, false"
  })
  void theStudentExceptionExtendsChildAges(int age, boolean isStudent, boolean eligible) {
    Applicant child = new Applicant(Relationship.CHILD, Sex.MALE, START.minusYears(age), isStudent);
    List<Ineligibility> problems =
        check(baobab, CoverType.FAMILY, member(Relationship.PRINCIPAL, 40), child);
    assertThat(problems).hasSize(eligible ? 0 : 1);
  }

  @Test
  void aStudentOlderThanTheChildAgeIsRefusedWhenThePlanHasNoStudentException() {
    // Acacia children 0-21 with no maxIfStudent; aged 22 on the start date is rated 23.
    assertThat(check(acacia, CoverType.FAMILY, member(Relationship.PRINCIPAL, 40), student(22)))
        .extracting(Ineligibility::code, Ineligibility::memberIndex)
        .containsExactly(tuple(EligibilityChecker.AGE_NOT_ELIGIBLE, 1));
  }

  @Test
  void theCoverTypeMustBeOffered() {
    PlanDefinition individualOnly = withCoverTypes(baobab, List.of(CoverType.INDIVIDUAL));
    assertThat(check(individualOnly, CoverType.FAMILY, member(Relationship.PRINCIPAL, 40)))
        .extracting(Ineligibility::code)
        .containsExactly(EligibilityChecker.COVER_TYPE_NOT_OFFERED);
  }

  @Test
  void exactlyOnePrincipalIsRequired() {
    assertThat(check(baobab, CoverType.FAMILY, member(Relationship.SPOUSE, 40)))
        .extracting(Ineligibility::code)
        .containsExactly(EligibilityChecker.PRINCIPAL_REQUIRED);
    assertThat(
            check(
                baobab,
                CoverType.FAMILY,
                member(Relationship.PRINCIPAL, 40),
                member(Relationship.PRINCIPAL, 41)))
        .extracting(Ineligibility::code)
        .containsExactly(EligibilityChecker.PRINCIPAL_REQUIRED);
  }

  @Test
  void individualCoverIsForThePrincipalOnly() {
    assertThat(
            check(
                baobab,
                CoverType.INDIVIDUAL,
                member(Relationship.PRINCIPAL, 40),
                member(Relationship.CHILD, 5)))
        .extracting(Ineligibility::code, Ineligibility::memberIndex)
        .containsExactly(tuple(EligibilityChecker.INDIVIDUAL_COVER_HAS_DEPENDANTS, 1));
  }

  @Test
  void onlyOneSpouse() {
    assertThat(
            check(
                baobab,
                CoverType.FAMILY,
                member(Relationship.PRINCIPAL, 40),
                member(Relationship.SPOUSE, 40),
                member(Relationship.SPOUSE, 39)))
        .extracting(Ineligibility::code, Ineligibility::memberIndex)
        .containsExactly(tuple(EligibilityChecker.TOO_MANY_SPOUSES, 2));
  }

  @Test
  void atMostMaxChildren() {
    List<Applicant> members = new ArrayList<>(List.of(member(Relationship.PRINCIPAL, 40)));
    for (int i = 0; i < 6; i++) {
      members.add(member(Relationship.CHILD, i + 1));
    }
    assertThat(checker.check(baobab, CoverType.FAMILY, members, START))
        .extracting(Ineligibility::code)
        .containsExactly(EligibilityChecker.TOO_MANY_CHILDREN);
  }

  @Test
  void onlyChildrenCanBeStudents() {
    Applicant studentSpouse =
        new Applicant(Relationship.SPOUSE, Sex.MALE, START.minusYears(30), true);
    assertThat(check(baobab, CoverType.FAMILY, member(Relationship.PRINCIPAL, 40), studentSpouse))
        .extracting(Ineligibility::code, Ineligibility::memberIndex)
        .containsExactly(tuple(EligibilityChecker.STUDENT_ONLY_FOR_CHILDREN, 1));
  }

  @Test
  void aSpouseOutsideTheSpouseRangeIsRefused() {
    assertThat(
            check(
                baobab,
                CoverType.FAMILY,
                member(Relationship.PRINCIPAL, 40),
                member(Relationship.SPOUSE, 70)))
        .extracting(Ineligibility::code, Ineligibility::memberIndex)
        .containsExactly(tuple(EligibilityChecker.AGE_NOT_ELIGIBLE, 1));
  }

  @Test
  void childrenAreRefusedWhenThePlanHasNoChildAges() {
    PlanDefinition noChildren = withoutChildAge(baobab);
    assertThat(
            check(
                noChildren,
                CoverType.FAMILY,
                member(Relationship.PRINCIPAL, 40),
                member(Relationship.CHILD, 3)))
        .extracting(Ineligibility::code)
        .containsExactly(EligibilityChecker.AGE_NOT_ELIGIBLE);
  }

  @Test
  void aMemberBornAfterTheStartIsRefused() {
    Applicant unborn = new Applicant(Relationship.CHILD, Sex.MALE, START.plusDays(10), false);
    assertThat(check(baobab, CoverType.FAMILY, member(Relationship.PRINCIPAL, 40), unborn))
        .extracting(Ineligibility::code, Ineligibility::memberIndex)
        .containsExactly(tuple(EligibilityChecker.AGE_NOT_ELIGIBLE, 1));
  }

  @Test
  void everyProblemIsReported() {
    assertThat(
            check(
                baobab,
                CoverType.INDIVIDUAL,
                member(Relationship.SPOUSE, 70),
                member(Relationship.SPOUSE, 30)))
        .extracting(Ineligibility::code)
        .contains(
            EligibilityChecker.PRINCIPAL_REQUIRED,
            EligibilityChecker.INDIVIDUAL_COVER_HAS_DEPENDANTS,
            EligibilityChecker.TOO_MANY_SPOUSES,
            EligibilityChecker.AGE_NOT_ELIGIBLE);
  }

  private static PlanDefinition withCoverTypes(PlanDefinition plan, List<CoverType> coverTypes) {
    var e = plan.eligibility();
    return copy(
        plan,
        new com.example.insurance.policyservice.catalogue.domain.Eligibility(
            coverTypes, e.principalAge(), e.spouseAge(), e.childAge(), e.maxChildren()));
  }

  private static PlanDefinition withoutChildAge(PlanDefinition plan) {
    var e = plan.eligibility();
    return copy(
        plan,
        new com.example.insurance.policyservice.catalogue.domain.Eligibility(
            e.coverTypes(), e.principalAge(), e.spouseAge(), null, e.maxChildren()));
  }

  private static PlanDefinition copy(
      PlanDefinition p, com.example.insurance.policyservice.catalogue.domain.Eligibility e) {
    return new PlanDefinition(
        p.code(),
        p.name(),
        p.version(),
        p.currency(),
        p.sale(),
        p.ageBasis(),
        e,
        p.modules(),
        p.waitingRules(),
        p.exclusions(),
        p.claimRules(),
        p.cancellation(),
        p.adjustments(),
        p.levies());
  }
}
