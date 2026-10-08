package dev.henriqueverri.pos.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.henriqueverri.pos.support.ApiIntegrationTest;
import dev.henriqueverri.pos.support.MutableClock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The worker against a simulated PulseBoard (WireMock) and the real outbox table. Each test starts
 * with an empty outbox, no stubs, no pause and the real time.
 */
abstract class OutboxIntegrationTest extends ApiIntegrationTest {

  static final String TRANSACTIONS = "/api/v1/ingest/transactions";

  @Autowired OutboxWorker worker;
  @Autowired OutboxStore store;
  @Autowired OutboxRepository events;
  @Autowired IntegrationPause pause;
  @Autowired MutableClock clock;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void startFromAnEmptyOutbox() {
    PULSEBOARD.resetAll();
    pause.clear();
    clock.reset();
    // Other test classes leave PENDING events behind; these classes own the outbox while they run.
    jdbc.update("DELETE FROM outbox_events");
  }

  @AfterEach
  void restoreSharedState() {
    pause.clear();
    clock.reset();
  }

  UUID payNewOrder() throws Exception {
    UUID orderId = UUID.fromString(createPendingOrder().get("id").asText());
    postJson("/api/orders/" + orderId + "/pay", "{\"paymentMethod\":\"CARD\"}")
        .andExpect(status().isOk());
    return orderId;
  }

  String refund(UUID orderId) throws Exception {
    return json(postJson("/api/orders/" + orderId + "/refund", "").andExpect(status().isOk()))
        .get("refundedAt")
        .asText();
  }

  OutboxEvent onlyEventOf(UUID orderId) {
    List<OutboxEvent> written = events.findByAggregateIdOrderBySequence(orderId);
    assertThat(written).hasSize(1);
    return written.get(0);
  }

  OutboxEvent reload(OutboxEvent event) {
    return events.findById(event.getId()).orElseThrow();
  }

  static String statusChanges(UUID orderId) {
    return TRANSACTIONS + "/pos:" + orderId + "/status-changes";
  }

  static String transactionBody(UUID id, UUID orderId, String status) {
    return "{\"data\":{\"id\":\"%s\",\"external_id\":\"pos:%s\",\"status\":\"%s\"}}"
        .formatted(id, orderId, status);
  }
}
