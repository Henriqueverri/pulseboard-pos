package dev.henriqueverri.pos.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.henriqueverri.pos.integration.outbox.OutboxRepository;
import dev.henriqueverri.pos.support.ApiIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Stock is taken when an order is paid and given back when it is refunded; nothing else moves it.
 */
class StockLifecycleTest extends ApiIntegrationTest {

  @Autowired private OutboxRepository events;

  @Test
  void payingTakesTheStockAndRefundingGivesItBack() throws Exception {
    UUID beer = productId(createTrackedProduct(unique("BEER"), 10, 2));
    UUID untracked = seededProductId("PB-005-ESS");
    String orderId = order(item(beer, 3) + "," + item(untracked, 1)).get("id").asText();

    assertThat(stockOf(beer)).as("a PENDING order reserves nothing").isEqualTo(10);

    postJson("/api/orders/" + orderId + "/pay", "{\"paymentMethod\":\"PIX\"}")
        .andExpect(status().isOk());
    assertThat(stockOf(beer)).isEqualTo(7);
    getJson("/api/products/" + beer + "/stock-movements")
        .andExpect(jsonPath("$.content[0].kind").value("SALE"))
        .andExpect(jsonPath("$.content[0].quantity").value(-3))
        .andExpect(jsonPath("$.content[0].balanceAfter").value(7))
        .andExpect(jsonPath("$.content[0].orderId").value(orderId))
        .andExpect(jsonPath("$.content[0].orderNumber").isNumber())
        .andExpect(jsonPath("$.content[0].userName").isNotEmpty());
    getJson("/api/products/" + untracked + "/stock-movements")
        .andExpect(jsonPath("$.totalElements").value(0));

    postJson("/api/orders/" + orderId + "/refund", "").andExpect(status().isOk());
    assertThat(stockOf(beer)).isEqualTo(10);
    getJson("/api/products/" + beer + "/stock-movements")
        .andExpect(jsonPath("$.content[0].kind").value("REFUND"))
        .andExpect(jsonPath("$.content[0].quantity").value(3))
        .andExpect(jsonPath("$.content[0].balanceAfter").value(10));
  }

  @Test
  void cancelingAPendingOrderDoesNotTouchTheStock() throws Exception {
    UUID beer = productId(createTrackedProduct(unique("BEER"), 4, 0));
    String orderId = order(item(beer, 2)).get("id").asText();

    postJson("/api/orders/" + orderId + "/cancel", "").andExpect(status().isOk());

    assertThat(stockOf(beer)).isEqualTo(4);
    getJson("/api/products/" + beer + "/stock-movements")
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void shortOfStockThePaymentIsRefusedAndNothingChanges() throws Exception {
    JsonNode lager = createTrackedProduct(unique("LAGER"), 5, 0);
    JsonNode ipa = createTrackedProduct(unique("IPA"), 1, 0);
    JsonNode stout = createTrackedProduct(unique("STOUT"), 0, 0);
    UUID lagerId = productId(lager);
    String orderId =
        order(item(lagerId, 2) + "," + item(productId(ipa), 2) + "," + item(productId(stout), 1))
            .get("id")
            .asText();

    postJson("/api/orders/" + orderId + "/pay", "{\"paymentMethod\":\"CASH\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("insufficient_stock"))
        .andExpect(jsonPath("$.detail").value("Saldo insuficiente para 2 produtos."))
        .andExpect(jsonPath("$.errors.length()").value(2))
        .andExpect(
            jsonPath("$.errors['" + ipa.get("sku").asText() + "'][0]").value("Resta 1 unidade."))
        .andExpect(
            jsonPath("$.errors['" + stout.get("sku").asText() + "'][0]")
                .value("Restam 0 unidades."));

    getJson("/api/orders/" + orderId)
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.paymentMethod").isEmpty());
    assertThat(stockOf(lagerId)).as("the lines already taken are rolled back").isEqualTo(5);
    getJson("/api/products/" + lagerId + "/stock-movements")
        .andExpect(jsonPath("$.totalElements").value(1));
    assertThat(events.findByAggregateIdOrderBySequence(UUID.fromString(orderId))).isEmpty();
  }

  @Test
  void aRefusedOrderCanBePaidAfterARestock() throws Exception {
    UUID ipa = productId(createTrackedProduct(unique("IPA"), 1, 0));
    String orderId = order(item(ipa, 2)).get("id").asText();
    postJson("/api/orders/" + orderId + "/pay", "{\"paymentMethod\":\"CARD\"}")
        .andExpect(status().isConflict());

    stockMovement(ipa, "{\"kind\":\"RECEIPT\",\"quantity\":6}").andExpect(status().isCreated());

    postJson("/api/orders/" + orderId + "/pay", "{\"paymentMethod\":\"CARD\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PAID"));
    assertThat(stockOf(ipa)).isEqualTo(5);
  }

  @Test
  void aRefundOnlyGivesBackWhatTheSaleTook() throws Exception {
    JsonNode created = createTrackedProduct(unique("LATE"), 0, 0);
    UUID product = productId(created);
    String sku = created.get("sku").asText();
    putJson(
            "/api/products/" + product,
            "{\"sku\":\""
                + sku
                + "\",\"name\":\"Late\",\"price\":\"3.00\",\"active\":true,"
                + "\"trackStock\":false}")
        .andExpect(status().isOk());
    String orderId = order(item(product, 2)).get("id").asText();
    postJson("/api/orders/" + orderId + "/pay", "{\"paymentMethod\":\"PIX\"}")
        .andExpect(status().isOk());

    putJson(
            "/api/products/" + product,
            "{\"sku\":\""
                + sku
                + "\",\"name\":\"Late\",\"price\":\"3.00\",\"active\":true,"
                + "\"trackStock\":true}")
        .andExpect(status().isOk());
    postJson("/api/orders/" + orderId + "/refund", "").andExpect(status().isOk());

    assertThat(stockOf(product)).isZero();
    getJson("/api/products/" + product + "/stock-movements")
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  private JsonNode order(String items) throws Exception {
    return createOrder(createCustomer(), "[" + items + "]");
  }

  private static String item(UUID product, int quantity) {
    return "{\"productId\":\"" + product + "\",\"quantity\":" + quantity + "}";
  }

  private static UUID productId(JsonNode product) {
    return UUID.fromString(product.get("id").asText());
  }
}
