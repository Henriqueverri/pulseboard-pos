package dev.henriqueverri.pos.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.henriqueverri.pos.support.ApiIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductApiTest extends ApiIntegrationTest {

  @Test
  void createsReadsAndUpdatesProduct() throws Exception {
    String sku = unique("NEW");

    JsonNode created =
        json(
            postJson(
                    "/api/products",
                    "{\"sku\":\" "
                        + sku
                        + " \",\"name\":\"Novo produto\",\"price\":\"19.9\",\"active\":true}")
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location")));
    String id = created.get("id").asText();
    assertThat(created.get("sku").asText()).isEqualTo(sku);
    assertThat(created.get("price").asText()).isEqualTo("19.90");
    assertThat(created.get("price").isTextual()).isTrue();

    getJson("/api/products/" + id)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Novo produto"));

    putJson(
            "/api/products/" + id,
            "{\"sku\":\"" + sku + "\",\"name\":\"Editado\",\"price\":\"25.00\",\"active\":false}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Editado"))
        .andExpect(jsonPath("$.price").value("25.00"))
        .andExpect(jsonPath("$.active").value(false));
  }

  @Test
  void skuIsUnique() throws Exception {
    postJson(
            "/api/products",
            "{\"sku\":\"PB-001-ESS\",\"name\":\"Duplicado\",\"price\":\"1.00\",\"active\":true}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("duplicate_sku"));

    String id =
        json(postJson(
                "/api/products",
                "{\"sku\":\""
                    + unique("UPD")
                    + "\",\"name\":\"Outro\",\"price\":\"1.00\",\"active\":true}"))
            .get("id")
            .asText();
    putJson(
            "/api/products/" + id,
            "{\"sku\":\"PB-001-ESS\",\"name\":\"Outro\",\"price\":\"1.00\",\"active\":true}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("duplicate_sku"));
  }

  @Test
  void validatesProduct() throws Exception {
    postJson("/api/products", "{\"sku\":\"\",\"name\":\"\",\"price\":\"0.00\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation_failed"))
        .andExpect(jsonPath("$.errors", hasKey("sku")))
        .andExpect(jsonPath("$.errors", hasKey("name")))
        .andExpect(jsonPath("$.errors", hasKey("price")))
        .andExpect(jsonPath("$.errors", hasKey("active")));

    postJson(
            "/api/products",
            "{\"sku\":\""
                + "X".repeat(65)
                + "\",\"name\":\"n\",\"price\":\"1.005\",\"active\":true}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors", hasKey("sku")))
        .andExpect(jsonPath("$.errors", hasKey("price")));
  }

  @Test
  void rejectsMoneyAsJsonNumber() throws Exception {
    postJson(
            "/api/products",
            "{\"sku\":\"" + unique("NUM") + "\",\"name\":\"n\",\"price\":19.90,\"active\":true}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation_failed"))
        .andExpect(jsonPath("$.errors", hasKey("price")));
  }

  @Test
  void unknownProductReturns404() throws Exception {
    getJson("/api/products/" + UUID.randomUUID())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("not_found"))
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  void searchesByNameOrSkuWithPagination() throws Exception {
    getJson("/api/products?q=pb-010")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].sku").value("PB-010-ESS"))
        .andExpect(jsonPath("$.content[1].sku").value("PB-010-PRO"));

    getJson("/api/products?q=teclado MECÂNICO")
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].name").value("Teclado mecânico Essential"));

    getJson("/api/products?q=PB-0&size=5&page=1")
        .andExpect(jsonPath("$.content.length()").value(5))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.size").value(5))
        .andExpect(jsonPath("$.content[0].sku").value("PB-003-PRO"));

    // LIKE wildcards in the term are literal, not patterns.
    getJson("/api/products?q=_").andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void filtersByActive() throws Exception {
    String sku = unique("FILTRO");
    postJson(
            "/api/products",
            "{\"sku\":\"" + sku + "\",\"name\":\"Inativo\",\"price\":\"1.00\",\"active\":false}")
        .andExpect(status().isCreated());

    getJson("/api/products?q=" + sku + "&active=true")
        .andExpect(jsonPath("$.totalElements").value(0));
    getJson("/api/products?q=" + sku + "&active=false")
        .andExpect(jsonPath("$.totalElements").value(1));
  }
}
