package com.example.insurance.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts the same Redis image tag as docker-compose and checks what the gateway relies on: the
 * password is required, values can be written with a TTL, and wrong or missing credentials are
 * refused (PLAN 17, M2).
 */
@Testcontainers
class RedisSmokeIT {

  private static final String PASSWORD = "smoke-test-password";

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:8.6.8-alpine"))
          .withCommand(
              "redis-server", "--requirepass", PASSWORD, "--save", "", "--appendonly", "no")
          .withExposedPorts(6379);

  @Test
  void pingWithThePasswordSucceeds() {
    RedisClient client = RedisClient.create(uri(PASSWORD));
    try (StatefulRedisConnection<String, String> connection = client.connect()) {
      assertThat(connection.sync().ping()).isEqualTo("PONG");
    } finally {
      client.shutdown();
    }
  }

  @Test
  void aValueWithATtlIsStoredAndExpires() throws Exception {
    RedisClient client = RedisClient.create(uri(PASSWORD));
    try (StatefulRedisConnection<String, String> connection = client.connect()) {
      connection.sync().setex("rate:ip:10.0.0.1", 1, "3");
      assertThat(connection.sync().get("rate:ip:10.0.0.1")).isEqualTo("3");

      Thread.sleep(Duration.ofMillis(1200).toMillis());
      assertThat(connection.sync().get("rate:ip:10.0.0.1")).isNull();
    } finally {
      client.shutdown();
    }
  }

  @Test
  void aWrongPasswordIsRefused() {
    RedisClient client = RedisClient.create(uri("not-the-password"));
    try {
      assertThatThrownBy(() -> client.connect().close())
          .isInstanceOf(RedisConnectionException.class);
    } finally {
      client.shutdown();
    }
  }

  @Test
  void anUnauthenticatedConnectionCannotRunCommands() {
    RedisClient client =
        RedisClient.create("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
    try {
      // Refused during the connect handshake (NOAUTH), before any command can run.
      assertThatThrownBy(() -> client.connect().close())
          .isInstanceOf(RedisConnectionException.class);
    } finally {
      client.shutdown();
    }
  }

  private static RedisURI uri(String password) {
    return RedisURI.builder()
        .withHost(REDIS.getHost())
        .withPort(REDIS.getMappedPort(6379))
        .withPassword(password.toCharArray())
        .build();
  }
}
