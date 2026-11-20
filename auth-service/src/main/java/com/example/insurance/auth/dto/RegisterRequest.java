package com.example.insurance.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Self-registration. There is no role or insurer field: registration always creates a CUSTOMER
 * (PLAN 4).
 */
@Schema(description = "Self-registration for a policyholder.")
public record RegisterRequest(
    @Schema(example = "ama@example.com") @NotBlank @Email @Size(max = 254) String email,
    @Schema(
            description = "At least 12 and at most 72 characters.",
            example = "correct horse battery")
        @NotBlank
        @Size(min = 12, max = 72)
        String password,
    @Schema(example = "Amina") @NotBlank @Size(max = 100) String firstName,
    @Schema(example = "Otieno") @NotBlank @Size(max = 100) String lastName) {}
