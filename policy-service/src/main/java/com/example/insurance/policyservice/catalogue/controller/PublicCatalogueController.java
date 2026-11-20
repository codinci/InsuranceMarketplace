package com.example.insurance.policyservice.catalogue.controller;

import com.example.insurance.policyservice.catalogue.dto.InsurerResponse;
import com.example.insurance.policyservice.catalogue.dto.PageResponse;
import com.example.insurance.policyservice.catalogue.dto.PlanDetailResponse;
import com.example.insurance.policyservice.catalogue.dto.PlanSummaryResponse;
import com.example.insurance.policyservice.catalogue.service.CatalogueBrowseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(
    name = "Catalogue",
    description = "Browse insurers and plans. Public: no token needed (PLAN 4).")
public class PublicCatalogueController {

  private final CatalogueBrowseService browse;

  public PublicCatalogueController(CatalogueBrowseService browse) {
    this.browse = browse;
  }

  @GetMapping("/insurers")
  @SecurityRequirements
  @Operation(summary = "List insurers", description = "Active insurers, by name.")
  @ApiResponse(responseCode = "200", description = "One page of insurers")
  @ApiResponse(responseCode = "400", description = "Bad paging (VALIDATION_FAILED)")
  public PageResponse<InsurerResponse> insurers(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return browse.insurers(PageRequest.of(page, size, Sort.by("name")));
  }

  @GetMapping("/insurers/{code}/plans")
  @SecurityRequirements
  @Operation(
      summary = "List an insurer's plans on sale",
      description = "Published plans whose sale period includes today, by name.")
  @ApiResponse(responseCode = "200", description = "One page of plans")
  @ApiResponse(responseCode = "400", description = "Bad paging (VALIDATION_FAILED)")
  @ApiResponse(responseCode = "404", description = "No such insurer (INSURER_NOT_FOUND)")
  public PageResponse<PlanSummaryResponse> plans(
      @Parameter(description = "Insurer code", example = "ACACIA") @PathVariable String code,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return browse.plansOnSale(code, PageRequest.of(page, size, Sort.by("name")));
  }

  @GetMapping("/plans/{planId}")
  @SecurityRequirements
  @Operation(
      summary = "Show a plan's terms",
      description =
          "Modules, options, benefits, limits, waiting rules, exclusions and cancellation of a"
              + " published plan. Prices come from quotes.")
  @ApiResponse(responseCode = "200", description = "The plan's terms")
  @ApiResponse(responseCode = "400", description = "Not a valid id (VALIDATION_FAILED)")
  @ApiResponse(responseCode = "404", description = "No such published plan (PLAN_NOT_FOUND)")
  public PlanDetailResponse plan(@PathVariable UUID planId) {
    return browse.plan(planId);
  }
}
