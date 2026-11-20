package com.example.insurance.policyservice.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.insurance.policyservice.AbstractPolicyIT;
import com.example.insurance.policyservice.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class AdminInsurerIT extends AbstractPolicyIT {

  private MvcResult create(String token, String body) throws Exception {
    return mockMvc
        .perform(
            withToken(post("/api/v1/admin/insurers"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andReturn();
  }

  @Test
  void aPlatformAdminCreatesAnInsurer() throws Exception {
    String code = uniqueCode("NEW");
    MvcResult result =
        create(TestTokens.platformAdmin(), "{\"code\":\"" + code + "\",\"name\":\"New Co\"}");
    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    assertThat((String) read(result, "$.code")).isEqualTo(code);
    assertThat((String) read(result, "$.id")).isNotBlank();
  }

  @Test
  void aCodeCanBeUsedOnlyOnce() throws Exception {
    String code = uniqueCode("TWICE");
    createInsurer(code);
    MvcResult again =
        create(TestTokens.platformAdmin(), "{\"code\":\"" + code + "\",\"name\":\"Again\"}");
    assertThat(again.getResponse().getStatus()).isEqualTo(409);
    assertThat((String) read(again, "$.code")).isEqualTo("INSURER_CODE_TAKEN");
  }

  @Test
  void anInvalidCodeIsRefused() throws Exception {
    MvcResult result =
        create(TestTokens.platformAdmin(), "{\"code\":\"lower case\",\"name\":\"X\"}");
    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat((String) read(result, "$.code")).isEqualTo("VALIDATION_FAILED");
  }
}
