package com.example.insurance.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Starts the same Postgres major version as docker-compose and runs the real init script from
 * {@code config/postgres}, so a broken script fails here before it fails in compose.
 */
@Testcontainers
class PostgresSmokeIT {

  private static final Path INIT_SCRIPT = Path.of("../config/postgres/init-databases.sql");

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17.12-alpine")
          .withCopyFileToContainer(
              MountableFile.forHostPath(INIT_SCRIPT.toAbsolutePath()),
              "/docker-entrypoint-initdb.d/10-databases.sql");

  @Test
  void connectsAndRunsASimpleQuery() throws SQLException {
    try (Connection connection = connect("postgres");
        Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery("SELECT 1")) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getInt(1)).isEqualTo(1);
    }
  }

  @Test
  void initScriptCreatesOneDatabasePerService() throws SQLException {
    List<String> databases = new ArrayList<>();
    try (Connection connection = connect("postgres");
        Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery("SELECT datname FROM pg_database ORDER BY datname")) {
      while (rs.next()) {
        databases.add(rs.getString(1));
      }
    }

    assertThat(databases)
        .contains("auth_db", "policy_db", "claim_db", "payment_db", "notification_db");
  }

  private Connection connect(String database) throws SQLException {
    String url =
        "jdbc:postgresql://"
            + POSTGRES.getHost()
            + ":"
            + POSTGRES.getMappedPort(5432)
            + "/"
            + database;
    return DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
  }
}
