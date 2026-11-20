package com.example.insurance.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Staff account created by an admin. {@code insurerId} is required for ADJUSTER and for insurer
 * ADMIN. Leave it null only when a platform admin creates another platform admin (PLAN 4).
 */
@Schema(
    description = "A staff account for one insurer, or a platform admin when insurerId is null.")
public record CreateStaffRequest(
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(min = 12, max = 72) String password,
    @NotBlank @Size(max = 100) String firstName,
    @NotBlank @Size(max = 100) String lastName,
    @NotBlank @Pattern(regexp = "ADJUSTER|ADMIN") @Schema(allowableValues = {"ADJUSTER", "ADMIN"})
        String role,
    UUID insurerId) {}
