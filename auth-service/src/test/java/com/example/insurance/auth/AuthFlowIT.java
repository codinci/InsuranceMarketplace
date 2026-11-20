package com.example.insurance.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.example.insurance.common.Roles;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIT extends AbstractAuthIT {

  @Test
  void registrationAlwaysCreatesACustomerAndIgnoresRoleAndInsurerInTheBody() throws Exception {
    String email = uniqueEmail("sneaky");
    MvcResult result =
        postJson(
            "/api/v1/auth/register",
            "{\"email\":\""
                + email
                + "\",\"password\":\""
                + PASSWORD
                + "\",\"firstName\":\"A\",\"lastName\":\"B\","
                + "\"roles\":[\"ADMIN\"],\"insurerId\":\"00000000-0000-0000-0000-00000000000a\"}",
            null);

    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    String body = result.getResponse().getContentAsString();
    assertThat(JsonPath.<java.util.List<String>>read(body, "$.roles"))
        .containsExactly(Roles.CUSTOMER);
    assertThat(JsonPath.<Object>read(body, "$.insurerId")).isNull();
  }

  @Test
  void registeringTheSameEmailTwiceReturnsEmailTaken() throws Exception {
    String email = uniqueEmail("twice");
    postJson(
        "/api/v1/auth/register",
        json("email", email, "password", PASSWORD, "firstName", "A", "lastName", "B"),
        null);

    MvcResult second =
        postJson(
            "/api/v1/auth/register",
            json("email", email, "password", PASSWORD, "firstName", "A", "lastName", "B"),
            null);

    assertThat(second.getResponse().getStatus()).isEqualTo(409);
    assertThat(JsonPath.<String>read(second.getResponse().getContentAsString(), "$.code"))
        .isEqualTo("EMAIL_TAKEN");
  }

  @Test
  void aShortPasswordIsRejectedWithValidationFailed() throws Exception {
    MvcResult result =
        postJson(
            "/api/v1/auth/register",
            json(
                "email",
                uniqueEmail("short"),
                "password",
                "tooshort",
                "firstName",
                "A",
                "lastName",
                "B"),
            null);

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.code"))
        .isEqualTo("VALIDATION_FAILED");
  }

  @Test
  void loginReturnsTokensAndMeReturnsTheSameUser() throws Exception {
    String email = uniqueEmail("me");
    MvcResult loggedIn = registeredCustomerLogin(email);

    assertThat(loggedIn.getResponse().getStatus()).isEqualTo(200);
    String access = accessTokenOf(loggedIn);
    MvcResult me =
        mockMvc
            .perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access))
            .andReturn();

    assertThat(me.getResponse().getStatus()).isEqualTo(200);
    assertThat(JsonPath.<String>read(me.getResponse().getContentAsString(), "$.email"))
        .isEqualTo(email);
  }

  @Test
  void emailIsCaseInsensitiveAndAWrongPasswordIsInvalidCredentials() throws Exception {
    String email = uniqueEmail("case");
    registeredCustomerLogin(email);

    assertThat(login(email.toUpperCase(), PASSWORD).getResponse().getStatus()).isEqualTo(200);
    MvcResult wrong = login(email, "wrong-password-value");
    assertThat(wrong.getResponse().getStatus()).isEqualTo(401);
    assertThat(JsonPath.<String>read(wrong.getResponse().getContentAsString(), "$.code"))
        .isEqualTo("INVALID_CREDENTIALS");
  }

  @Test
  void anUnknownEmailGetsTheSameAnswerAsAWrongPassword() throws Exception {
    MvcResult unknown = login(uniqueEmail("nobody"), PASSWORD);

    assertThat(unknown.getResponse().getStatus()).isEqualTo(401);
    assertThat(JsonPath.<String>read(unknown.getResponse().getContentAsString(), "$.code"))
        .isEqualTo("INVALID_CREDENTIALS");
  }

  @Test
  void fiveWrongPasswordsLockTheAccountEvenForTheRightPassword() throws Exception {
    String email = uniqueEmail("locked");
    registeredCustomerLogin(email);

    for (int attempt = 1; attempt <= 5; attempt++) {
      assertThat(login(email, "wrong-password-value").getResponse().getStatus()).isEqualTo(401);
    }
    MvcResult correctPassword = login(email, PASSWORD);

    assertThat(correctPassword.getResponse().getStatus()).isEqualTo(423);
    assertThat(JsonPath.<String>read(correctPassword.getResponse().getContentAsString(), "$.code"))
        .isEqualTo("ACCOUNT_LOCKED");
  }

  @Test
  void refreshRotatesTheTokenAndReusingTheOldOneRevokesTheWholeLogin() throws Exception {
    String email = uniqueEmail("rotate");
    MvcResult first = registeredCustomerLogin(email);
    String original = refreshTokenOf(first);

    MvcResult rotated = postJson("/api/v1/auth/refresh", json("refreshToken", original), null);
    String replacement =
        JsonPath.read(rotated.getResponse().getContentAsString(), "$.refreshToken");
    assertThat(rotated.getResponse().getStatus()).isEqualTo(200);
    assertThat(replacement).isNotEqualTo(original);

    MvcResult reuse = postJson("/api/v1/auth/refresh", json("refreshToken", original), null);
    assertThat(reuse.getResponse().getStatus()).isEqualTo(401);
    assertThat(JsonPath.<String>read(reuse.getResponse().getContentAsString(), "$.code"))
        .isEqualTo("REFRESH_TOKEN_REUSED");

    // The reuse revoked the family, so the replacement no longer works either. This proves the
    // revocation was saved even though the request failed.
    MvcResult replacementAfterReuse =
        postJson("/api/v1/auth/refresh", json("refreshToken", replacement), null);
    assertThat(replacementAfterReuse.getResponse().getStatus()).isEqualTo(401);
  }

  @Test
  void logoutRevokesTheLoginAndIsIdempotent() throws Exception {
    String email = uniqueEmail("logout");
    String refresh = refreshTokenOf(registeredCustomerLogin(email));

    assertThat(
            postJson("/api/v1/auth/logout", json("refreshToken", refresh), null)
                .getResponse()
                .getStatus())
        .isEqualTo(204);
    assertThat(
            postJson("/api/v1/auth/logout", json("refreshToken", refresh), null)
                .getResponse()
                .getStatus())
        .isEqualTo(204);
    assertThat(
            postJson("/api/v1/auth/refresh", json("refreshToken", refresh), null)
                .getResponse()
                .getStatus())
        .isEqualTo(401);
  }

  @Test
  void meWithoutATokenIsUnauthenticatedInTheProblemFormat() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/auth/me")).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(401);
    assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.code"))
        .isEqualTo("UNAUTHENTICATED");
  }

  @Test
  void theJwksPublishesTheKeyThatSignsAccessTokens() throws Exception {
    MvcResult loggedIn = registeredCustomerLogin(uniqueEmail("jwks"));
    String access = accessTokenOf(loggedIn);
    String jwksJson =
        mockMvc
            .perform(get("/.well-known/jwks.json"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    RSAKey published = (RSAKey) JWKSet.parse(jwksJson).getKeys().get(0);
    Jwt decoded = NimbusJwtDecoder.withPublicKey(published.toRSAPublicKey()).build().decode(access);

    assertThat(decoded.getClaimAsStringList("roles")).containsExactly(Roles.CUSTOMER);
    assertThat(published.isPrivate()).isFalse();
  }
}
