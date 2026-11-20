package com.example.insurance.policyservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The generated OpenAPI document must match {@code docs/openapi/policy-service.json} (PLAN 14, the
 * contract gate). To accept an intended API change, run the build with {@code OPENAPI_UPDATE=1} and
 * commit the new file.
 */
class OpenApiSnapshotIT extends AbstractPolicyIT {

  private static final Path OPENAPI_DIR = Path.of("../docs/openapi");
  private static final Path COMMITTED = OPENAPI_DIR.resolve("policy-service.json");

  @Test
  void theGeneratedSpecMatchesTheCommittedSpec() throws Exception {
    String generated =
        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonMapper mapper = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
    String pretty = lf(mapper.writeValueAsString(mapper.readTree(generated)) + "\n");

    if (System.getenv("OPENAPI_UPDATE") != null) {
      Files.createDirectories(OPENAPI_DIR);
      Files.writeString(COMMITTED, pretty);
    }

    assertThat(Files.exists(COMMITTED))
        .as(
            "docs/openapi/policy-service.json is missing. Run the build with OPENAPI_UPDATE=1 once.")
        .isTrue();
    assertThat(pretty).isEqualTo(lf(Files.readString(COMMITTED)));
  }

  private static String lf(String text) {
    return text.replace("\r\n", "\n");
  }
}
