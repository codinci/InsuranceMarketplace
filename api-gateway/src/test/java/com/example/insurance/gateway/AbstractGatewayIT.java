package com.example.insurance.gateway;

import com.example.insurance.gateway.support.StubUpstream;
import com.example.insurance.gateway.support.TestTokens;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared setup for gateway integration tests: a Redis container with the production image tag, stub
 * upstreams for each service, and the gateway's route and JWKS addresses pointed at the stubs. The
 * payment route is left unset, so it behaves like a service that is not built yet.
 */
public abstract class AbstractGatewayIT {

  static final String REDIS_PASSWORD = "gateway-test-password";

  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:8.6.8-alpine"))
          .withCommand(
              "redis-server", "--requirepass", REDIS_PASSWORD, "--save", "", "--appendonly", "no")
          .withExposedPorts(6379);

  static final StubUpstream AUTH = StubUpstream.start("auth-service").withJwks(TestTokens.jwks());
  static final StubUpstream POLICY = StubUpstream.start("policy-service");
  static final StubUpstream CLAIM = StubUpstream.start("claim-service");

  static {
    REDIS.start();
  }

  @DynamicPropertySource
  static void gatewayProperties(DynamicPropertyRegistry registry) {
    registry.add("GATEWAY_AUTH_URI", AUTH::uri);
    registry.add("GATEWAY_POLICY_URI", POLICY::uri);
    registry.add("GATEWAY_CLAIM_URI", CLAIM::uri);
    registry.add("GATEWAY_JWT_JWK_SET_URI", () -> AUTH.uri() + "/.well-known/jwks.json");
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    registry.add("spring.data.redis.password", () -> REDIS_PASSWORD);
  }

  protected static String bearer(String token) {
    return "Bearer " + token;
  }
}
