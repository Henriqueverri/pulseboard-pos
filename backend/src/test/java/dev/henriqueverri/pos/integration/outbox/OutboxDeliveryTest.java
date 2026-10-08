package dev.henriqueverri.pos.integration.outbox;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import dev.henriqueverri.pos.integration.pulseboard.PulseBoardClient;
import dev.henriqueverri.pos.integration.pulseboard.PulseBoardProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Delivery, headers, body and idempotency against a simulated PulseBoard. */
class OutboxDeliveryTest extends OutboxIntegrationTest {

  @Autowired private PulseBoardClient client;
  @Autowired private PulseBoardProperties pulseBoard;

  @Test
  void deliversAPaidOrderAndMarksItSent() throws Exception {
    UUID orderId = payNewOrder();
    OutboxEvent event = onlyEventOf(orderId);
    UUID remoteId = UUID.randomUUID();
    PULSEBOARD.stubFor(
        post(urlEqualTo(TRANSACTIONS))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withHeader("X-Request-Id", event.requestId())
                    .withBody(transactionBody(remoteId, orderId, "paid"))));

    assertThat(worker.runOnce()).isEqualTo(1);

    OutboxEvent sent = reload(event);
    assertThat(sent.getStatus()).isEqualTo(OutboxStatus.SENT);
    assertThat(sent.getFailureKind()).isNull();
    assertThat(sent.getAttempts()).isEqualTo(1);
    assertThat(sent.getLastHttpStatus()).isEqualTo(201);
    assertThat(sent.getRemoteId()).isEqualTo(remoteId);
    assertThat(sent.getLastRequestId()).isEqualTo("pos-" + event.getId());
    assertThat(sent.getProcessedAt()).isNotNull();
    assertThat(sent.getLockedUntil()).isNull();
    assertThat(sent.getLastErrorCode()).isNull();

