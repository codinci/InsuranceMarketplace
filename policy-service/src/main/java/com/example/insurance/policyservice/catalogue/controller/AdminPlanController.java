package com.example.insurance.policyservice.catalogue.controller;

import com.example.insurance.policyservice.catalogue.dto.AdminPlanResponse;
import com.example.insurance.policyservice.catalogue.dto.PageResponse;
import com.example.insurance.policyservice.catalogue.service.PlanLifecycleService;
import com.example.insurance.policyservice.catalogue.service.StaffScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/plans")
@Tag(
    name = "Catalogue admin",
    description = "Insurers and plan import. Insurer admins act only on their own insurer.")
public class AdminPlanController {

  private final PlanLifecycleService lifecycle;

  public AdminPlanController(PlanLifecycleService lifecycle) {
    this.lifecycle = lifecycle;
  }

  @PostMapping("/{planId}/publish")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "Publish a draft",
      description =
          "Makes the version immutable and puts it on sale. An older published version of the"
              + " same plan is retired in the same step (D33).")
  @ApiResponse(responseCode = "200", description = "Published")
  @ApiResponse(responseCode = "401", description = "No valid access token (UNAUTHENTICATED)")
  @ApiResponse(responseCode = "403", description = "Not an admin (FORBIDDEN)")
  @ApiResponse(responseCode = "404", description = "No such plan, or not yours (PLAN_NOT_FOUND)")
  @ApiResponse(
      responseCode = "409",
      description =
          "Not a draft, or a newer version is published (PLAN_NOT_DRAFT,"
              + " NEWER_VERSION_PUBLISHED)")
  public AdminPlanResponse publish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId) {
    return lifecycle.publish(StaffScope.of(jwt), planId, Actors.of(jwt));
  }

  @PostMapping("/{planId}/retire")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "Retire a published plan",
      description = "Takes it off sale. Existing policies keep their own snapshot.")
  @ApiResponse(responseCode = "200", description = "Retired")
  @ApiResponse(responseCode = "401", description = "No valid access token (UNAUTHENTICATED)")
  @ApiResponse(responseCode = "403", description = "Not an admin (FORBIDDEN)")
  @ApiResponse(responseCode = "404", description = "No such plan, or not yours (PLAN_NOT_FOUND)")
  @ApiResponse(responseCode = "409", description = "Not published (PLAN_NOT_PUBLISHED)")
  public AdminPlanResponse retire(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId) {
    return lifecycle.retire(StaffScope.of(jwt), planId);
  }

  @GetMapping
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "List plan versions",
      description =
          "Newest first. An insurer admin sees only their own insurer; a platform admin may"
              + " filter by insurerId.")
  @ApiResponse(responseCode = "200", description = "One page of plan versions")
  @ApiResponse(responseCode = "400", description = "Bad paging or filter (VALIDATION_FAILED)")
  @ApiResponse(responseCode = "401", description = "No valid access token (UNAUTHENTICATED)")
  @ApiResponse(responseCode = "403", description = "Not an admin (FORBIDDEN)")
  @ApiResponse(
      responseCode = "404",
      description = "The insurerId filter names an insurer you cannot see (INSURER_NOT_FOUND)")
  public PageResponse<AdminPlanResponse> list(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "DRAFT, PUBLISHED or RETIRED")
          @RequestParam(required = false)
          @Pattern(regexp = "DRAFT|PUBLISHED|RETIRED")
          String status,
      @Parameter(description = "Platform admin only: one insurer") @RequestParam(required = false)
          UUID insurerId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return lifecycle.list(
        StaffScope.of(jwt),
        status,
        insurerId,
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
  }
}
