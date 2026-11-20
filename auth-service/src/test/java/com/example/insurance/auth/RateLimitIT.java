package com.example.insurance.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Its own context with low limits, so the limit is reached without many requests. */
@SpringBootTest(
    properties = {
      "auth.login.attempts-per-minute-per-ip=3",
      "auth.login.attempts-per-minute-per-email=1000"
    })
@AutoConfigureMockMvc
class RateLimitIT extends AbstractAuthIT {

  @Test
  void theFourthLoginFromOneClientInAMinuteIsRateLimited() throws Exception {
    for (int attempt = 1; attempt <= 3; attempt++) {
      MvcResult result = login(uniqueEmail("burst" + attempt), PASSWORD);
      assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    MvcResult limited = login(uniqueEmail("burst4"), PASSWORD);

    assertThat(limited.getResponse().getStatus()).isEqualTo(429);
    assertThat(JsonPath.<String>read(limited.getResponse().getContentAsString(), "$.code"))
        .isEqualTo("RATE_LIMITED");
  }
}
