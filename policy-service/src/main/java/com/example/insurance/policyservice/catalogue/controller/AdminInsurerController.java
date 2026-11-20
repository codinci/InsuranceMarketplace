package com.example.insurance.policyservice.catalogue.controller;

import com.example.insurance.policyservice.catalogue.dto.CreateInsurerRequest;
import com.example.insurance.policyservice.catalogue.dto.ImportResponse;
import com.example.insurance.policyservice.catalogue.dto.InsurerResponse;
import com.example.insurance.policyservice.catalogue.service.ImportOutcome;
import com.example.insurance.policyservice.catalogue.service.InsurerService;
import com.example.insurance.policyservice.catalogue.service.PlanImportService;
import com.example.insurance.policyservice.catalogue.service.StaffScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/insurers")
@Tag(
    name = "Catalogue admin",
    description = "Insurers and plan import. Insurer admins act only on their own insurer.")
public class AdminInsurerController {

  private final InsurerService insurerService;
  private final PlanImportService importService;

  public AdminInsurerController(InsurerService insurerService, PlanImportService importService) {
    this.insurerService = insurerService;
    this.importService = importService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Create an insurer", description = "Platform admin only (PLAN 4).")
  @ApiResponse(responseCode = "201", description = "Insurer created")
  @ApiResponse(responseCode = "400", description = "Invalid code or name (VALIDATION_FAILED)")
  @ApiResponse(responseCode = "401", description = "No valid access token (UNAUTHENTICATED)")
  @ApiResponse(
      responseCode = "403",
      description = "Not an admin, or an insurer admin (FORBIDDEN, PLATFORM_ADMIN_REQUIRED)")
  @ApiResponse(responseCode = "409", description = "Code already used (INSURER_CODE_TAKEN)")
  public InsurerResponse create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateInsurerRequest request) {
    return insurerService.create(StaffScope.of(jwt), request);
  }

  @PostMapping(path = "/{insurerId}/plans/import", consumes = MediaType.APPLICATION_JSON_VALUE)
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "Import a plan document",
      description =
          "Validates the document against the schema and the semantic rules"
              + " (docs/catalogue-schema.md). With dryRun=true nothing is stored. Otherwise a new"
              + " version is stored as DRAFT, and an existing DRAFT of the same version is"
              + " replaced (D34). Maximum 256 KB.",
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              required = true,
              description = "A plan document, schema version 1.",
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE,
                      schema = @Schema(type = "object"))))
  @ApiResponse(responseCode = "200", description = "Valid (dry run) or a draft replaced")
  @ApiResponse(responseCode = "201", description = "A new draft version stored")
  @ApiResponse(responseCode = "401", description = "No valid access token (UNAUTHENTICATED)")
  @ApiResponse(responseCode = "403", description = "Not an admin (FORBIDDEN)")
  @ApiResponse(
      responseCode = "404",
      description = "No such insurer, or not yours (INSURER_NOT_FOUND)")
  @ApiResponse(
      responseCode = "409",
      description = "That version is published or retired (PLAN_VERSION_IMMUTABLE)")
  @ApiResponse(responseCode = "413", description = "Document too large (PLAN_TOO_LARGE)")
  @ApiResponse(
      responseCode = "422",
      description =
          "Invalid document, every problem in errors (PLAN_INVALID), or the document names"
              + " another insurer (INSURER_MISMATCH)")
  public ResponseEntity<ImportResponse> importPlan(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID insurerId,
      @Parameter(description = "Validate only; store nothing.")
          @RequestParam(defaultValue = "false")
          boolean dryRun,
      HttpServletRequest request)
      throws IOException {
    // Read at most one byte more than allowed, so an oversized body is never held in memory.
    byte[] body = request.getInputStream().readNBytes(PlanImportService.MAX_DOCUMENT_BYTES + 1);
    ImportOutcome outcome =
        importService.importPlan(StaffScope.of(jwt), insurerId, body, dryRun, Actors.of(jwt));
    HttpStatus status =
        outcome.result() == ImportOutcome.Result.CREATED ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status)
        .body(new ImportResponse(outcome.result().name(), outcome.plan()));
  }
}
