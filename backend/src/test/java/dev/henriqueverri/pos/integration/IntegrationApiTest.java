package dev.henriqueverri.pos.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.henriqueverri.pos.integration.outbox.IntegrationPause;
import dev.henriqueverri.pos.integration.outbox.OutboxWorker;
import dev.henriqueverri.pos.integration.pulseboard.PulseBoardProperties;
import dev.henriqueverri.pos.security.Role;
import dev.henriqueverri.pos.support.ApiIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class IntegrationApiTest extends ApiIntegrationTest {

  private static final String TRANSACTIONS = "/api/v1/ingest/transactions";

  @Autowired private OutboxWorker worker;
  @Autowired private IntegrationPause pause;
  @Autowired private PulseBoardProperties pulseBoard;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void startFromAnEmptyOutbox() {
    PULSEBOARD.resetAll();
    pause.clear();
    jdbc.update("DELETE FROM outbox_events");
  }

  @Test
  void listsEventsNewestFirstAndFiltersByStatus() throws Exception {
    UUID sent = payNewOrder();
    PULSEBOARD.stubFor(
        post(urlEqualTo(TRANSACTIONS)).willReturn(aResponse().withStatus(201).withBody("{}")));
    worker.runOnce();
    UUID pending = payNewOrder();
    long pendingNumber = orderNumber(pending);

    getJson("/api/integration/events")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].orderId").value(pending.toString()))
        .andExpect(jsonPath("$.content[0].orderNumber").value(pendingNumber))
        .andExpect(jsonPath("$.content[0].externalId").value("pos:" + pending))
        .andExpect(jsonPath("$.content[0].eventType").value("ORDER_PAID"))
        .andExpect(jsonPath("$.content[0].status").value("PENDING"))
        .andExpect(jsonPath("$.content[0].nextAttemptAt").exists())
        .andExpect(jsonPath("$.content[0].payload").doesNotExist())
        .andExpect(jsonPath("$.content[1].orderId").value(sent.toString()))
        .andExpect(jsonPath("$.content[1].status").value("SENT"))
        .andExpect(jsonPath("$.content[1].lastHttpStatus").value(201));

    getJson("/api/integration/events?status=SENT&size=1")
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.size").value(1))
        .andExpect(jsonPath("$.content[0].orderId").value(sent.toString()));
    getJson("/api/integration/events?status=BOGUS").andExpect(status().isBadRequest());
  }

  @Test
  void detailIncludesTheExactPayload() throws Exception {
    UUID orderId = payNewOrder();
    String eventId = onlyEventId(orderId);

    JsonNode detail =
        json(getJson("/api/integration/events/" + eventId).andExpect(status().isOk()));

    assertThat(detail.get("requestId").asText()).isEqualTo("pos-" + eventId);
    assertThat(detail.get("payload").get("external_id").asText()).isEqualTo("pos:" + orderId);
    assertThat(detail.get("payload").get("status").asText()).isEqualTo("paid");
    getJson("/api/integration/events/" + UUID.randomUUID())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("not_found"));
  }

  @Test
  void onlyFailedEventsCanBeRetried() throws Exception {
    String eventId = onlyEventId(payNewOrder());

    postJson("/api/integration/events/" + eventId + "/retry", "")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("integration_event_not_failed"));
    postJson("/api/integration/events/" + UUID.randomUUID() + "/retry", "")
        .andExpect(status().isNotFound());
  }

  @Test
  void healthShowsStateWithoutTheSecret() throws Exception {
    payNewOrder();
    PULSEBOARD.stubFor(
        post(urlEqualTo(TRANSACTIONS)).willReturn(aResponse().withStatus(422).withBody("{}")));
    worker.runOnce();
    payNewOrder();

    getJson("/api/integration/health")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(true))
        .andExpect(jsonPath("$.pausedUntil").doesNotExist())
        .andExpect(jsonPath("$.targetUrl").value(PULSEBOARD.baseUrl() + "/api/v1"))
        .andExpect(jsonPath("$.keyPrefix").value("testprefix00"))
        .andExpect(jsonPath("$.pending").value(1))
        .andExpect(jsonPath("$.failed").value(1))
        .andExpect(content().string(not(containsString(pulseBoard.apiKey()))))
        .andExpect(content().string(not(containsString("faketestonlykey"))));
  }

  @Test
  void theIntegrationIsAdminOnly() throws Exception {
    String eventId = onlyEventId(payNewOrder());
    String cashier = bearer(tokenFor(Role.CASHIER));
    for (MockHttpServletRequestBuilder request :
        new MockHttpServletRequestBuilder[] {
          get("/api/integration/events"),
          get("/api/integration/events/" + eventId),
          MockMvcRequestBuilders.post("/api/integration/events/" + eventId + "/retry"),
          MockMvcRequestBuilders.post("/api/integration/events/retry-configuration-failures"),
          get("/api/integration/health")
        }) {
      mvc.perform(request.header(HttpHeaders.AUTHORIZATION, cashier))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("forbidden"));
    }
  }

  /** The order detail shows its deliveries; a waiting refund says which event holds it back. */
  @Test
  void orderDetailIncludesItsIntegrationEvents() throws Exception {
    UUID orderId = payNewOrder();
    postJson("/api/orders/" + orderId + "/refund", "").andExpect(status().isOk());

    JsonNode order = json(getJson("/api/orders/" + orderId).andExpect(status().isOk()));

    JsonNode integration = order.get("integrationEvents");
    assertThat(integration).hasSize(2);
    assertThat(integration.get(0).get("eventType").asText()).isEqualTo("ORDER_PAID");
    assertThat(integration.get(0).get("status").asText()).isEqualTo("PENDING");
    assertThat(integration.get(0).get("blockedBy").isNull()).isTrue();
    assertThat(integration.get(1).get("eventType").asText()).isEqualTo("ORDER_REFUNDED");
    assertThat(integration.get(1).get("blockedBy").asLong())
        .isEqualTo(integration.get(0).get("sequence").asLong());
    assertThat(integration.get(0).get("payload").isNull()).isTrue();

    // Cashiers see the order detail too, integration status included.
    mvc.perform(
            get("/api/orders/" + orderId)
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(Role.CASHIER))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.integrationEvents.length()").value(2));
  }

  private UUID payNewOrder() throws Exception {
    UUID orderId = UUID.fromString(createPendingOrder().get("id").asText());
    postJson("/api/orders/" + orderId + "/pay", "{\"paymentMethod\":\"PIX\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.integrationEvents[0].status").value("PENDING"));
    return orderId;
  }

  private String onlyEventId(UUID orderId) {
    return jdbc.queryForObject(
        "SELECT id::text FROM outbox_events WHERE aggregate_id = ?", String.class, orderId);
  }

  private long orderNumber(UUID orderId) {
    return jdbc.queryForObject("SELECT number FROM orders WHERE id = ?", Long.class, orderId);
  }
}
