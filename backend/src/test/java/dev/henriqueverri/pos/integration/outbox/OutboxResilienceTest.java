package dev.henriqueverri.pos.integration.outbox;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Classified outcomes end to end: backoff, Retry-After, pause on 401, reconciliation, retry. */
class OutboxResilienceTest extends OutboxIntegrationTest {

  private static final String UNAUTHORIZED =
      "{\"message\":\"Unauthenticated.\",\"code\":\"invalid_api_key\"}";

  @Test
  void serverErrorsBackOffUntilExhausted() throws Exception {
    UUID orderId = payNewOrder();
    PULSEBOARD.stubFor(
        post(urlEqualTo(TRANSACTIONS))
            .willReturn(aResponse().withStatus(503).withBody("{\"message\":\"Server Error\"}")));

    for (int attempt = 1; attempt <= 9; attempt++) {
      Instant before = clock.instant();
      assertThat(worker.runOnce()).isEqualTo(1);
      OutboxEvent waiting = onlyEventOf(orderId);
      assertThat(waiting.getStatus()).isEqualTo(OutboxStatus.PENDING);
      assertThat(waiting.getAttempts()).isEqualTo(attempt);
      assertThat(waiting.getLastErrorCode()).isEqualTo("HTTP_5XX");
      assertThat(waiting.getLastHttpStatus()).isEqualTo(503);
      Duration nominal = Duration.ofSeconds(Math.min(900, 10L << (attempt - 1)));
      assertThat(waiting.getNextAttemptAt())
          .isAfterOrEqualTo(before.plus(nominal.dividedBy(2)).minusMillis(1))
          .isBeforeOrEqualTo(clock.instant().plus(nominal).plusMillis(1));

      // Not due yet: nothing is claimed.
      assertThat(worker.runOnce()).isZero();
      clock.advance(nominal.plusSeconds(1));
    }

    worker.runOnce();

    OutboxEvent exhausted = onlyEventOf(orderId);
    assertThat(exhausted.getStatus()).isEqualTo(OutboxStatus.FAILED);
    assertThat(exhausted.getFailureKind()).isEqualTo(FailureKind.EXHAUSTED);
    assertThat(exhausted.getAttempts()).isEqualTo(10);
    assertThat(exhausted.getLastErrorCode()).isEqualTo("HTTP_5XX");
    PULSEBOARD.verify(10, postRequestedFor(urlEqualTo(TRANSACTIONS)));

    clock.advance(Duration.ofHours(1));
    assertThat(worker.runOnce()).isZero();
  }

