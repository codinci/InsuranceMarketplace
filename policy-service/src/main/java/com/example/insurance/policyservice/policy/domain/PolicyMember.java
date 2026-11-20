package com.example.insurance.policyservice.policy.domain;

import com.example.insurance.policyservice.catalogue.api.Relationship;
import com.example.insurance.policyservice.catalogue.api.Sex;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/** A covered person on a policy, with a member number for their card (PLAN 3, 7). */
@Entity
@Table(name = "policy_members")
public class PolicyMember {

  @Id private UUID id;

  @Column(name = "policy_id", nullable = false)
  private UUID policyId;

  @Column(nullable = false)
  private int position;

  @Column(name = "member_number", nullable = false, unique = true, length = 60)
  private String memberNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Relationship relationship;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Sex sex;

  @Column(name = "first_name", nullable = false, length = 100)
  private String firstName;

  @Column(name = "last_name", nullable = false, length = 100)
  private String lastName;

  @Column(name = "date_of_birth", nullable = false)
  private LocalDate dateOfBirth;

  @Column(nullable = false)
  private boolean student;

  protected PolicyMember() {
    // Required by JPA.
  }

  /** The values of a new member. */
  public record Details(
      Relationship relationship,
      Sex sex,
      String firstName,
      String lastName,
      LocalDate dateOfBirth,
      boolean student) {}

  public static PolicyMember of(UUID policyId, int position, String memberNumber, Details details) {
    PolicyMember member = new PolicyMember();
    member.id = UUID.randomUUID();
    member.policyId = policyId;
    member.position = position;
    member.memberNumber = memberNumber;
    member.relationship = details.relationship();
    member.sex = details.sex();
    member.firstName = details.firstName();
    member.lastName = details.lastName();
    member.dateOfBirth = details.dateOfBirth();
    member.student = details.student();
    return member;
  }

  public int getPosition() {
    return position;
  }

  public String getMemberNumber() {
    return memberNumber;
  }

  public Relationship getRelationship() {
    return relationship;
  }

  public Sex getSex() {
    return sex;
  }

  public String getFirstName() {
    return firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public LocalDate getDateOfBirth() {
    return dateOfBirth;
  }

  public boolean isStudent() {
    return student;
  }

  @Override
  public String toString() {
    return "PolicyMember[memberNumber=" + memberNumber + "]";
  }
}
