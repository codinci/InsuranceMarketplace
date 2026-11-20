package com.example.insurance.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.insurance.common.web.CorrelationIds;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

/**
 * The servlet correlation filter from common-starter, in the full auth-service context (PLAN 15,
 * M2). It replaces hello-service's test, which is retired in the same milestone.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CorrelationIdIT extends AbstractAuthIT {

  @Test
  void echoesAValidIncomingId() throws Exception {
    mockMvc
        .perform(get("/.well-known/jwks.json").header(CorrelationIds.HEADER, "corr-echo-1"))
        .andExpect(status().isOk())
        .andExpect(header().string(CorrelationIds.HEADER, "corr-echo-1"));
  }

  @Test
  void generatesAnIdWhenNoneIsSent() throws Exception {
    mockMvc
        .perform(get("/.well-known/jwks.json"))
        .andExpect(status().isOk())
        .andExpect(header().exists(CorrelationIds.HEADER));
  }

  @Test
  void replacesAnIdThatWouldForgeLogLines() throws Exception {
    mockMvc
        .perform(
            get("/.well-known/jwks.json").header(CorrelationIds.HEADER, "bad value with spaces"))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(CorrelationIds.HEADER, org.hamcrest.Matchers.not("bad value with spaces")));
  }

  @Test
  void clearsTheMdcSoTheIdCannotLeakIntoTheNextRequestOnThisThread() throws Exception {
    mockMvc
        .perform(get("/.well-known/jwks.json").header(CorrelationIds.HEADER, "corr-leak-check"))
        .andExpect(status().isOk());

    assertThat(MDC.get(CorrelationIds.MDC_KEY)).isNull();
  }
}
