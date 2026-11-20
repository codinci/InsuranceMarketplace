package com.example.insurance.policyservice.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.example.insurance.policyservice.AbstractPolicyIT;
import com.example.insurance.policyservice.support.TestTokens;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/** The customer's own profile, with the national ID encrypted at rest (D37, PLAN 12). */
class ProfileIT extends AbstractPolicyIT {

  static final String PROFILE =
      """
      {"firstName":"Amina","lastName":"Otieno","dateOfBirth":"1990-06-15","sex":"FEMALE",
       "nationalId":"A1234567","phone":"+254700000000"}
      """;

  @Autowired JdbcTemplate jdbc;

  private MvcResult putProfile(String token, String body) throws Exception {
    return mockMvc
        .perform(
            withToken(put("/api/v1/me/profile"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andReturn();
  }

  private MvcResult getProfile(String token) throws Exception {
    return mockMvc.perform(withToken(get("/api/v1/me/profile"), token)).andReturn();
  }

  @Test
  void aProfileIsSavedWithTheNationalIdEncryptedAndMasked() throws Exception {
    UUID userId = UUID.randomUUID();
    MvcResult saved = putProfile(TestTokens.customer(userId), PROFILE);
    assertThat(saved.getResponse().getStatus()).isEqualTo(200);
    assertThat((String) read(saved, "$.nationalIdMasked")).isEqualTo("***567");
    assertThat(saved.getResponse().getContentAsString()).doesNotContain("A1234567");

    String column =
        jdbc.queryForObject(
            "select national_id from policy_holders where user_id = ?", String.class, userId);
    assertThat(column).startsWith("v1:test-key:").doesNotContain("A1234567");

    MvcResult read = getProfile(TestTokens.customer(userId));
    assertThat((String) read(read, "$.firstName")).isEqualTo("Amina");
    assertThat((String) read(read, "$.nationalIdMasked")).isEqualTo("***567");
  }

  @Test
  void aSecondPutUpdatesTheSameProfile() throws Exception {
    UUID userId = UUID.randomUUID();
    putProfile(TestTokens.customer(userId), PROFILE);
    MvcResult updated =
        putProfile(TestTokens.customer(userId), PROFILE.replace("Amina", "Wanjiru"));
    assertThat((String) read(updated, "$.firstName")).isEqualTo("Wanjiru");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from policy_holders where user_id = ?", Integer.class, userId))
        .isEqualTo(1);
  }

  @Test
  void eachCustomerSeesOnlyTheirOwnProfile() throws Exception {
    putProfile(TestTokens.customer(UUID.randomUUID()), PROFILE);
    MvcResult other = getProfile(TestTokens.customer(UUID.randomUUID()));
    assertThat(other.getResponse().getStatus()).isEqualTo(404);
    assertThat((String) read(other, "$.code")).isEqualTo("PROFILE_NOT_FOUND");
  }

  @Test
  void invalidDetailsAreRefused() throws Exception {
    MvcResult badId = putProfile(TestTokens.customer(), PROFILE.replace("A1234567", "a-1"));
    assertThat(badId.getResponse().getStatus()).isEqualTo(400);
    assertThat((String) read(badId, "$.code")).isEqualTo("VALIDATION_FAILED");
    MvcResult future =
        putProfile(TestTokens.customer(), PROFILE.replace("1990-06-15", "2999-01-01"));
    assertThat(future.getResponse().getStatus()).isEqualTo(400);
  }

  @Test
  void staffAndAnonymousCallersCannotUseProfiles() throws Exception {
    assertThat(getProfile(TestTokens.platformAdmin()).getResponse().getStatus()).isEqualTo(403);
    assertThat(
            putProfile(TestTokens.adjuster(UUID.randomUUID()), PROFILE).getResponse().getStatus())
        .isEqualTo(403);
    assertThat(getProfile(null).getResponse().getStatus()).isEqualTo(401);
  }
}