  @Test
  void rateLimitingWaitsForRetryAfter() throws Exception {
    UUID orderId = payNewOrder();
    PULSEBOARD.stubFor(
        post(urlEqualTo(TRANSACTIONS))
            .willReturn(
                aResponse()
                    .withStatus(429)
                    .withHeader("Retry-After", "120")
                    .withBody("{\"message\":\"Too Many Attempts.\",\"code\":\"rate_limited\"}")));

    worker.runOnce();

    OutboxEvent waiting = onlyEventOf(orderId);
    assertThat(waiting.getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(waiting.getLastErrorCode()).isEqualTo("rate_limited");
    assertThat(Duration.between(waiting.getLastAttemptAt(), waiting.getNextAttemptAt()))
        .isBetween(Duration.ofSeconds(119), Duration.ofSeconds(121));
  }

  @Test
  void rateLimitingWithoutRetryAfterWaitsSixtySeconds() throws Exception {
    UUID orderId = payNewOrder();
    PULSEBOARD.stubFor(post(urlEqualTo(TRANSACTIONS)).willReturn(aResponse().withStatus(429)));

    worker.runOnce();

    OutboxEvent waiting = onlyEventOf(orderId);
    assertThat(Duration.between(waiting.getLastAttemptAt(), waiting.getNextAttemptAt()))
        .isBetween(Duration.ofSeconds(59), Duration.ofSeconds(61));
  }

  /** Section 8.7 of the plan: one 401 pauses everything; at most one request per cooldown. */
  @Test
  void anInvalidKeyPausesTheIntegrationWithoutALoop() throws Exception {
    UUID first = payNewOrder();
    UUID second = payNewOrder();
    PULSEBOARD.stubFor(
        post(urlEqualTo(TRANSACTIONS))
            .willReturn(aResponse().withStatus(401).withBody(UNAUTHORIZED)));
    Instant before = clock.instant();

    assertThat(worker.runOnce()).isEqualTo(2);

    OutboxEvent rejected = onlyEventOf(first);
    assertThat(rejected.getStatus()).isEqualTo(OutboxStatus.FAILED);
    assertThat(rejected.getFailureKind()).isEqualTo(FailureKind.CONFIGURATION);
    assertThat(rejected.getLastErrorCode()).isEqualTo("invalid_api_key");
    assertThat(rejected.getLastHttpStatus()).isEqualTo(401);
    // The rest of the batch is given back untouched: not attempted, not counted.
    OutboxEvent released = onlyEventOf(second);
    assertThat(released.getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(released.getAttempts()).isZero();
    PULSEBOARD.verify(1, postRequestedFor(urlEqualTo(TRANSACTIONS)));

    Instant pausedUntil = pause.pausedUntil(clock.instant());
    assertThat(pausedUntil)
        .isAfterOrEqualTo(before.plus(Duration.ofMinutes(15)))
        .isBeforeOrEqualTo(clock.instant().plus(Duration.ofMinutes(15)));
    getJson("/api/integration/health")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pausedUntil").value(pausedUntil.toString()))
        .andExpect(jsonPath("$.failed").value(1))
        .andExpect(jsonPath("$.pending").value(1));

    // While paused: nothing is claimed, no request goes out.
    clock.advance(Duration.ofMinutes(14));
    assertThat(worker.runOnce()).isZero();
    PULSEBOARD.verify(1, postRequestedFor(urlEqualTo(TRANSACTIONS)));

    // After the cooldown only PENDING events are claimed; the key is still bad, so it pauses again.
    clock.advance(Duration.ofMinutes(1).plusSeconds(1));
    assertThat(worker.runOnce()).isEqualTo(1);
    assertThat(onlyEventOf(second).getFailureKind()).isEqualTo(FailureKind.CONFIGURATION);
    assertThat(pause.isPaused(clock.instant())).isTrue();
    PULSEBOARD.verify(2, postRequestedFor(urlEqualTo(TRANSACTIONS)));

    // The key is fixed (restart): "Reprocessar falhas de configuração" resumes everything at once.
    PULSEBOARD.resetAll();
    PULSEBOARD.stubFor(
        post(urlEqualTo(TRANSACTIONS)).willReturn(aResponse().withStatus(201).withBody("{}")));
    postJson("/api/integration/events/retry-configuration-failures", "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.retried").value(2));
    assertThat(pause.isPaused(clock.instant())).isFalse();
    getJson("/api/integration/health").andExpect(jsonPath("$.pausedUntil").doesNotExist());

    assertThat(worker.runOnce()).isEqualTo(2);
    assertThat(onlyEventOf(first).getStatus()).isEqualTo(OutboxStatus.SENT);
    assertThat(onlyEventOf(second).getStatus()).isEqualTo(OutboxStatus.SENT);
  }

  @Test
  void aRefundAlreadyRecordedIsReconciledAsSent() throws Exception {
    UUID orderId = paidAndDeliveredThenRefunded();
    UUID remoteId = UUID.randomUUID();
    stubInvalidTransition(orderId);
    PULSEBOARD.stubFor(
        get(urlEqualTo(TRANSACTIONS + "/pos:" + orderId))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(transactionBody(remoteId, orderId, "refunded"))));

    assertThat(worker.runOnce()).isEqualTo(1);

    OutboxEvent refund = events.findByAggregateIdOrderBySequence(orderId).get(1);
    assertThat(refund.getStatus()).isEqualTo(OutboxStatus.SENT);
    assertThat(refund.getRemoteId()).isEqualTo(remoteId);
    assertThat(refund.getLastHttpStatus()).isEqualTo(409);
    PULSEBOARD.verify(1, getRequestedFor(urlEqualTo(TRANSACTIONS + "/pos:" + orderId)));
  }

  @Test
  void aRefundThatDivergesFromPulseBoardFailsPermanently() throws Exception {
    UUID orderId = paidAndDeliveredThenRefunded();
    stubInvalidTransition(orderId);
    PULSEBOARD.stubFor(
        get(urlEqualTo(TRANSACTIONS + "/pos:" + orderId))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(transactionBody(UUID.randomUUID(), orderId, "canceled"))));

    worker.runOnce();

