package dev.henriqueverri.pos.integration.outbox;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.github.tomakehurst.wiremock.http.Fault;
import dev.henriqueverri.pos.integration.pulseboard.PulseBoardClient;
import dev.henriqueverri.pos.integration.pulseboard.PulseBoardProperties;
import dev.henriqueverri.pos.support.CapturedLogs;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * One delivery attempt can be followed through the logs by its {@code request_id} ({@code
 * pos-<event id>}, the same {@code X-Request-Id} PulseBoard logs), and no log line ever contains
 * the API key.
 */
class OutboxObservabilityTest extends OutboxIntegrationTest {

  @Autowired private PulseBoardProperties pulseBoard;

  @Test
  void everyLineOfAnAttemptCarriesTheEventsRequestId() throws Exception {
    UUID orderId = payNewOrder();
    OutboxEvent event = onlyEventOf(orderId);
    PULSEBOARD.stubFor(
        post(urlEqualTo(TRANSACTIONS))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("X-Request-Id", event.requestId())
                    .withBody(transactionBody(UUID.randomUUID(), orderId, "paid"))));

    List<ILoggingEvent> attempt;
    try (CapturedLogs logs = CapturedLogs.start()) {
      worker.runOnce();
      attempt =
          logs.events().stream()
              .filter(e -> event.requestId().equals(e.getMDCPropertyMap().get("request_id")))
              .toList();
    }

    // claim → HTTP call → classified outcome, persisted
    assertThat(attempt)
        .extracting(e -> CapturedLogs.fields(e).get("outcome"))
        .containsExactly("claimed", null, "sent");
    assertThat(attempt)
        .allSatisfy(
            e -> assertThat(e.getMDCPropertyMap()).containsEntry("event_id", "" + event.getId()));

    Map<String, Object> claimed = CapturedLogs.fields(attempt.get(0));
    assertThat(claimed)
        .containsEntry("order_id", orderId)
        .containsEntry("external_id", "pos:" + orderId)
        .containsEntry("event_type", OutboxEventType.ORDER_PAID)
        .containsEntry("attempt", 1);

    ILoggingEvent call = attempt.get(1);
    assertThat(call.getLoggerName()).isEqualTo(PulseBoardClient.class.getName());
    assertThat(CapturedLogs.fields(call))
        .containsEntry("target", "pulseboard")
        .containsEntry("operation", "create_transaction")
        .containsEntry("http_status", 201)
        .containsEntry("response_request_id", event.requestId())
        .containsKey("duration_ms");

    assertThat(CapturedLogs.fields(attempt.get(2)))
        .containsEntry("http_status", 201)
        .containsEntry("replayed", false)
        .containsEntry("recorded", true);
  }

  @Test
  void aTransientFailureLogsTheRetryAndThePermanentOneTheFailureKind() throws Exception {
    UUID orderId = payNewOrder();
    PULSEBOARD.stubFor(
        post(urlEqualTo(TRANSACTIONS))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    try (CapturedLogs logs = CapturedLogs.start()) {
      worker.runOnce();

      ILoggingEvent call = logs.from(PulseBoardClient.class).get(0);
      assertThat(call.getLevel()).isEqualTo(Level.WARN);
      assertThat(CapturedLogs.fields(call))
          .containsEntry("error_code", "NETWORK")
          .containsKeys("error_type", "duration_ms");
      assertThat(outcomes(logs)).containsExactly("claimed", "retry_scheduled");
      assertThat(CapturedLogs.fields(logs.from(OutboxWorker.class).get(1)))
          .containsEntry("error_code", "NETWORK")
          .containsKey("next_attempt_at");
    }

    jdbc.update("UPDATE outbox_events SET next_attempt_at = now() WHERE aggregate_id = ?", orderId);
    PULSEBOARD.stubFor(
        post(urlEqualTo(TRANSACTIONS))
            .willReturn(
                aResponse()
                    .withStatus(422)
                    .withBody(
                        "{\"message\":\"The selected product SKU is invalid.\","
                            + "\"code\":\"validation_failed\"}")));
    try (CapturedLogs logs = CapturedLogs.start()) {
      worker.runOnce();

      ILoggingEvent failed = logs.from(OutboxWorker.class).get(1);
      assertThat(CapturedLogs.fields(failed))
          .containsEntry("outcome", "failed")
          .containsEntry("failure_kind", FailureKind.PERMANENT)
          .containsEntry("http_status", 422)
          .containsEntry("error_code", "validation_failed")
          .containsEntry("attempt", 2);
    }
  }

  /** A refund waiting behind a failed payment is reported, not silently stuck. */
  @Test
  void aFailedEventReportsTheEventsItBlocks() throws Exception {
    UUID orderId = payNewOrder();
    refund(orderId);
    PULSEBOARD.stubFor(
        post(urlEqualTo(TRANSACTIONS))
            .willReturn(aResponse().withStatus(422).withBody("{\"code\":\"validation_failed\"}")));

    try (CapturedLogs logs = CapturedLogs.start()) {
      worker.runOnce();

      assertThat(outcomes(logs)).containsExactly("claimed", "failed", "blocking");
      assertThat(CapturedLogs.fields(logs.from(OutboxWorker.class).get(2)))
          .containsEntry("blocked_events", 1L);
    }
  }

  /** The 401 path is the one most likely to leak a key: none of it reaches the logs. */
  @Test
  void theApiKeyNeverAppearsInTheLogs() throws Exception {
    String key = pulseBoard.apiKey();
    String secret = key.substring(key.lastIndexOf('_') + 1);
    UUID paid = payNewOrder();
    PULSEBOARD.stubFor(
        post(urlEqualTo(TRANSACTIONS))
            .willReturn(
                aResponse()
                    .withStatus(401)
                    .withBody("{\"message\":\"Unauthenticated.\",\"code\":\"invalid_api_key\"}")));

    String rendered;
    try (CapturedLogs logs = CapturedLogs.start()) {
      worker.runOnce();
      assertThat(outcomes(logs)).containsExactly("claimed", "failed", "integration_paused");

      pause.clear();
      jdbc.update("DELETE FROM outbox_events");
      payNewOrder();
      PULSEBOARD.stubFor(
          post(urlEqualTo(TRANSACTIONS))
              .willReturn(aResponse().withStatus(201).withBody("{\"data\":{}}")));
      worker.runOnce();
      rendered = logs.rendered();
    }

    assertThat(rendered).contains("pos:" + paid);
    assertThat(rendered).doesNotContain(key).doesNotContain(secret).doesNotContain("Bearer");
  }

  @Test
  void theIntegrationLogsWhenItResumesAfterAPause() throws Exception {
    PULSEBOARD.stubFor(
        post(urlEqualTo(TRANSACTIONS))
            .willReturn(aResponse().withStatus(401).withBody("{\"code\":\"invalid_api_key\"}")));
    payNewOrder();
    worker.runOnce();
    clock.advance(Duration.ofMinutes(16));

    try (CapturedLogs logs = CapturedLogs.start()) {
      worker.runOnce();
      worker.runOnce();

      List<ILoggingEvent> resumed = logs.from(IntegrationPause.class);
      assertThat(resumed).hasSize(1);
      assertThat(CapturedLogs.fields(resumed.get(0)))
          .containsEntry("outcome", "integration_resumed")
          .containsEntry("reason", "cooldown_elapsed");
    }
  }

  private static List<Object> outcomes(CapturedLogs logs) {
    return logs.from(OutboxWorker.class).stream()
        .map(e -> CapturedLogs.fields(e).get("outcome"))
        .toList();
  }
}
