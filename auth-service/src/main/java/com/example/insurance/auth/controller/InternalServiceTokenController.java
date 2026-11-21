package com.example.insurance.auth.controller;

import com.example.insurance.auth.dto.ServiceTokenRequest;
import com.example.insurance.auth.dto.ServiceTokenResponse;
import com.example.insurance.auth.service.ServiceTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal only: the gateway never routes {@code /internal/**} (PLAN 12, ADR-014). The caller
 * authenticates with its service secret instead of a user token.
 */
@RestController
@RequestMapping("/internal")
@Tag(
    name = "Internal",
    description = "Service-to-service endpoints. Not published through the gateway.")
public class InternalServiceTokenController {

  private final ServiceTokenService serviceTokens;

  public InternalServiceTokenController(ServiceTokenService serviceTokens) {
    this.serviceTokens = serviceTokens;
  }

  @PostMapping("/service-token")
  @Operation(
      summary = "Get a service token",
      description =
          "Trades a service name and its secret for a five-minute SERVICE token. No user token is needed.")
  @ApiResponse(responseCode = "200", description = "Token issued")
  @ApiResponse(responseCode = "400", description = "Validation failed (code VALIDATION_FAILED)")
  @ApiResponse(
      responseCode = "401",
      description = "Unknown service or wrong secret (INVALID_SERVICE_CREDENTIALS)")
  public ServiceTokenResponse serviceToken(@Valid @RequestBody ServiceTokenRequest request) {
    ServiceTokenService.ServiceToken token =
        serviceTokens.issue(request.service(), request.secret());
    return new ServiceTokenResponse(token.value(), "Bearer", token.expiresInSeconds());
  }
}
