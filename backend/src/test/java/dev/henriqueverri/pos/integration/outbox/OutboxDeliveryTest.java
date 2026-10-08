package dev.henriqueverri.pos.integration.outbox;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import dev.henriqueverri.pos.integration.pulseboard.PulseBoardClient;
import dev.henriqueverri.pos.integration.pulseboard.PulseBoardProperties;
import dev.henriqueverri.pos.support.ApiIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** The worker against a simulated PulseBoard (WireMock) and the real outbox table. */
class OutboxDeliveryTest extends ApiIntegrationTest {

  private static final String TRANSACTIONS = "/api/v1/ingest/transactions";

  @Autowired private OutboxWorker worker;
  @Autowired private OutboxStore store;
  @Autowired private OutboxRepository events;
  @Autowired private PulseBoardClient client;
  @Autowired private PulseBoardProperties pulseBoard;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void startFromAnEmptyOutbox() {
    PULSEBOARD.resetAll();
    // Other test classes leave PENDING events behind; this class owns the outbox while it runs.
    jdbc.update("DELETE FROM outbox_events");
  }

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

    OutboxEvent sent = events.findById(event.getId()).orElseThrow();
    assertThat(sent.getStatus()).isEqualTo(OutboxStatus.SENT);
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

    Instant now = Instant.now();
    List<ClaimedEvent> claimedByA = store.claim(now, now.minusSeconds(1), 20);
    assertThat(claimedByA).extracting(ClaimedEvent::id).containsExactly(event.getId());
    ClaimedEvent held = claimedByA.get(0);
    client.createTransaction(held.payload(), held.requestId());

    assertThat(worker.runOnce()).isEqualTo(1);

    OutboxEvent sent = events.findById(event.getId()).orElseThrow();
    assertThat(sent.getStatus()).isEqualTo(OutboxStatus.SENT);
    assertThat(sent.getAttempts()).isEqualTo(2);
    assertThat(sent.getLastHttpStatus()).isEqualTo(200);
    assertThat(sent.getRemoteId()).isEqualTo(remoteId);

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

  /** A worker whose claim was taken over cannot overwrite the new holder's outcome. */
  @Test
  void aStaleWorkerCannotRecordAResult() throws Exception {
    UUID orderId = payNewOrder();
    Instant now = Instant.now();
    ClaimedEvent stale = store.claim(now, now.minusSeconds(1), 20).get(0);
    ClaimedEvent current = store.claim(now, now.plusSeconds(120), 20).get(0);
    assertThat(current.id()).isEqualTo(stale.id());
    assertThat(current.attempts()).isEqualTo(stale.attempts() + 1);

    assertThat(store.markFailed(stale, now, 500, null, "HTTP_500", "late result")).isFalse();
    assertThat(store.markSent(current, now, 201, null, null)).isTrue();
    assertThat(onlyEventOf(orderId).getStatus()).isEqualTo(OutboxStatus.SENT);
  }

  @Test
  void deliversARefundAsAStatusChangeOfTheExternalId() throws Exception {
    UUID orderId = payNewOrder();
    PULSEBOARD.stubFor(
        post(urlEqualTo(TRANSACTIONS)).willReturn(aResponse().withStatus(201).withBody("{}")));
    worker.runOnce();
    String refundedAt =
        json(postJson("/api/orders/" + orderId + "/refund", "").andExpect(status().isOk()))
            .get("refundedAt")
            .asText();
    String statusChanges = TRANSACTIONS + "/pos:" + orderId + "/status-changes";
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

  /** Classifying errors is the next phase; for now any other answer is a recorded failure. */
  @Test
  void recordsARejectionAsFailedWithPulseBoardsError() throws Exception {
    UUID orderId = payNewOrder();
    PULSEBOARD.stubFor(
        post(urlEqualTo(TRANSACTIONS))
            .willReturn(
                aResponse()
                    .withStatus(422)
                    .withHeader("X-Request-Id", "pb-req-123")
                    .withBody(
                        "{\"message\":\"The given data was invalid.\",\"code\":\"validation_failed\","
                            + "\"errors\":{\"items.0.sku\":[\"The selected product SKU is"
                            + " invalid.\"]}}")));

    worker.runOnce();

    OutboxEvent failed = onlyEventOf(orderId);
    assertThat(failed.getStatus()).isEqualTo(OutboxStatus.FAILED);
    assertThat(failed.getLastHttpStatus()).isEqualTo(422);
    assertThat(failed.getLastErrorCode()).isEqualTo("validation_failed");
    assertThat(failed.getLastError())
        .startsWith("The given data was invalid.")
        .contains("items.0.sku");
    assertThat(failed.getLastRequestId()).isEqualTo("pb-req-123");
    assertThat(failed.getLockedUntil()).isNull();
    assertThat(failed.getProcessedAt()).isNull();
  }

  @Test
  void recordsATimeoutWithoutAnHttpStatus() throws Exception {
    UUID orderId = payNewOrder();
    PULSEBOARD.stubFor(
        post(urlEqualTo(TRANSACTIONS))
            .willReturn(aResponse().withStatus(201).withFixedDelay(2_000)));

    worker.runOnce();

    OutboxEvent failed = onlyEventOf(orderId);
    assertThat(failed.getStatus()).isEqualTo(OutboxStatus.FAILED);
    assertThat(failed.getLastErrorCode()).isEqualTo("TIMEOUT");
    assertThat(failed.getLastHttpStatus()).isNull();
    assertThat(failed.getLastError()).doesNotContain(pulseBoard.apiKey());
  }

  @Test
  void recordsANetworkFailure() throws Exception {
    UUID orderId = payNewOrder();
    PULSEBOARD.stubFor(
        post(urlEqualTo(TRANSACTIONS))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    worker.runOnce();

    OutboxEvent failed = onlyEventOf(orderId);
    assertThat(failed.getStatus()).isEqualTo(OutboxStatus.FAILED);
    assertThat(failed.getLastErrorCode()).isEqualTo("NETWORK");
    assertThat(failed.getLastHttpStatus()).isNull();
  }

  private UUID payNewOrder() throws Exception {
    UUID orderId = UUID.fromString(createPendingOrder().get("id").asText());
    postJson("/api/orders/" + orderId + "/pay", "{\"paymentMethod\":\"CARD\"}")
        .andExpect(status().isOk());
    return orderId;
  }

  private OutboxEvent onlyEventOf(UUID orderId) {
    List<OutboxEvent> written = events.findByAggregateIdOrderBySequence(orderId);
    assertThat(written).hasSize(1);
    return written.get(0);
  }

  private String storedPayload(UUID eventId) {
    return jdbc.queryForObject(
        "SELECT payload::text FROM outbox_events WHERE id = ?", String.class, eventId);
  }

  private static String transactionBody(UUID id, UUID orderId, String status) {
    return "{\"data\":{\"id\":\"%s\",\"external_id\":\"pos:%s\",\"status\":\"%s\"}}"
        .formatted(id, orderId, status);
  }
}
