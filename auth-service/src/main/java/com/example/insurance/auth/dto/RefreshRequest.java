package com.example.insurance.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "A refresh token, used once and replaced by the response.")
public record RefreshRequest(@NotBlank @Schema(hidden = false) String refreshToken) {}
