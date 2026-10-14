package dev.henriqueverri.pos.integration.outbox;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import dev.henriqueverri.pos.integration.pulseboard.PulseBoardProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Stock mirrored in PulseBoard: every change of a tracked product's stock enqueues its absolute
 * balance ({@code STOCK_CHANGED}), delivered as {@code PUT /ingest/products/{sku}/stock}.
 */
class StockOutboxTest extends OutboxIntegrationTest {

  @Autowired private PulseBoardProperties pulseBoard;

  @Test
  void everyMovementEnqueuesTheAbsoluteBalance() throws Exception {
    String sku = unique("BEER");
    UUID beer = productId(createTrackedProduct(sku, 10, 2));
    UUID customer = createCustomer();
    String orderId =
        createOrder(customer, "[{\"productId\":\"" + beer + "\",\"quantity\":3}]")
            .get("id")
            .asText();
    postJson("/api/orders/" + orderId + "/pay", "{\"paymentMethod\":\"PIX\"}")
        .andExpect(status().isOk());
    postJson("/api/orders/" + orderId + "/refund", "").andExpect(status().isOk());
    stockMovement(beer, "{\"kind\":\"ADJUSTMENT\",\"quantity\":-4,\"reason\":\"Quebra\"}")
        .andExpect(status().isCreated());

    List<OutboxEvent> written = events.findByAggregateIdOrderBySequence(beer);
    // Created with stock control, received 10, sold 3, refunded 3, broke 4.
    assertThat(written)
        .extracting(OutboxEvent::getEventType)
        .containsOnly(OutboxEventType.STOCK_CHANGED);
    assertThat(written)
        .extracting(OutboxEvent::getAggregateType)
        .containsOnly(OutboxEvent.AGGREGATE_PRODUCT);
    List<JsonNode> payloads = payloads(written);
    assertThat(payloads)
        .extracting(p -> p.get("quantity").asInt())
        .containsExactly(0, 10, 7, 10, 6);
    assertThat(payloads).extracting(p -> p.get("sku").asText()).containsOnly(sku);
    assertThat(payloads).extracting(p -> p.get("tracked").asBoolean()).containsOnly(true);
    assertThat(payloads).extracting(p -> p.get("min_quantity").asInt()).containsOnly(2);
    List<Instant> instants =
        payloads.stream().map(p -> Instant.parse(p.get("occurred_at").asText())).toList();
    assertThat(instants).isSortedAccordingTo(Instant::compareTo).doesNotHaveDuplicates();
    assertThat(payloads.get(0).get("occurred_at").asText()).endsWith("Z");
  }

  @Test
  void aRefusedPaymentEnqueuesNoStock() throws Exception {
    UUID beer = productId(createTrackedProduct(unique("BEER"), 1, 0));
    int before = events.findByAggregateIdOrderBySequence(beer).size();
    String orderId =
        createOrder(createCustomer(), "[{\"productId\":\"" + beer + "\",\"quantity\":2}]")
            .get("id")
            .asText();

    postJson("/api/orders/" + orderId + "/pay", "{\"paymentMethod\":\"CASH\"}")
        .andExpect(status().isConflict());

    assertThat(events.findByAggregateIdOrderBySequence(beer)).hasSize(before);
  }

  @Test
  void stockSettingsChangesAreMirroredButOtherEditsAreNot() throws Exception {
    String sku = unique("WINE");
    UUID wine =
        UUID.fromString(
            json(postJson("/api/products", product(sku, "12.00", null, null))
                    .andExpect(status().isCreated()))
                .get("id")
                .asText());
    assertThat(events.findByAggregateIdOrderBySequence(wine))
        .as("a product without stock control has nothing to mirror")
        .isEmpty();

    putJson("/api/products/" + wine, product(sku, "12.00", true, 3)).andExpect(status().isOk());
    putJson("/api/products/" + wine, product(sku, "15.00", true, 3)).andExpect(status().isOk());
    putJson("/api/products/" + wine, product(sku, "15.00", true, 5)).andExpect(status().isOk());
    putJson("/api/products/" + wine, product(sku, "15.00", false, 5)).andExpect(status().isOk());

    List<JsonNode> payloads = payloads(events.findByAggregateIdOrderBySequence(wine));
    assertThat(payloads)
        .extracting(p -> p.get("tracked").asBoolean() + "/" + p.get("min_quantity").asInt())
        .as("turned on, minimum changed, turned off; the price change is not stock")
        .containsExactly("true/3", "true/5", "false/5");
  }

  @Test
  void deliversTheSnapshotAsAPutToTheProductStock() throws Exception {
    String sku = unique("BEER");
    UUID beer = productId(createTrackedProduct(sku, 0, 1));
    OutboxEvent event = onlyStockEventOf(beer);
    String path = stockPath(sku);
    PULSEBOARD.stubFor(
        put(urlEqualTo(path))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(stockBody(sku, 0, 1, "out"))));

    assertThat(worker.runOnce()).isEqualTo(1);

