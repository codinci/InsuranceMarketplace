package com.example.insurance.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Runs against a real server socket, because the forwarded-address handling is done by Tomcat,
 * which MockMvc skips. The per-IP login limit is set to 2 here, so the third attempt from one
 * client address is throttled, while another client address is not (PLAN 12, ADR-009).
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "auth.login.attempts-per-minute-per-ip=2")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class ClientIpIT extends AbstractAuthIT {

  @LocalServerPort int port;

  private final HttpClient http = HttpClient.newHttpClient();

  @Test
  void theLoginLimitFollowsTheForwardedClientNotTheGateway() throws Exception {
    assertThat(login("198.51.100.7")).isEqualTo(401);
    assertThat(login("198.51.100.7")).isEqualTo(401);
    assertThat(login("198.51.100.7")).isEqualTo(429);

    assertThat(login("198.51.100.8"))
        .as("another client behind the same gateway is not throttled by the first one")
        .isEqualTo(401);
  }

  private int login(String forwardedFor) throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
            .header("Content-Type", "application/json")
            .header("X-Forwarded-For", forwardedFor)
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    json("email", uniqueEmail("ip"), "password", PASSWORD)))
            .build();
    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
    return response.statusCode();
  }
}
