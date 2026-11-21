package com.example.insurance.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    description = "A short-lived SERVICE token for calls to another service's /internal endpoints.")
public record ServiceTokenResponse(String accessToken, String tokenType, long expiresIn) {}
