package com.example.insurance.auth.controller;

import com.example.insurance.auth.dto.CreateStaffRequest;
import com.example.insurance.auth.dto.UserSummary;
import com.example.insurance.auth.service.StaffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(
    name = "Staff",
    description =
        "Creating ADJUSTER and ADMIN accounts. Insurer admins are limited to their own insurer.")
public class AdminUserController {

  private final StaffService staffService;

  public AdminUserController(StaffService staffService) {
    this.staffService = staffService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "Create a staff account",
      description =
          "Requires the ADMIN role. An insurer admin can only name their own insurerId; a platform admin (no insurerId) can name any insurer.")
  @ApiResponse(responseCode = "201", description = "Staff account created")
  @ApiResponse(
      responseCode = "400",
      description =
          "Validation failed, or an adjuster without insurerId (VALIDATION_FAILED, INSURER_REQUIRED)")
  @ApiResponse(responseCode = "401", description = "No valid access token (UNAUTHENTICATED)")
  @ApiResponse(
      responseCode = "403",
      description = "Not an admin, or outside your insurer (FORBIDDEN, INSURER_SCOPE_VIOLATION)")
  @ApiResponse(responseCode = "409", description = "Email already registered (EMAIL_TAKEN)")
  public UserSummary create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateStaffRequest request) {
    return staffService.create(jwt, request);
  }
}
