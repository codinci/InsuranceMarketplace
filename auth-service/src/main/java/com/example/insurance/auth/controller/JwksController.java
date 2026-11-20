package com.example.insurance.auth.controller;

import com.nimbusds.jose.jwk.JWKSet;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public keys that other services use to verify access tokens (PLAN 12). Contains no secret. */
@RestController
@Tag(name = "Keys", description = "Public signing keys for verifying access tokens.")
public class JwksController {

  private final JWKSet publicJwkSet;

  public JwksController(JWKSet publicJwkSet) {
    this.publicJwkSet = publicJwkSet;
  }

  @GetMapping("/.well-known/jwks.json")
  @Operation(
      summary = "JSON Web Key Set",
      description = "The RS256 public keys, identified by kid.")
  @ApiResponse(responseCode = "200", description = "The key set")
  public Map<String, Object> jwks() {
    return publicJwkSet.toJSONObject();
  }
}
