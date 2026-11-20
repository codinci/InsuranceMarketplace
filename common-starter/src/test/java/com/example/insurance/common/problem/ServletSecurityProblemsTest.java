package com.example.insurance.common.problem;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

class ServletSecurityProblemsTest {

  @Test
  void aMissingTokenIs401InTheProblemFormat() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    ServletSecurityProblems.entryPoint()
        .commence(new MockHttpServletRequest(), response, new BadCredentialsException("x"));

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentType()).isEqualTo("application/problem+json");
    assertThat(response.getContentAsString())
        .contains("\"code\":\"UNAUTHENTICATED\"")
        .contains("\"status\":401");
  }

  @Test
  void aMissingRoleIs403InTheProblemFormat() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    ServletSecurityProblems.accessDenied()
        .handle(new MockHttpServletRequest(), response, new AccessDeniedException("x"));

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentAsString()).contains("\"code\":\"FORBIDDEN\"");
  }
}