    // SENT is final: the next run has nothing to do.
    assertThat(worker.runOnce()).isZero();
    PULSEBOARD.verify(1, postRequestedFor(urlEqualTo(TRANSACTIONS)));
  }

  @Test
  void sendsTheStoredSnapshotWithTheContractHeaders() throws Exception {
    UUID orderId = payNewOrder();
    OutboxEvent event = onlyEventOf(orderId);
    PULSEBOARD.stubFor(
        post(urlEqualTo(TRANSACTIONS))
            .willReturn(aResponse().withStatus(201).withBody("{\"data\":{}}")));

    worker.runOnce();

    List<LoggedRequest> requests = PULSEBOARD.findAll(postRequestedFor(urlEqualTo(TRANSACTIONS)));
    assertThat(requests).hasSize(1);
    LoggedRequest request = requests.get(0);
    assertThat(request.getHeader("Authorization")).isEqualTo("Bearer " + pulseBoard.apiKey());
    assertThat(request.getHeader("Content-Type")).isEqualTo("application/json");
    assertThat(request.getHeader("Accept")).isEqualTo("application/json");
    assertThat(request.getHeader("X-Request-Id"))
        .isEqualTo("pos-" + event.getId())
        .hasSize(40)
        .matches("[A-Za-z0-9._-]{8,64}");
    assertThat(request.containsHeader("X-Organization-Id")).isFalse();
    assertThat(request.getBodyAsString()).isEqualTo(storedPayload(event.getId()));
    assertThat(objectMapper.readTree(request.getBodyAsString()).get("external_id").asText())
        .isEqualTo("pos:" + orderId);
  }

  /**
   * Worker A delivers, PulseBoard records the sale, and A dies before recording the result. When
   * the lease expires, worker B sends the very same bytes again; PulseBoard answers with an
   * idempotent replay and the sale exists once.
   */
  @Test
  void aRedeliveryAfterALostResultIsAnIdempotentReplay() throws Exception {
    UUID orderId = payNewOrder();
    OutboxEvent event = onlyEventOf(orderId);
    UUID remoteId = UUID.randomUUID();
    stubCreatedThenReplayed(orderId, remoteId);

    Instant now = clock.instant();
    List<ClaimedEvent> claimedByA = store.claim(now, now.minusSeconds(1), 20);
    assertThat(claimedByA).extracting(ClaimedEvent::id).containsExactly(event.getId());
    ClaimedEvent held = claimedByA.get(0);
    client.createTransaction(held.payload(), held.requestId());

    assertThat(worker.runOnce()).isEqualTo(1);

    OutboxEvent sent = reload(event);
    assertThat(sent.getStatus()).isEqualTo(OutboxStatus.SENT);
    assertThat(sent.getAttempts()).isEqualTo(2);
    assertThat(sent.getLastHttpStatus()).isEqualTo(200);
    assertThat(sent.getRemoteId()).isEqualTo(remoteId);
    assertSameRequestTwiceAndOneCreation();
  }

  /**
   * Timeout: the outcome is unknown; the backoff retry gets the replay and the sale exists once.
   */
  @Test
  void aTimeoutThenAReplayIsSentOnce() throws Exception {
    UUID orderId = payNewOrder();
    OutboxEvent event = onlyEventOf(orderId);
    UUID remoteId = UUID.randomUUID();
    PULSEBOARD.stubFor(
        post(urlEqualTo(TRANSACTIONS))
            .inScenario("idempotency")
            .whenScenarioStateIs(STARTED)
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withBody(transactionBody(remoteId, orderId, "paid"))
                    .withFixedDelay(2_000))
            .willSetStateTo("recorded"));
    PULSEBOARD.stubFor(
        post(urlEqualTo(TRANSACTIONS))
            .inScenario("idempotency")
            .whenScenarioStateIs("recorded")
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Idempotent-Replayed", "true")
                    .withBody(transactionBody(remoteId, orderId, "paid"))));

    worker.runOnce();
    OutboxEvent waiting = reload(event);
    assertThat(waiting.getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(waiting.getLastErrorCode()).isEqualTo("TIMEOUT");

    clock.advance(Duration.ofSeconds(11));
    assertThat(worker.runOnce()).isEqualTo(1);

    OutboxEvent sent = reload(event);
    assertThat(sent.getStatus()).isEqualTo(OutboxStatus.SENT);
    assertThat(sent.getAttempts()).isEqualTo(2);
    assertThat(sent.getLastErrorCode()).isNull();
    assertSameRequestTwiceAndOneCreation();
  }

  /** A worker whose claim was taken over cannot overwrite the new holder's outcome. */
  @Test
  void aStaleWorkerCannotRecordAResult() throws Exception {
    UUID orderId = payNewOrder();
    Instant now = clock.instant();
    ClaimedEvent stale = store.claim(now, now.minusSeconds(1), 20).get(0);
    ClaimedEvent current = store.claim(now, now.plusSeconds(120), 20).get(0);
    assertThat(current.id()).isEqualTo(stale.id());
    assertThat(current.attempts()).isEqualTo(stale.attempts() + 1);

    assertThat(
            store.markFailed(
                stale, now, FailureKind.PERMANENT, 500, null, "HTTP_500", "late result"))
        .isFalse();
    assertThat(store.reschedule(stale, now, now, 503, null, "HTTP_5XX", "late")).isFalse();
    assertThat(store.markSent(current, now, 201, null, null)).isTrue();
    assertThat(onlyEventOf(orderId).getStatus()).isEqualTo(OutboxStatus.SENT);
  }

  @Test
  void deliversARefundAsAStatusChangeOfTheExternalId() throws Exception {
    UUID orderId = payNewOrder();
    PULSEBOARD.stubFor(
        post(urlEqualTo(TRANSACTIONS)).willReturn(aResponse().withStatus(201).withBody("{}")));
    worker.runOnce();
    String refundedAt = refund(orderId);
    String statusChanges = statusChanges(orderId);
    PULSEBOARD.stubFor(
        post(urlEqualTo(statusChanges))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withBody(transactionBody(UUID.randomUUID(), orderId, "refunded"))));

    assertThat(worker.runOnce()).isEqualTo(1);

    List<OutboxEvent> written = events.findByAggregateIdOrderBySequence(orderId);
    assertThat(written)
        .extracting(OutboxEvent::getEventType, OutboxEvent::getStatus)
        .containsExactly(
            tuple(OutboxEventType.ORDER_PAID, OutboxStatus.SENT),
            tuple(OutboxEventType.ORDER_REFUNDED, OutboxStatus.SENT));
    PULSEBOARD.verify(
        1,
        postRequestedFor(urlEqualTo(statusChanges))
            .withHeader("X-Request-Id", equalTo("pos-" + written.get(1).getId())));
    LoggedRequest request = PULSEBOARD.findAll(postRequestedFor(urlEqualTo(statusChanges))).get(0);
    assertThat(request.getUrl()).isEqualTo(statusChanges);
    assertThat(objectMapper.readTree(request.getBodyAsString()))
        .isEqualTo(
            objectMapper.readTree(
                "{\"status\":\"refunded\",\"occurred_at\":\"" + refundedAt + "\"}"));
  }

  @Test
  void anUnknownSkuIsAPermanentFailureWithPulseBoardsMessage() throws Exception {
    UUID orderId = payNewOrder();
    PULSEBOARD.stubFor(
        post(urlEqualTo(TRANSACTIONS))
            .willReturn(
                aResponse()
                    .withStatus(422)
                    .withHeader("X-Request-Id", "pb-req-123")
                    .withBody(
                        "{\"message\":\"The selected product SKU is invalid.\","
                            + "\"code\":\"validation_failed\","
                            + "\"errors\":{\"items.0.sku\":[\"The selected product SKU is"
                            + " invalid.\"]}}")));

    worker.runOnce();

    OutboxEvent failed = onlyEventOf(orderId);
    assertThat(failed.getStatus()).isEqualTo(OutboxStatus.FAILED);
    assertThat(failed.getFailureKind()).isEqualTo(FailureKind.PERMANENT);
    assertThat(failed.getLastHttpStatus()).isEqualTo(422);
    assertThat(failed.getLastErrorCode()).isEqualTo("validation_failed");
    assertThat(failed.getLastError())
        .isEqualTo(
            "The selected product SKU is invalid. | items.0.sku: The selected product SKU is"
                + " invalid.");
    assertThat(failed.getLastRequestId()).isEqualTo("pb-req-123");
    assertThat(failed.getLockedUntil()).isNull();
    assertThat(failed.getProcessedAt()).isNull();

    // No retry: a permanent failure is never claimed again by the worker.
    clock.advance(Duration.ofHours(2));
    assertThat(worker.runOnce()).isZero();
    PULSEBOARD.verify(1, postRequestedFor(urlEqualTo(TRANSACTIONS)));
  }

  @Test
  void aTimeoutIsRetriedWithBackoff() throws Exception {
    UUID orderId = payNewOrder();
    PULSEBOARD.stubFor(
        post(urlEqualTo(TRANSACTIONS))
            .willReturn(aResponse().withStatus(201).withFixedDelay(2_000)));
    Instant before = clock.instant();

    worker.runOnce();

    OutboxEvent waiting = onlyEventOf(orderId);
    assertThat(waiting.getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(waiting.getFailureKind()).isNull();
    assertThat(waiting.getLastErrorCode()).isEqualTo("TIMEOUT");
    assertThat(waiting.getLastHttpStatus()).isNull();
    assertThat(waiting.getLastError()).doesNotContain(pulseBoard.apiKey());
    assertThat(waiting.getNextAttemptAt())
        .isAfterOrEqualTo(before.plusSeconds(5))
        .isBefore(clock.instant().plusSeconds(10));
  }

  @Test
  void aNetworkFailureIsRetriedWithBackoff() throws Exception {
    UUID orderId = payNewOrder();
    PULSEBOARD.stubFor(
        post(urlEqualTo(TRANSACTIONS))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    worker.runOnce();

    OutboxEvent waiting = onlyEventOf(orderId);
    assertThat(waiting.getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(waiting.getLastErrorCode()).isEqualTo("NETWORK");
    assertThat(waiting.getLastHttpStatus()).isNull();
    assertThat(waiting.getNextAttemptAt()).isAfter(waiting.getLastAttemptAt());
  }

  private void stubCreatedThenReplayed(UUID orderId, UUID remoteId) {
    PULSEBOARD.stubFor(
        post(urlEqualTo(TRANSACTIONS))
            .inScenario("idempotency")
            .whenScenarioStateIs(STARTED)
            .willReturn(
                aResponse().withStatus(201).withBody(transactionBody(remoteId, orderId, "paid")))
            .willSetStateTo("recorded"));
    PULSEBOARD.stubFor(
        post(urlEqualTo(TRANSACTIONS))
            .inScenario("idempotency")
            .whenScenarioStateIs("recorded")
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Idempotent-Replayed", "true")
                    .withBody(transactionBody(remoteId, orderId, "paid"))));
  }

  private void assertSameRequestTwiceAndOneCreation() {
    List<LoggedRequest> requests = PULSEBOARD.findAll(postRequestedFor(urlEqualTo(TRANSACTIONS)));
    assertThat(requests).hasSize(2);
    assertThat(requests.get(1).getBodyAsString()).isEqualTo(requests.get(0).getBodyAsString());
    assertThat(requests.get(1).getHeader("X-Request-Id"))
        .isEqualTo(requests.get(0).getHeader("X-Request-Id"));
    assertThat(PULSEBOARD.getAllServeEvents())
        .extracting(ServeEvent::getResponse)
        .filteredOn(response -> response.getStatus() == 201)
        .hasSize(1);
  }

  private String storedPayload(UUID eventId) {
    return jdbc.queryForObject(
        "SELECT payload::text FROM outbox_events WHERE id = ?", String.class, eventId);
  }
}
