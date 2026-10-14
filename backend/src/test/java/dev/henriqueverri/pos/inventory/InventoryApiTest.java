package dev.henriqueverri.pos.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.henriqueverri.pos.support.ApiIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InventoryApiTest extends ApiIntegrationTest {

  @Test
  void newProductsHaveNoStockControlUnlessAsked() throws Exception {
    JsonNode plain =
        json(
            postJson(
                    "/api/products",
                    "{\"sku\":\""
                        + unique("PLAIN")
                        + "\",\"name\":\"Sem estoque\",\"price\":\"5.00\",\"active\":true}")
                .andExpect(status().isCreated()));
    assertThat(plain.get("trackStock").asBoolean()).isFalse();
    assertThat(plain.get("stockQuantity").asInt()).isZero();
    assertThat(plain.get("stockMin").asInt()).isZero();
    assertThat(plain.get("lowStock").asBoolean()).isFalse();

    JsonNode tracked = createTrackedProduct(0, 3);
    assertThat(tracked.get("trackStock").asBoolean()).isTrue();
    assertThat(tracked.get("stockMin").asInt()).isEqualTo(3);
    assertThat(tracked.get("lowStock").asBoolean()).isTrue();
  }

  @Test
  void receiptsAndAdjustmentsMoveTheBalanceAndLeaveAHistory() throws Exception {
    UUID product = id(createTrackedProduct(0, 5));

    JsonNode receipt =
        json(
            stockMovement(
                    product, "{\"kind\":\"RECEIPT\",\"quantity\":24,\"reason\":\" NF 1234 \"}")
                .andExpect(status().isCreated()));
    assertThat(receipt.get("kind").asText()).isEqualTo("RECEIPT");
    assertThat(receipt.get("quantity").asInt()).isEqualTo(24);
    assertThat(receipt.get("balanceAfter").asInt()).isEqualTo(24);
    assertThat(receipt.get("reason").asText()).isEqualTo("NF 1234");
    assertThat(receipt.get("userName").asText()).isNotBlank();
    assertThat(receipt.get("orderId").isNull()).isTrue();

    stockMovement(product, "{\"kind\":\"ADJUSTMENT\",\"quantity\":-3,\"reason\":\"Quebra\"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.balanceAfter").value(21));

    getJson("/api/products/" + product)
        .andExpect(jsonPath("$.stockQuantity").value(21))
        .andExpect(jsonPath("$.lowStock").value(false));

    getJson("/api/products/" + product + "/stock-movements")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].kind").value("ADJUSTMENT"))
        .andExpect(jsonPath("$.content[0].quantity").value(-3))
        .andExpect(jsonPath("$.content[0].reason").value("Quebra"))
        .andExpect(jsonPath("$.content[1].kind").value("RECEIPT"));
  }

  @Test
  void anAdjustmentNeverTakesTheBalanceBelowZero() throws Exception {
    JsonNode created = createTrackedProduct(2, 0);
    UUID product = id(created);
    String sku = created.get("sku").asText();

    stockMovement(product, "{\"kind\":\"ADJUSTMENT\",\"quantity\":-3,\"reason\":\"Contagem\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("insufficient_stock"))
        .andExpect(jsonPath("$.errors." + sku + "[0]").value("Restam 2 unidades."));

    getJson("/api/products/" + product).andExpect(jsonPath("$.stockQuantity").value(2));
    getJson("/api/products/" + product + "/stock-movements")
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void validatesTheMovement() throws Exception {
    UUID product = id(createTrackedProduct(0, 0));

    stockMovement(product, "{\"kind\":\"RECEIPT\",\"quantity\":0}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation_failed"))
        .andExpect(jsonPath("$.errors", hasKey("quantity")));
    stockMovement(product, "{\"kind\":\"RECEIPT\",\"quantity\":-1}")
        .andExpect(jsonPath("$.errors", hasKey("quantity")));
    stockMovement(product, "{\"kind\":\"ADJUSTMENT\",\"quantity\":0,\"reason\":\"x\"}")
        .andExpect(jsonPath("$.errors", hasKey("quantity")));
    stockMovement(product, "{\"kind\":\"ADJUSTMENT\",\"quantity\":2,\"reason\":\"  \"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors", hasKey("reason")));
    stockMovement(product, "{\"kind\":\"SALE\",\"quantity\":1}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors", hasKey("kind")));
    stockMovement(product, "{\"kind\":\"RECEIPT\"}")
        .andExpect(jsonPath("$.errors", hasKey("quantity")));

    getJson("/api/products/" + product + "/stock-movements")
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void productsWithoutStockControlCannotBeMoved() throws Exception {
    UUID product = seededProductId("PB-004-ESS");

    stockMovement(product, "{\"kind\":\"RECEIPT\",\"quantity\":5}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("stock_not_tracked"));
  }

  @Test
  void unknownProductIs404() throws Exception {
    String unknown = UUID.randomUUID().toString();
    stockMovement(UUID.fromString(unknown), "{\"kind\":\"RECEIPT\",\"quantity\":5}")
        .andExpect(status().isNotFound());
    getJson("/api/products/" + unknown + "/stock-movements").andExpect(status().isNotFound());
  }

  @Test
  void editingAProductKeepsItsBalance() throws Exception {
    JsonNode created = createTrackedProduct(7, 2);
    UUID product = id(created);

    putJson(
            "/api/products/" + product,
            "{\"sku\":\""
                + created.get("sku").asText()
                + "\",\"name\":\"Renomeado\",\"price\":\"9.00\",\"active\":true}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trackStock").value(true))
        .andExpect(jsonPath("$.stockMin").value(2))
        .andExpect(jsonPath("$.stockQuantity").value(7));

    putJson(
            "/api/products/" + product,
            "{\"sku\":\""
                + created.get("sku").asText()
                + "\",\"name\":\"Renomeado\",\"price\":\"9.00\",\"active\":true,"
                + "\"trackStock\":true,\"stockMin\":10,\"stockQuantity\":999}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stockMin").value(10))
        .andExpect(jsonPath("$.stockQuantity").value(7))
        .andExpect(jsonPath("$.lowStock").value(true));
  }

  @Test
  void rejectsANegativeMinimum() throws Exception {
    postJson(
            "/api/products",
            "{\"sku\":\""
                + unique("NEG")
                + "\",\"name\":\"X\",\"price\":\"1.00\",\"active\":true,\"stockMin\":-1}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors", hasKey("stockMin")));
  }

  @Test
  void filtersLowStock() throws Exception {
    String marker = unique("LOW");
    UUID low = id(createTrackedProduct(marker + "-A", 2, 2));
    createTrackedProduct(marker + "-B", 3, 2);
    UUID empty = id(createTrackedProduct(marker + "-C", 0, 0));

    JsonNode page =
        json(getJson("/api/products?lowStock=true&q=" + marker).andExpect(status().isOk()));
    assertThat(page.get("content"))
        .extracting(node -> node.get("id").asText())
        .containsExactly(low.toString(), empty.toString());

    JsonNode seeded = json(getJson("/api/products?lowStock=true&q=PB-001-ESS"));
    assertThat(seeded.get("totalElements").asInt()).isZero();
  }

  private JsonNode createTrackedProduct(int stock, int min) throws Exception {
    return createTrackedProduct(unique("STK"), stock, min);
  }

  private static UUID id(JsonNode node) {
    return UUID.fromString(node.get("id").asText());
  }
}
