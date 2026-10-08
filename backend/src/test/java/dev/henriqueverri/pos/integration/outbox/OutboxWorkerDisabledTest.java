package dev.henriqueverri.pos.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import dev.henriqueverri.pos.integration.pulseboard.PulseBoardClient;
import dev.henriqueverri.pos.integration.pulseboard.PulseBoardProperties;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OutboxWorkerDisabledTest {

  /** Events are not even claimed: they stay PENDING, untouched, until the integration is on. */
  @ParameterizedTest
  @CsvSource({"false, pb_abcdefghijkl_secret", "true, ''"})
  void doesNothingWhileTheIntegrationIsOff(boolean enabled, String key) {
    OutboxStore store = mock(OutboxStore.class);
    PulseBoardClient client = mock(PulseBoardClient.class);
    PulseBoardProperties pulseBoard =
        new PulseBoardProperties(
            enabled,
            URI.create("http://localhost:8000/api/v1"),
            key,
            Duration.ofSeconds(10),
            Duration.ofSeconds(30));
    OutboxWorker worker =
        new OutboxWorker(
            store,
            client,
            pulseBoard,
            new OutboxProperties(Duration.ofSeconds(2), 20, Duration.ofMinutes(2)),
            Clock.systemUTC());

    assertThat(worker.runOnce()).isZero();
    verifyNoInteractions(store, client);
  }
}
