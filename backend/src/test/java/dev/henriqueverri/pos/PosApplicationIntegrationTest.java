package dev.henriqueverri.pos;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import dev.henriqueverri.pos.support.TestcontainersConfiguration;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "management.endpoint.health.show-components=always")
@Import(TestcontainersConfiguration.class)
class PosApplicationIntegrationTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private Flyway flyway;

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void connectsToRealPostgres() {
    String version = jdbcTemplate.queryForObject("SELECT version()", String.class);

    assertThat(version).startsWith("PostgreSQL 16");
  }

  @Test
  void appliesFlywayMigrationsFromScratch() {
    MigrationInfo[] applied = flyway.info().applied();

    assertThat(applied).isNotEmpty();
    assertThat(applied[0].getVersion().getVersion()).isEqualTo("1");
    assertThat(applied).allMatch(migration -> migration.getState() == MigrationState.SUCCESS);
    assertThat(flyway.info().pending()).isEmpty();

    Integer successfulRows =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success",
            Integer.class);
    assertThat(successfulRows).isEqualTo(1);
  }

  @Test
  void healthEndpointIsUpWithDatabase() {
    ResponseEntity<JsonNode> response =
        restTemplate.getForEntity("/actuator/health", JsonNode.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().path("status").asText()).isEqualTo("UP");
    assertThat(response.getBody().path("components").path("db").path("status").asText())
        .isEqualTo("UP");
  }
}
