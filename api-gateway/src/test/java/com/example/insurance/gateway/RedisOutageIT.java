package com.example.insurance.gateway;

import com.example.insurance.gateway.support.StubUpstream;
import com.example.insurance.gateway.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Redis goes down while the gateway runs. Requests must keep flowing (fail open, ADR-009). Own
 * container and a short Redis timeout so a stopped Redis cannot stall the test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class RedisOutageIT {

  private static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:8.6.8-alpine"))
          .withCommand("redis-server", "--save", "", "--appendonly", "no")
          .withExposedPorts(6379);

  private static final StubUpstream CLAIM = StubUpstream.start("claim-service");
  private static final StubUpstream AUTH =
      StubUpstream.start("auth-service").withJwks(TestTokens.jwks());

  static {
    REDIS.start();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("GATEWAY_CLAIM_URI", CLAIM::uri);
    registry.add("GATEWAY_AUTH_URI", AUTH::uri);
    registry.add("GATEWAY_JWT_JWK_SET_URI", () -> AUTH.uri() + "/.well-known/jwks.json");
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    registry.add("spring.data.redis.timeout", () -> "300ms");
  }

  @Autowired WebTestClient client;

  @Test
  void requestsKeepFlowingWhenRedisIsDown() {
    client
        .get()
        .uri("/api/v1/claims/before-outage")
        .header("Authorization", "Bearer " + TestTokens.valid())
        .exchange()
        .expectStatus()
        .isOk();

    REDIS.stop();

    client
        .get()
        .uri("/api/v1/claims/during-outage")
        .header("Authorization", "Bearer " + TestTokens.valid())
        .exchange()
        .expectStatus()
        .isOk();
  }
}
