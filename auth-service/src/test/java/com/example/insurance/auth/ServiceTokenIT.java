package com.example.insurance.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The service-token endpoint end to end (ADR-014): a valid secret gets a SERVICE token, a wrong one
 * does not, and a SERVICE token cannot use staff endpoints.
 */
@SpringBootTest(properties = "auth.service.secrets.policy-service=it-service-secret-1")
@AutoConfigureMockMvc
class ServiceTokenIT extends AbstractAuthIT {

  private static final String PATH = "/internal/service-token";

  @Test
  void aValidSecretGetsAServiceTokenWithTheSingleServiceRole() throws Exception {
    MvcResult result =
        postJson(PATH, json("service", "policy-service", "secret", "it-service-secret-1"), null);

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    String token = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    assertThat(JsonPath.<Integer>read(result.getResponse().getContentAsString(), "$.expiresIn"))
        .isEqualTo(300);
    // The token is usable on a staff endpoint only for SERVICE; admin endpoints need ADMIN.
    MvcResult adminCall =
        mockMvc
            .perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + token))
            .andReturn();
    assertThat(adminCall.getResponse().getStatus()).isEqualTo(403);
  }

  @Test
  void aWrongSecretIsRefusedWithAStableCode() throws Exception {
    MvcResult result = postJson(PATH, json("service", "policy-service", "secret", "guess"), null);

    assertThat(result.getResponse().getStatus()).isEqualTo(401);
    assertThat(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.code"))
        .isEqualTo("INVALID_SERVICE_CREDENTIALS");
  }

  @Test
  void anUnknownServiceGetsTheSameRefusal() throws Exception {
    MvcResult result =
        postJson(PATH, json("service", "claim-service", "secret", "it-service-secret-1"), null);

    assertThat(result.getResponse().getStatus()).isEqualTo(401);
  }

  @Test
  void aMissingFieldIsAValidationFailure() throws Exception {
    MvcResult result = postJson(PATH, json("service", "policy-service"), null);

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(result.getResponse().getContentType())
        .contains(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
  }
}
