package com.example.insurance.hello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Full application context, so the correlation filter from common-starter is active. The
 * {@code @WebMvcTest} slice in {@link HelloControllerTest} does not load it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class CorrelationIdIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void echoesTheIncomingCorrelationId() throws Exception {
    mockMvc
        .perform(get("/api/v1/hello").header("X-Correlation-Id", "corr-echo-1"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Correlation-Id", "corr-echo-1"));
  }

  @Test
  void generatesACorrelationIdWhenNoneIsSent() throws Exception {
    mockMvc
        .perform(get("/api/v1/hello"))
        .andExpect(status().isOk())
        .andExpect(header().exists("X-Correlation-Id"));
  }

  @Test
  void theSameCorrelationIdAppearsInTheJsonLogLine(CapturedOutput output) throws Exception {
    mockMvc
        .perform(get("/api/v1/hello").header("X-Correlation-Id", "corr-log-7"))
        .andExpect(status().isOk());

    String requestLogLine =
        output
            .getAll()
            .lines()
            .filter(line -> line.contains("hello request served"))
            .findFirst()
            .orElse("");

    assertThat(requestLogLine).contains("\"correlationId\":\"corr-log-7\"");
    assertThat(requestLogLine).startsWith("{");
  }
}
