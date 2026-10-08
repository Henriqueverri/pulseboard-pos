package dev.henriqueverri.pos.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class IntegrationPauseTest {

  private static final Instant NOW = Instant.parse("2026-10-06T21:00:00Z");

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

  @Test
  void notPausedInitially() {
    assertThat(pause.isPaused(NOW)).isFalse();
    assertThat(pause.pausedUntil(NOW)).isNull();
  }

  @Test
  void pausesForFifteenMinutes() {
    assertThat(pause.pause(NOW)).isEqualTo(NOW.plus(Duration.ofMinutes(15)));

    assertThat(pause.isPaused(NOW.plus(Duration.ofMinutes(14)))).isTrue();
    assertThat(pause.pausedUntil(NOW.plusSeconds(1))).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    assertThat(pause.isPaused(NOW.plus(Duration.ofMinutes(15)))).isFalse();
    assertThat(pause.pausedUntil(NOW.plus(Duration.ofMinutes(16)))).isNull();
  }

  @Test
  void clearLiftsThePause() {
    pause.pause(NOW);
    pause.clear();

    assertThat(pause.isPaused(NOW.plusSeconds(1))).isFalse();
  }
}
