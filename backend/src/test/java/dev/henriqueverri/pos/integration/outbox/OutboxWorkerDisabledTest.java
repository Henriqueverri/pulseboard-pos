package dev.henriqueverri.pos.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import dev.henriqueverri.pos.integration.pulseboard.PulseBoardClient;
import dev.henriqueverri.pos.integration.pulseboard.PulseBoardProperties;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OutboxWorkerDisabledTest {

  private static final OutboxProperties SETTINGS =
      new OutboxProperties(
          Duration.ofSeconds(2),
          20,
          Duration.ofMinutes(2),
          10,
          Duration.ofSeconds(10),
          Duration.ofMinutes(15),
          Duration.ofMinutes(15));

  private final OutboxStore store = mock(OutboxStore.class);
  private final PulseBoardClient client = mock(PulseBoardClient.class);
  private final IntegrationPause pause = new IntegrationPause(SETTINGS);

  /** Events are not even claimed: they stay PENDING, untouched, until the integration is on. */
  @ParameterizedTest
  @CsvSource({"false, pb_abcdefghijkl_secret", "true, ''"})
  void doesNothingWhileTheIntegrationIsOff(boolean enabled, String key) {
    assertThat(worker(enabled, key).runOnce()).isZero();
    verifyNoInteractions(store, client);
  }

  @Test
  void doesNothingWhilePaused() {
    Clock clock = Clock.systemUTC();
    pause.pause(clock.instant());

    assertThat(worker(true, "pb_abcdefghijkl_secret").runOnce()).isZero();
    verifyNoInteractions(store, client);
  }

  private OutboxWorker worker(boolean enabled, String key) {
    PulseBoardProperties pulseBoard =
        new PulseBoardProperties(
            enabled,
            URI.create("http://localhost:8000/api/v1"),
            key,
            Duration.ofSeconds(10),
            Duration.ofSeconds(30));
    return new OutboxWorker(
        store,
        client,
        new IngestResponseClassifier(),
        new RetryPolicy(SETTINGS),
        pause,
        pulseBoard,
        SETTINGS,
        Clock.systemUTC());
  }
}
