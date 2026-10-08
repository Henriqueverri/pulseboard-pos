package dev.henriqueverri.pos.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.henriqueverri.pos.integration.PulseBoardHealthIndicator.State;
import dev.henriqueverri.pos.integration.outbox.IntegrationPause;
import dev.henriqueverri.pos.integration.outbox.OutboxProperties;
import dev.henriqueverri.pos.integration.outbox.OutboxRepository;
import dev.henriqueverri.pos.integration.pulseboard.PulseBoardProperties;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.dao.DataAccessResourceFailureException;

class PulseBoardHealthIndicatorTest {

  private static final String KEY = "pb_abcdefghijkl_" + "s3cr3tPartThatMustNeverLeak0000000000000";
  private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

  private final IntegrationPause pause =
      new IntegrationPause(
          new OutboxProperties(
              Duration.ofSeconds(2),
              20,
              Duration.ofMinutes(2),
              10,
              Duration.ofSeconds(10),
              Duration.ofMinutes(15),
              Duration.ofMinutes(15)));
  private final OutboxRepository events = mock(OutboxRepository.class);

  @Test
  void disabled() {
    Health health = health(false, KEY);

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails())
        .containsEntry("state", State.DISABLED)
        .containsEntry("enabled", false);
  }

  @Test
  void enabledWithoutAKeyIsNotConfigured() {
    Health health = health(true, "");

    assertThat(health.getDetails()).containsEntry("state", State.NOT_CONFIGURED);
    assertThat(health.getDetails()).doesNotContainKey("keyPrefix");
  }

  @Test
  void aMalformedKeyIsReportedWithoutExposingAnyPartOfIt() {
    Health health = health(true, "not-a-pulseboard-key-but-maybe-a-secret");

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails()).containsEntry("state", State.INVALID_KEY);
    assertThat(health.getDetails()).doesNotContainKey("keyPrefix");
    assertThat(health.toString()).doesNotContain("maybe-a-secret");
  }

  @Test
  void configuredShowsOnlyThePublicPrefixAndTheBacklog() {
    when(events.countByStatusIn(any())).thenReturn(3L, 1L);

    Health health = health(true, KEY);

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails())
        .containsEntry("state", State.ACTIVE)
        .containsEntry("enabled", true)
        .containsEntry("apiUrl", "http://localhost:8000/api/v1")
        .containsEntry("keyPrefix", "abcdefghijkl")
        .containsEntry("pending", 3L)
        .containsEntry("failed", 1L)
        .doesNotContainKey("pausedUntil");
    assertThat(health.toString()).doesNotContain(KEY).doesNotContain("s3cr3t");
  }

  @Test
  void pausedAfterA401() {
    Instant until = pause.pause(NOW.minusSeconds(60));

    Health health = health(true, KEY);

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails())
        .containsEntry("state", State.PAUSED)
        .containsEntry("pausedUntil", until.toString());
  }

  @Test
  void credentialsInTheUrlAreNotShown() {
    PulseBoardProperties properties =
        properties(true, KEY, URI.create("https://user:pass@pulseboard.example:8443/api/v1"));

    Health health = indicator(properties).health();

    assertThat(health.getDetails())
        .containsEntry("apiUrl", "https://pulseboard.example:8443/api/v1");
  }

  /** The database has its own health component; this one still reports the integration state. */
  @Test
  void aDatabaseOutageOnlyDropsTheBacklog() {
    when(events.countByStatusIn(any())).thenThrow(new DataAccessResourceFailureException("down"));

    Health health = health(true, KEY);

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails())
        .containsEntry("state", State.ACTIVE)
        .doesNotContainKeys("pending", "failed");
  }

  private Health health(boolean enabled, String key) {
    return indicator(properties(enabled, key, URI.create("http://localhost:8000/api/v1"))).health();
  }

  private PulseBoardHealthIndicator indicator(PulseBoardProperties properties) {
    return new PulseBoardHealthIndicator(
        properties, pause, events, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static PulseBoardProperties properties(boolean enabled, String key, URI url) {
    return new PulseBoardProperties(
        enabled, url, key, Duration.ofSeconds(10), Duration.ofSeconds(30));
  }
}
