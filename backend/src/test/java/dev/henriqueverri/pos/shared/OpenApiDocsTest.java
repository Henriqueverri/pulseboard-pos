package dev.henriqueverri.pos.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.henriqueverri.pos.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;

class OpenApiDocsTest extends ApiIntegrationTest {

  @Test
  void documentsTheRealEndpointsWithMoneyAsString() throws Exception {
    JsonNode docs = json(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()));

    JsonNode paths = docs.get("paths");
    assertThat(paths.has("/api/products")).isTrue();
    assertThat(paths.has("/api/customers")).isTrue();
    assertThat(paths.has("/api/orders")).isTrue();
    assertThat(paths.has("/api/orders/{id}/pay")).isTrue();
    assertThat(paths.has("/api/orders/{id}/cancel")).isTrue();
    assertThat(paths.has("/api/orders/{id}/refund")).isTrue();
    assertThat(paths.fieldNames()).toIterable().allMatch(path -> path.startsWith("/api/"));

    JsonNode schemas = docs.get("components").get("schemas");
    assertThat(schemas.get("OrderResponse").get("properties").get("total").get("type").asText())
        .isEqualTo("string");
    assertThat(schemas.get("ProductRequest").get("properties").get("price").get("type").asText())
        .isEqualTo("string");
    assertThat(schemas.has("Order")).isFalse();
    assertThat(schemas.has("Product")).isFalse();
    assertThat(schemas.has("User")).isFalse();

    JsonNode bearer = docs.get("components").get("securitySchemes").get("bearerAuth");
    assertThat(bearer.get("scheme").asText()).isEqualTo("bearer");
    assertThat(bearer.get("bearerFormat").asText()).isEqualTo("JWT");
    assertThat(docs.get("security").get(0).has("bearerAuth")).isTrue();
    JsonNode login = paths.get("/api/auth/login").get("post");
    assertThat(login.get("security")).isEmpty();
    assertThat(paths.has("/api/auth/me")).isTrue();

    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
  }
}
