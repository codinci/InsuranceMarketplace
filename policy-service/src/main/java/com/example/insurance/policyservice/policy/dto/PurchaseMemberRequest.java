package com.example.insurance.policyservice.policy.dto;

import com.example.insurance.policyservice.catalogue.api.Relationship;
import com.example.insurance.policyservice.catalogue.api.Sex;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(description = "A quoted member with their name. The quoted fields must not change (D36).")
public record PurchaseMemberRequest(
    @NotNull Relationship relationship,
    @NotNull Sex sex,
    @NotNull LocalDate dateOfBirth,
    boolean student,
    @NotBlank @Size(max = 100) String firstName,
    @NotBlank @Size(max = 100) String lastName) {

  @Override
  public String toString() {
    return "PurchaseMemberRequest[relationship=" + relationship + "]";
  }
}
