package com.example.insurance.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base for integration tests: one Postgres 17 container (the same major version as compose) shared
 * by every test class, and helpers that call the API the way a client would.
 */
@ActiveProfiles("test")
abstract class AbstractAuthIT {

  static final String PLATFORM_ADMIN_EMAIL = "platform.admin@example.com";
  static final String PLATFORM_ADMIN_PASSWORD = "platform-admin-password-1";
  static final String PASSWORD = "a-long-enough-password";

  // One container for the whole test run. The JUnit extension would stop it after each class, but
  // Spring
  // caches application contexts across classes and they keep pointing at this container. Ryuk
  // removes it.
  @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.12-alpine");

  static {
    POSTGRES.start();
  }

  @Autowired protected MockMvc mockMvc;

  /** An email no other test uses, so tests in one context never see each other's accounts. */
  static String uniqueEmail(String prefix) {
    return prefix + "." + UUID.randomUUID() + "@example.com";
  }

  protected MvcResult postJson(String path, String json, String bearerToken) throws Exception {
    var request = post(path).contentType(MediaType.APPLICATION_JSON).content(json);
    if (bearerToken != null) {
      request.header("Authorization", "Bearer " + bearerToken);
    }
    return mockMvc.perform(request).andReturn();
  }

  protected MvcResult login(String email, String password) throws Exception {
    return postJson("/api/v1/auth/login", json("email", email, "password", password), null);
  }

  protected String accessTokenOf(MvcResult loginResult) throws Exception {
    return JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");
  }

  protected String refreshTokenOf(MvcResult loginResult) throws Exception {
    return JsonPath.read(loginResult.getResponse().getContentAsString(), "$.refreshToken");
  }

  protected String platformAdminToken() throws Exception {
    return accessTokenOf(login(PLATFORM_ADMIN_EMAIL, PLATFORM_ADMIN_PASSWORD));
  }

  /** Creates a customer through the public API and returns the login result. */
  protected MvcResult registeredCustomerLogin(String email) throws Exception {
    postJson(
        "/api/v1/auth/register",
        json("email", email, "password", PASSWORD, "firstName", "Amina", "lastName", "Otieno"),
        null);
    return login(email, PASSWORD);
  }

  /** Builds a small JSON object from alternating key and value strings. Test inputs only. */
  static String json(String... keyValues) {
    StringBuilder out = new StringBuilder("{");
    for (int i = 0; i < keyValues.length; i += 2) {
      if (i > 0) {
        out.append(',');
      }
      out.append('"').append(keyValues[i]).append("\":\"").append(keyValues[i + 1]).append('"');
    }
    return out.append('}').toString();
  }
}
