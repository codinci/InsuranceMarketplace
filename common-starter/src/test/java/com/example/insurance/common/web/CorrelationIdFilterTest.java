package com.example.insurance.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

  private final CorrelationIdFilter filter = new CorrelationIdFilter();

  @Test
  void echoesAValidIncomingIdOnTheResponse() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/hello");
    request.addHeader(CorrelationIdFilter.HEADER, "corr-abc123");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChainNoOp());

    assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("corr-abc123");
  }

  @Test
  void generatesAnIdWhenTheHeaderIsMissing() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/hello");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChainNoOp());

    assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isNotBlank();
    assertThat(response.getHeader(CorrelationIdFilter.HEADER)).hasSize(36); // UUID text form
  }

  @Test
  void replacesAnInvalidIncomingIdSoClientsCannotInjectLogText() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/hello");
    request.addHeader(CorrelationIdFilter.HEADER, "bad id\nFAKE LOG LINE");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChainNoOp());

    assertThat(response.getHeader(CorrelationIdFilter.HEADER)).doesNotContain("\n", "FAKE");
    assertThat(response.getHeader(CorrelationIdFilter.HEADER)).hasSize(36);
  }

  @Test
  void rejectsAnIdThatIsTooLong() {
    String tooLong = "a".repeat(65);

    assertThat(CorrelationIdFilter.resolve(tooLong)).isNotEqualTo(tooLong);
  }

  @Test
  void putsTheIdInTheMdcDuringTheRequestAndRemovesItAfterwards() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/hello");
    request.addHeader(CorrelationIdFilter.HEADER, "corr-xyz");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> seenInChain = new AtomicReference<>();
    FilterChain chain = (req, res) -> seenInChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));

    filter.doFilter(request, response, chain);

    assertThat(seenInChain.get()).isEqualTo("corr-xyz");
    assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
  }

  @Test
  void clearsTheMdcEvenWhenTheChainFails() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/hello");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain failing =
        (ServletRequest req, ServletResponse res) -> {
          throw new IllegalStateException("boom");
        };

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> filter.doFilter(request, response, failing))
        .isInstanceOf(IllegalStateException.class);

    assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
  }

  /** A chain that does nothing, for tests that only check the response header. */
  private static final class MockFilterChainNoOp implements FilterChain {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response) {
      // no-op
    }
  }
}
