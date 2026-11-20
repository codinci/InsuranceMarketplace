package com.example.insurance.policyservice.policy.dto;

import com.example.insurance.policyservice.catalogue.api.Sex;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(description = "The policy holder's details. Email stays with auth-service.")
public record ProfileRequest(
    @Schema(example = "Amina") @NotBlank @Size(max = 100) String firstName,
    @Schema(example = "Otieno") @NotBlank @Size(max = 100) String lastName,
    @Schema(example = "1990-06-15") @NotNull @Past LocalDate dateOfBirth,
    @NotNull Sex sex,
    @Schema(
            description = "Upper-case letters and digits, 5 to 20. Stored encrypted.",
            example = "A1234567")
        @NotBlank
        @Pattern(regexp = "^[A-Z0-9]{5,20}$")
        String nationalId,
    @Schema(example = "+254700000000") @NotBlank @Pattern(regexp = "^\\+?[0-9]{9,15}$")
        String phone) {

  @Override
  public String toString() {
    return "ProfileRequest[firstName=" + firstName + "]";
  }
}
