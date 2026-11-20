package com.example.insurance.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import com.example.insurance.gateway.support.TestTokens;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The gateway in the full application context, against stub upstreams and a real Redis (PLAN 17,
 * M2).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GatewayIT extends AbstractGatewayIT {

  private static final String CORRELATION = "X-Correlation-Id";

  @Autowired WebTestClient client;

  @Test
  void aRouteReachesItsServiceAndKeepsThePath() {
    client
        .get()
        .uri("/api/v1/claims/c-42")
        .header("Authorization", bearer(TestTokens.valid()))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .value(body ->
            assertThat(body)
                .contains("\"service\":\"claim-service\"")
                .contains("/api/v1/claims/c-42"));
  }

  @Test
  void theAuthorizationHeaderIsForwardedUnchanged() {
    String token = TestTokens.valid();
    client
        .get()
        .uri("/api/v1/claims/forwarded")
        .header("Authorization", bearer(token))
        .exchange()
        .expectStatus()
        .isOk();

    assertThat(CLAIM.lastHeader("Authorization")).isEqualTo(bearer(token));
  }

  @Test
  void anonymousCatalogueReadWorksWithoutAToken() {
    client.get().uri("/api/v1/insurers").exchange().expectStatus().isOk();
  }

  @Test
  void aCatalogueWriteStillNeedsAToken() {
    client
        .post()
        .uri("/api/v1/insurers")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .contentTypeCompatibleWith("application/problem+json")
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("UNAUTHENTICATED");
  }

  @Test
  void aProtectedRouteWithoutATokenIsUnauthenticatedInTheProblemFormat() {
    client
        .get()
        .uri("/api/v1/claims/none")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("UNAUTHENTICATED");
  }

  @Test
  void aMalformedTokenIsRejectedAtTheEdge() {
    client
        .get()
        .uri("/api/v1/claims/x")
        .header("Authorization", bearer("not.a.jwt"))
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void anExpiredTokenIsRejected() {
    client
        .get()
        .uri("/api/v1/claims/x")
        .header("Authorization", bearer(TestTokens.expired()))
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void aTokenFromAnotherIssuerIsRejected() {
    client
        .get()
        .uri("/api/v1/claims/x")
        .header("Authorization", bearer(TestTokens.wrongIssuer()))
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void aTokenWithAWrongSignatureIsRejected() {
    client
        .get()
        .uri("/api/v1/claims/x")
        .header("Authorization", bearer(TestTokens.wrongSignature()))
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void internalPathsAreNotFoundEvenWithAValidToken() {
    client
        .get()
        .uri("/internal/policies/1")
        .header("Authorization", bearer(TestTokens.valid()))
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("NOT_FOUND");
  }

  @Test
  void anUnknownPathIsNotFoundInTheProblemFormat() {
    client
        .get()
        .uri("/no/such/path")
        .header("Authorization", bearer(TestTokens.valid()))
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("NOT_FOUND");
  }

  @Test
  void aClientCannotSetTheAddressTheServicesSee() {
    client
        .get()
        .uri("/api/v1/claims/spoof")
        .header("Authorization", bearer(TestTokens.valid()))
        .header("X-Forwarded-For", "203.0.113.99")
        .exchange()
        .expectStatus()
        .isOk();

    assertThat(CLAIM.lastHeader("X-Forwarded-For")).isNotNull().doesNotContain("203.0.113.99");
  }

  @Test
  void aValidCorrelationIdIsEchoedAndForwarded() {
    client
        .get()
        .uri("/api/v1/claims/corr")
        .header("Authorization", bearer(TestTokens.valid()))
        .header(CORRELATION, "corr-m2-1")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals(CORRELATION, "corr-m2-1");

    assertThat(CLAIM.lastHeader(CORRELATION)).isEqualTo("corr-m2-1");
  }

  @Test
  void aCorrelationIdIsGeneratedWhenNoneIsSent() {
    client
        .get()
        .uri("/api/v1/insurers")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .exists(CORRELATION);
  }

  @Test
  void aCorrelationIdThatWouldForgeLogLinesIsReplaced() {
    client
        .get()
        .uri("/api/v1/insurers")
        .header(CORRELATION, "bad value with spaces")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .value(CORRELATION, value -> assertThat(value).isNotEqualTo("bad value with spaces"));
  }

  @Test
  void aServiceThatIsNotBuiltYetAnswers503() {
    client
        .get()
        .uri("/api/v1/payments/p-1")
        .header("Authorization", bearer(TestTokens.valid()))
        .exchange()
        .expectStatus()
        .isEqualTo(503)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("UPSTREAM_UNAVAILABLE");
  }

  @Test
  void theAggregatedSwaggerSpecOfTheAuthServiceIsServed() {
    client
        .get()
        .uri("/v3/api-docs/auth")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .value(body -> assertThat(body).contains("auth-service"));
  }

  @Test
  void theAggregatedSwaggerSpecOfThePolicyServiceIsServed() {
    client
        .get()
        .uri("/v3/api-docs/policy")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .value(body ->
            assertThat(body).contains("policy-service").contains("\"path\":\"/v3/api-docs\""));
  }

  @Test
  void corsPreflightIsRefusedWhenNoOriginIsConfigured() {
    client
        .options()
        .uri("/api/v1/insurers")
        .header("Origin", "http://browser.example")
        .header("Access-Control-Request-Method", "GET")
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  @Order(Integer.MAX_VALUE)
  void requestsAreRateLimitedPerClientWithTheProblemFormat() {
    // The burst is 100 (application.properties). Send until this client's bucket is empty.
    for (int attempt = 0; attempt < 300; attempt++) {
      EntityExchangeResult<String> result =
          client.get().uri("/api/v1/insurers").exchange().expectBody(String.class).returnResult();
      if (result.getStatus().value() == 429) {
        assertThat(result.getResponseBody()).contains("\"code\":\"RATE_LIMITED\"");
        assertThat(result.getResponseHeaders().getFirst("X-RateLimit-Remaining")).isNotNull();
        return;
      }
    }
    fail("no request was rate limited within 300 attempts");
  }
}
