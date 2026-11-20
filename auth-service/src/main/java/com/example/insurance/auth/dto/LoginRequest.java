package com.example.insurance.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Email and password login.")
public record LoginRequest(
    @Schema(example = "ama@example.com") @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(max = 72) String password) {}
