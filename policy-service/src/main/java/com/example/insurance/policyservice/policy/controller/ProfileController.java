package com.example.insurance.policyservice.policy.controller;

import com.example.insurance.policyservice.policy.dto.ProfileRequest;
import com.example.insurance.policyservice.policy.dto.ProfileResponse;
import com.example.insurance.policyservice.policy.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/profile")
@Tag(name = "Profile", description = "The calling customer's own policy-holder details.")
public class ProfileController {

  private final ProfileService profiles;

  public ProfileController(ProfileService profiles) {
    this.profiles = profiles;
  }

  @GetMapping
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Get my profile", description = "CUSTOMER only. The national ID is masked.")
  @ApiResponse(responseCode = "200", description = "The profile")
  @ApiResponse(responseCode = "401", description = "No valid access token (UNAUTHENTICATED)")
  @ApiResponse(responseCode = "403", description = "Not a customer (FORBIDDEN)")
  @ApiResponse(responseCode = "404", description = "No profile yet (PROFILE_NOT_FOUND)")
  public ProfileResponse get(@AuthenticationPrincipal Jwt jwt) {
    return profiles.get(Customers.of(jwt));
  }

  @PutMapping
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "Create or update my profile",
      description = "Needed before buying a policy. The national ID is stored encrypted.")
  @ApiResponse(responseCode = "200", description = "The saved profile")
  @ApiResponse(responseCode = "400", description = "Invalid details (VALIDATION_FAILED)")
  @ApiResponse(responseCode = "401", description = "No valid access token (UNAUTHENTICATED)")
  @ApiResponse(responseCode = "403", description = "Not a customer (FORBIDDEN)")
  public ProfileResponse put(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileRequest request) {
    return profiles.save(Customers.of(jwt), request);
  }
}