    OutboxEvent refund = events.findByAggregateIdOrderBySequence(orderId).get(1);
    assertThat(refund.getStatus()).isEqualTo(OutboxStatus.FAILED);
    assertThat(refund.getFailureKind()).isEqualTo(FailureKind.PERMANENT);
    assertThat(refund.getLastErrorCode()).isEqualTo("invalid_transition");
    assertThat(refund.getLastError()).contains("status no PulseBoard: canceled");
  }

  @Test
  void aFailedLookupDuringReconciliationIsRetriedLater() throws Exception {
    UUID orderId = paidAndDeliveredThenRefunded();
    stubInvalidTransition(orderId);
    PULSEBOARD.stubFor(
        get(urlEqualTo(TRANSACTIONS + "/pos:" + orderId)).willReturn(aResponse().withStatus(502)));

    worker.runOnce();

    OutboxEvent refund = events.findByAggregateIdOrderBySequence(orderId).get(1);
    assertThat(refund.getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(refund.getLastErrorCode()).isEqualTo("HTTP_5XX");
  }

  /**
   * Manual retry keeps {@code attempts} as history and restarts the limit and the backoff: after an
   * exhausted event is retried, its next failure waits ~10 s again instead of failing at once.
   */
  @Test
  void manualRetryRestartsTheAttemptLimit() throws Exception {
    UUID orderId = payNewOrder();
    OutboxEvent event = onlyEventOf(orderId);
    jdbc.update(
        "UPDATE outbox_events SET status = 'FAILED', failure_kind = 'EXHAUSTED', attempts = 10"
            + " WHERE id = ?",
        event.getId());
    PULSEBOARD.stubFor(post(urlEqualTo(TRANSACTIONS)).willReturn(aResponse().withStatus(500)));

    postJson("/api/integration/events/" + event.getId() + "/retry", "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.failureKind").doesNotExist())
        .andExpect(jsonPath("$.attempts").value(10))
        .andExpect(jsonPath("$.attemptsAtRetry").value(10));

    Instant before = clock.instant();
    assertThat(worker.runOnce()).isEqualTo(1);

    OutboxEvent retried = reload(event);
    assertThat(retried.getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(retried.getAttempts()).isEqualTo(11);
    assertThat(retried.getNextAttemptAt())
        .isAfterOrEqualTo(before.plusSeconds(5))
        .isBeforeOrEqualTo(clock.instant().plusSeconds(10));
  }

  @Test
  void aPermanentFailureCanBeRetriedAfterTheCauseIsFixed() throws Exception {
    UUID orderId = payNewOrder();
    PULSEBOARD.stubFor(
        post(urlEqualTo(TRANSACTIONS))
            .willReturn(
                aResponse()
                    .withStatus(409)
                    .withBody(
                        "{\"message\":\"Email in use.\",\"code\":\"customer_email_conflict\"}")));
    worker.runOnce();
    OutboxEvent failed = onlyEventOf(orderId);
    assertThat(failed.getFailureKind()).isEqualTo(FailureKind.PERMANENT);
    assertThat(failed.getLastError()).contains("Vincule o cliente pelo ID externo");

    // Someone linked the customer in PulseBoard; the same payload now goes through.
    PULSEBOARD.resetAll();
    PULSEBOARD.stubFor(
        post(urlEqualTo(TRANSACTIONS)).willReturn(aResponse().withStatus(201).withBody("{}")));
    postJson("/api/integration/events/" + failed.getId() + "/retry", "").andExpect(status().isOk());
    worker.runOnce();

    assertThat(reload(failed).getStatus()).isEqualTo(OutboxStatus.SENT);
  }

  private UUID paidAndDeliveredThenRefunded() throws Exception {
    UUID orderId = payNewOrder();
    PULSEBOARD.stubFor(
        post(urlEqualTo(TRANSACTIONS)).willReturn(aResponse().withStatus(201).withBody("{}")));
    worker.runOnce();
    refund(orderId);
    return orderId;
  }

  private void stubInvalidTransition(UUID orderId) {
    PULSEBOARD.stubFor(
        post(urlEqualTo(statusChanges(orderId)))
            .willReturn(
                aResponse()
                    .withStatus(409)
                    .withBody(
                        "{\"message\":\"Cannot change status from refunded to refunded.\","
                            + "\"code\":\"invalid_transition\"}")));
  }
}
