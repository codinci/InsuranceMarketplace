package com.example.insurance.auth.controller;

import com.example.insurance.auth.dto.LoginRequest;
import com.example.insurance.auth.dto.RefreshRequest;
import com.example.insurance.auth.dto.RegisterRequest;
import com.example.insurance.auth.dto.TokenResponse;
import com.example.insurance.auth.dto.UserSummary;
import com.example.insurance.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(
    name = "Auth",
    description = "Registration, login, token refresh and logout for policyholders and staff.")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Register a policyholder",
      description =
          "Creates a CUSTOMER account. Role and insurer are set by the server, never from the request.")
  @ApiResponse(responseCode = "201", description = "Account created")
  @ApiResponse(responseCode = "400", description = "Validation failed (code VALIDATION_FAILED)")
  @ApiResponse(responseCode = "409", description = "Email already registered (code EMAIL_TAKEN)")
  public UserSummary register(@Valid @RequestBody RegisterRequest request) {
    return authService.register(request);
  }

  @PostMapping("/login")
  @Operation(
      summary = "Log in with email and password",
      description = "Returns an access token (15 minutes) and a refresh token (7 days).")
  @ApiResponse(responseCode = "200", description = "Logged in")
  @ApiResponse(responseCode = "400", description = "Validation failed (code VALIDATION_FAILED)")
  @ApiResponse(
      responseCode = "401",
      description = "Wrong email or password (code INVALID_CREDENTIALS)")
  @ApiResponse(
      responseCode = "423",
      description = "Account locked after repeated failures (code ACCOUNT_LOCKED)")
  @ApiResponse(responseCode = "429", description = "Too many attempts (code RATE_LIMITED)")
  public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
    return authService.login(request, http.getRemoteAddr());
  }

  @PostMapping("/refresh")
  @Operation(
      summary = "Rotate tokens",
      description =
          "Replaces the refresh token. Presenting an already used one revokes the whole login (code REFRESH_TOKEN_REUSED).")
  @ApiResponse(responseCode = "200", description = "New tokens issued")
  @ApiResponse(
      responseCode = "401",
      description =
          "Token invalid, expired or reused (INVALID_REFRESH_TOKEN, REFRESH_TOKEN_REUSED)")
  public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
    return authService.refresh(request);
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Log out",
      description =
          "Revokes the refresh token's login. Idempotent: an unknown token also returns 204.")
  @ApiResponse(responseCode = "204", description = "Logged out")
  public void logout(@Valid @RequestBody RefreshRequest request) {
    authService.logout(request);
  }

  @GetMapping("/me")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "The signed-in user",
      description = "Reads the current account from the token's subject.")
  @ApiResponse(responseCode = "200", description = "The current user")
  @ApiResponse(responseCode = "401", description = "No valid access token (code UNAUTHENTICATED)")
  public UserSummary me(@AuthenticationPrincipal Jwt jwt) {
    return authService.me(
        UUID.fromString(Objects.requireNonNull(jwt.getSubject(), "token has no subject")));
  }
}