    OutboxEvent sent = reload(event);
    assertThat(sent.getStatus()).isEqualTo(OutboxStatus.SENT);
    assertThat(sent.getLastHttpStatus()).isEqualTo(200);
    List<LoggedRequest> requests = PULSEBOARD.findAll(putRequestedFor(urlEqualTo(path)));
    assertThat(requests).hasSize(1);
    LoggedRequest request = requests.get(0);
    assertThat(request.getHeader("Authorization")).isEqualTo("Bearer " + pulseBoard.apiKey());
    assertThat(request.getHeader("Content-Type")).isEqualTo("application/json");
    assertThat(request.getHeader("X-Request-Id")).isEqualTo("pos-" + event.getId());
    assertThat(request.getBodyAsString()).isEqualTo(storedPayload(event.getId()));
  }

  @Test
  void anOlderBalanceAnsweredAsAReplayIsSent() throws Exception {
    String sku = unique("BEER");
    UUID beer = productId(createTrackedProduct(sku, 0, 0));
    OutboxEvent event = onlyStockEventOf(beer);
    PULSEBOARD.stubFor(
        put(urlEqualTo(stockPath(sku)))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Idempotent-Replayed", "true")
                    .withBody(stockBody(sku, 7, 0, "ok"))));

    worker.runOnce();

    assertThat(reload(event).getStatus()).isEqualTo(OutboxStatus.SENT);
  }

  @Test
  void anUnknownSkuFailsWithAHintAndDoesNotHoldBackTheNextBalance() throws Exception {
    String sku = unique("GIN");
    UUID gin = productId(createTrackedProduct(sku, 0, 0));
    OutboxEvent first = onlyStockEventOf(gin);
    PULSEBOARD.stubFor(
        put(urlPathMatching("/api/v1/ingest/products/.*/stock"))
            .willReturn(
                aResponse()
                    .withStatus(422)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"message\":\"The product could not be resolved.\","
                            + "\"code\":\"validation_failed\","
                            + "\"errors\":{\"sku\":[\"The selected product SKU is invalid.\"]}}")));
    worker.runOnce();

    OutboxEvent failed = reload(first);
    assertThat(failed.getStatus()).isEqualTo(OutboxStatus.FAILED);
    assertThat(failed.getFailureKind()).isEqualTo(FailureKind.PERMANENT);
    assertThat(failed.getLastErrorCode()).isEqualTo("validation_failed");
    assertThat(failed.getLastError())
        .contains("sku: The selected product SKU is invalid.")
        .contains("Cadastre o produto com o mesmo SKU no PulseBoard");

    PULSEBOARD.resetAll();
    PULSEBOARD.stubFor(
        put(anyUrl()).willReturn(aResponse().withStatus(200).withBody(stockBody(sku, 5, 0, "ok"))));
    stockMovement(gin, "{\"kind\":\"RECEIPT\",\"quantity\":5}").andExpect(status().isCreated());

    assertThat(worker.runOnce()).as("the newer balance is not blocked").isEqualTo(1);
    List<OutboxEvent> written = events.findByAggregateIdOrderBySequence(gin);
    assertThat(written)
        .extracting(OutboxEvent::getStatus)
        .containsExactly(OutboxStatus.FAILED, OutboxStatus.SENT);

    getJson("/api/integration/events?status=FAILED")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(first.getId().toString()))
        .andExpect(jsonPath("$.content[0].eventType").value("STOCK_CHANGED"))
        .andExpect(jsonPath("$.content[0].aggregateType").value("PRODUCT"))
        .andExpect(jsonPath("$.content[0].productId").value(gin.toString()))
        .andExpect(jsonPath("$.content[0].productSku").value(sku))
        .andExpect(jsonPath("$.content[0].productName").value("Cerveja " + sku))
        .andExpect(jsonPath("$.content[0].externalId").value(sku))
        .andExpect(jsonPath("$.content[0].orderId").isEmpty())
        .andExpect(jsonPath("$.content[0].orderNumber").isEmpty())
        .andExpect(jsonPath("$.content[0].blockedBy").isEmpty());
    getJson("/api/integration/events/" + first.getId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payload.sku").value(sku))
        .andExpect(jsonPath("$.payload.quantity").value(0));
  }

  private OutboxEvent onlyStockEventOf(UUID productId) {
    List<OutboxEvent> written = events.findByAggregateIdOrderBySequence(productId);
    assertThat(written).hasSize(1);
    return written.get(0);
  }

  private List<JsonNode> payloads(List<OutboxEvent> written) {
    return written.stream().map(e -> readTree(storedPayload(e.getId()))).toList();
  }

  private JsonNode readTree(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String storedPayload(UUID eventId) {
    return jdbc.queryForObject(
        "SELECT payload::text FROM outbox_events WHERE id = ?", String.class, eventId);
  }

  private static UUID productId(JsonNode product) {
    return UUID.fromString(product.get("id").asText());
  }

  private static String product(String sku, String price, Boolean trackStock, Integer stockMin) {
    return "{\"sku\":\"%s\",\"name\":\"Vinho %s\",\"price\":\"%s\",\"active\":true,\"trackStock\":%s,\"stockMin\":%s}"
        .formatted(sku, sku, price, trackStock, stockMin);
  }

  private static String stockPath(String sku) {
    return "/api/v1/ingest/products/" + sku + "/stock";
  }

  private static String stockBody(String sku, int quantity, int min, String status) {
    return ("{\"data\":{\"product_id\":\"%s\",\"sku\":\"%s\",\"tracked\":true,\"quantity\":%d,"
            + "\"min_quantity\":%d,\"status\":\"%s\",\"updated_at\":\"2026-10-13T15:00:00.000000Z\"}}")
        .formatted(UUID.randomUUID(), sku, quantity, min, status);
  }
}
