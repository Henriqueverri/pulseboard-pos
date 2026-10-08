package dev.henriqueverri.pos.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.henriqueverri.pos.support.ApiIntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class OrderApiTest extends ApiIntegrationTest {

  @Autowired private JdbcTemplate jdbc;

  @Test
  void createsPendingOrderWithServerComputedTotalsAsStrings() throws Exception {
    UUID customer = createCustomer();
    UUID mouse = seededProductId("PB-001-ESS");
    UUID keyboard = seededProductId("PB-002-ESS");

    JsonNode order =
        createOrder(
            customer,
            "[{\"productId\":\""
                + mouse
                + "\",\"quantity\":2},{\"productId\":\""
                + keyboard
                + "\",\"quantity\":1}]");

    assertThat(order.get("status").asText()).isEqualTo("PENDING");
    assertThat(order.get("number").asLong()).isPositive();
    assertThat(order.get("currency").asText()).isEqualTo("BRL");
    assertThat(order.get("customer").get("id").asText()).isEqualTo(customer.toString());
    assertThat(order.get("paymentMethod").isNull()).isTrue();
    assertThat(order.get("paidAt").isNull()).isTrue();
    // Same numbers as the PulseBoard integration contract example: 2 x 79.90 + 249.90.
    assertThat(order.get("total").isTextual()).isTrue();
    assertThat(order.get("total").asText()).isEqualTo("409.70");
    JsonNode items = order.get("items");
    assertThat(items).hasSize(2);
    JsonNode mouseLine = items.get(0);
    assertThat(mouseLine.get("sku").asText()).isEqualTo("PB-001-ESS");
    assertThat(mouseLine.get("productName").asText()).isEqualTo("Mouse sem fio Essential");
    assertThat(mouseLine.get("unitPrice").asText()).isEqualTo("79.90");
    assertThat(mouseLine.get("lineTotal").asText()).isEqualTo("159.80");
    assertThat(items.get(1).get("lineTotal").asText()).isEqualTo("249.90");

    getJson("/api/orders/" + order.get("id").asText())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value("409.70"))
        .andExpect(jsonPath("$.items.length()").value(2));
  }

  @Test
  void ignoresPricesAndTotalsSentByTheClient() throws Exception {
    UUID mouse = seededProductId("PB-001-ESS");
    String body =
        "{\"customerId\":\""
            + createCustomer()
            + "\",\"total\":\"0.01\",\"subtotal\":\"0.01\",\"items\":[{\"productId\":\""
            + mouse
            + "\",\"quantity\":3,\"unitPrice\":\"0.01\"}]}";

    postJson("/api/orders", body)
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andExpect(jsonPath("$.items[0].unitPrice").value("79.90"))
        .andExpect(jsonPath("$.total").value("239.70"));
  }

  @Test
  void fullLifecyclePayThenRefund() throws Exception {
    String id = createPendingOrder().get("id").asText();

    JsonNode paid =
        json(
            postJson("/api/orders/" + id + "/pay", "{\"paymentMethod\":\"PIX\"}")
                .andExpect(status().isOk()));
    assertThat(paid.get("status").asText()).isEqualTo("PAID");
    assertThat(paid.get("paymentMethod").asText()).isEqualTo("PIX");
    Instant paidAt = Instant.parse(paid.get("paidAt").asText());
    assertThat(paidAt.getNano()).isZero();

    JsonNode refunded =
        json(postJson("/api/orders/" + id + "/refund", "").andExpect(status().isOk()));
    assertThat(refunded.get("status").asText()).isEqualTo("REFUNDED");
    assertThat(Instant.parse(refunded.get("refundedAt").asText())).isAfterOrEqualTo(paidAt);
    assertThat(refunded.get("paidAt").asText()).isEqualTo(paid.get("paidAt").asText());
  }

  @Test
  void cancelsPendingOrder() throws Exception {
    String id = createPendingOrder().get("id").asText();

    postJson("/api/orders/" + id + "/cancel", "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELED"))
        .andExpect(jsonPath("$.canceledAt").isNotEmpty());
  }

  @Test
  void invalidTransitionsReturn409ProblemDetail() throws Exception {
    String canceled = createPendingOrder().get("id").asText();
    postJson("/api/orders/" + canceled + "/cancel", "").andExpect(status().isOk());

    expectInvalidTransition(canceled, "pay");
    expectInvalidTransition(canceled, "refund");
    expectInvalidTransition(canceled, "cancel");

    String pending = createPendingOrder().get("id").asText();
    expectInvalidTransition(pending, "refund");

    String refunded = createPendingOrder().get("id").asText();
    postJson("/api/orders/" + refunded + "/pay", "{\"paymentMethod\":\"CASH\"}")
        .andExpect(status().isOk());
    expectInvalidTransition(refunded, "pay");
    expectInvalidTransition(refunded, "cancel");
    postJson("/api/orders/" + refunded + "/refund", "").andExpect(status().isOk());
    expectInvalidTransition(refunded, "pay");
    expectInvalidTransition(refunded, "cancel");
    expectInvalidTransition(refunded, "refund");

    getJson("/api/orders/" + refunded).andExpect(jsonPath("$.status").value("REFUNDED"));
  }

  private void expectInvalidTransition(String id, String action) throws Exception {
    String body = action.equals("pay") ? "{\"paymentMethod\":\"CARD\"}" : "";
    postJson("/api/orders/" + id + "/" + action, body)
        .andExpect(status().isConflict())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.code").value("invalid_order_transition"))
        .andExpect(jsonPath("$.detail").isNotEmpty())
        .andExpect(jsonPath("$.trace").doesNotExist());
  }

  @Test
  void unknownOrderReturns404() throws Exception {
    String path = "/api/orders/" + UUID.randomUUID();
    getJson(path).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("not_found"));
    postJson(path + "/cancel", "").andExpect(status().isNotFound());
  }

  @Test
  void malformedIdReturns400() throws Exception {
    getJson("/api/orders/not-a-uuid")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation_failed"))
        .andExpect(jsonPath("$.errors.id").isArray());
  }

  @Test
  void validatesCreateRequest() throws Exception {
    postJson("/api/orders", "{\"items\":[]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation_failed"))
        .andExpect(jsonPath("$.errors", hasKey("customerId")))
        .andExpect(jsonPath("$.errors", hasKey("items")));

    UUID mouse = seededProductId("PB-001-ESS");
    postJson(
            "/api/orders",
            "{\"customerId\":\""
                + createCustomer()
                + "\",\"items\":[{\"productId\":\""
                + mouse
                + "\",\"quantity\":0}]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors", hasKey("items[0].quantity")));
  }

  @Test
  void rejectsInvalidPaymentMethod() throws Exception {
    String id = createPendingOrder().get("id").asText();

    postJson("/api/orders/" + id + "/pay", "{\"paymentMethod\":\"BITCOIN\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation_failed"))
        .andExpect(jsonPath("$.errors", hasKey("paymentMethod")));
    postJson("/api/orders/" + id + "/pay", "{}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors", hasKey("paymentMethod")));
    postJson("/api/orders/" + id + "/pay", "{not json")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("malformed_request"));
  }

  @Test
  void rejectsUnknownCustomerUnknownProductDuplicatesAndInactiveProducts() throws Exception {
    UUID customer = createCustomer();
    UUID mouse = seededProductId("PB-001-ESS");

    postJson(
            "/api/orders",
            "{\"customerId\":\""
                + UUID.randomUUID()
                + "\",\"items\":[{\"productId\":\""
                + mouse
                + "\",\"quantity\":1}]}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("unknown_customer"));

    postJson(
            "/api/orders",
            "{\"customerId\":\""
                + customer
                + "\",\"items\":[{\"productId\":\""
                + UUID.randomUUID()
                + "\",\"quantity\":1}]}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("unknown_product"));

    postJson(
            "/api/orders",
            "{\"customerId\":\""
                + customer
                + "\",\"items\":[{\"productId\":\""
                + mouse
                + "\",\"quantity\":1},{\"productId\":\""
                + mouse
                + "\",\"quantity\":2}]}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("duplicate_order_item"));

    String inactiveSku = unique("INATIVO");
    String inactive =
        json(postJson(
                    "/api/products",
                    "{\"sku\":\""
                        + inactiveSku
                        + "\",\"name\":\"Produto inativo\",\"price\":\"10.00\",\"active\":false}")
                .andExpect(status().isCreated()))
            .get("id")
            .asText();
    postJson(
            "/api/orders",
            "{\"customerId\":\""
                + customer
                + "\",\"items\":[{\"productId\":\""
                + inactive
                + "\",\"quantity\":1}]}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("product_inactive"));
  }

  @Test
  void rejectsTotalAboveColumnLimit() throws Exception {
    String expensive =
        json(postJson(
                    "/api/products",
                    "{\"sku\":\""
                        + unique("CARO")
                        + "\",\"name\":\"Produto caro\",\"price\":\"9999999999.99\",\"active\":true}")
                .andExpect(status().isCreated()))
            .get("id")
            .asText();

    postJson(
            "/api/orders",
            "{\"customerId\":\""
                + createCustomer()
                + "\",\"items\":[{\"productId\":\""
                + expensive
                + "\",\"quantity\":2}]}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("order_total_too_large"));
  }

  @Test
  void listsOrdersPaginatedWithFilters() throws Exception {
    JsonNode first = createPendingOrder();
    JsonNode second = createPendingOrder();
    String paidId = second.get("id").asText();
    postJson("/api/orders/" + paidId + "/pay", "{\"paymentMethod\":\"CARD\"}")
        .andExpect(status().isOk());

    getJson("/api/orders?number=" + first.get("number").asLong())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(first.get("id").asText()))
        .andExpect(jsonPath("$.content[0].customer.name").value("Ana Lima"))
        .andExpect(jsonPath("$.content[0].total").value("79.90"))
        .andExpect(jsonPath("$.content[0].items").doesNotExist());

    JsonNode paidPage = json(getJson("/api/orders?status=PAID&size=100"));
    assertThat(paidPage.get("content"))
        .allMatch(order -> order.get("status").asText().equals("PAID"))
        .anyMatch(order -> order.get("id").asText().equals(paidId));

    JsonNode pageOne = json(getJson("/api/orders?page=0&size=1").andExpect(status().isOk()));
    assertThat(pageOne.get("content")).hasSize(1);
    assertThat(pageOne.get("page").asInt()).isZero();
    assertThat(pageOne.get("size").asInt()).isEqualTo(1);
    assertThat(pageOne.get("totalElements").asLong()).isGreaterThanOrEqualTo(2);
    assertThat(pageOne.get("totalPages").asInt()).isEqualTo(pageOne.get("totalElements").asInt());
    JsonNode pageTwo = json(getJson("/api/orders?page=1&size=1"));
    // Newest first: the page boundary never repeats an order.
    assertThat(pageOne.get("content").get(0).get("number").asLong())
        .isGreaterThan(pageTwo.get("content").get(0).get("number").asLong());

    String future = Instant.now().plusSeconds(3600).toString();
    getJson("/api/orders?from=" + future)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    String past = Instant.now().minusSeconds(3600).toString();
    getJson("/api/orders?from=" + past + "&to=" + future + "&number=" + first.get("number"))
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void validatesPaginationParameters() throws Exception {
    getJson("/api/orders?size=101")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation_failed"))
        .andExpect(jsonPath("$.errors", hasKey("size")));
    getJson("/api/orders?page=-1").andExpect(status().isBadRequest());
    getJson("/api/orders?status=UNKNOWN")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors", hasKey("status")));
  }

  @Test
  void persistsSnapshotsAndKeepsThemWhenTheProductChanges() throws Exception {
    String sku = unique("SNAP");
    String productId =
        json(postJson(
                    "/api/products",
                    "{\"sku\":\""
                        + sku
                        + "\",\"name\":\"Original\",\"price\":\"10.00\",\"active\":true}")
                .andExpect(status().isCreated()))
            .get("id")
            .asText();
    JsonNode order =
        createOrder(createCustomer(), "[{\"productId\":\"" + productId + "\",\"quantity\":3}]");

    putJson(
            "/api/products/" + productId,
            "{\"sku\":\""
                + sku
                + "-NEW\",\"name\":\"Renomeado\",\"price\":\"99.00\",\"active\":true}")
        .andExpect(status().isOk());

    getJson("/api/orders/" + order.get("id").asText())
        .andExpect(jsonPath("$.items[0].sku").value(sku))
        .andExpect(jsonPath("$.items[0].productName").value("Original"))
        .andExpect(jsonPath("$.items[0].unitPrice").value("10.00"))
        .andExpect(jsonPath("$.total").value("30.00"));
    String stored =
        jdbc.queryForObject(
            "SELECT total::text FROM orders WHERE id = ?::uuid",
            String.class,
            order.get("id").asText());
    assertThat(stored).isEqualTo("30.00");
  }
}
