package com.example.insurance.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A new access token and the refresh token that replaces the previous one.")
public record TokenResponse(
    String accessToken, String tokenType, long expiresIn, String refreshToken) {}
