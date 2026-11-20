package com.example.insurance.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The generated OpenAPI document must match the committed {@code docs/openapi/auth-service.json}
 * (PLAN 14, the contract gate in verify.sh). To accept an intended API change, run the build with
 * {@code OPENAPI_UPDATE=1} and commit the new file.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiSnapshotIT extends AbstractAuthIT {

  private static final Path OPENAPI_DIR = Path.of("../docs/openapi");
  private static final Path COMMITTED = OPENAPI_DIR.resolve("auth-service.json");

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
    // Jackson uses the platform line separator, so both sides are compared with LF only (Git stores
    // LF).
    String pretty = lf(mapper.writeValueAsString(mapper.readTree(generated)) + "\n");

    if (System.getenv("OPENAPI_UPDATE") != null) {
      Files.createDirectories(OPENAPI_DIR);
      Files.writeString(COMMITTED, pretty);
    }

    assertThat(Files.exists(COMMITTED))
        .as("docs/openapi/auth-service.json is missing. Run the build with OPENAPI_UPDATE=1 once.")
        .isTrue();
    assertThat(pretty).isEqualTo(lf(Files.readString(COMMITTED)));
  }

  private static String lf(String text) {
    return text.replace("\r\n", "\n");
  }
}
