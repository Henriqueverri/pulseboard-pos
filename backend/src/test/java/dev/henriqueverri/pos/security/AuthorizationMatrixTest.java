package dev.henriqueverri.pos.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.henriqueverri.pos.support.ApiIntegrationTest;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * The role matrix of the plan: CASHIER operates the till (customers, orders, pay, cancel) and reads
 * the catalog; only ADMIN creates/updates products and refunds orders.
 */
class AuthorizationMatrixTest extends ApiIntegrationTest {

  private ResultActions as(Role role, MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(role))));
  }

  private static MockHttpServletRequestBuilder jsonBody(
      MockHttpServletRequestBuilder request, String body) {
    return request.contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private static String productJson(String sku) {
    return "{\"sku\":\"" + sku + "\",\"name\":\"Matriz\",\"price\":\"5.00\",\"active\":true}";
  }

  @Test
  void cashierCannotAdministerProducts() throws Exception {
    String id =
        json(postJson("/api/products", productJson(unique("ADM"))).andExpect(status().isCreated()))
            .get("id")
            .asText();

    as(Role.CASHIER, jsonBody(post("/api/products"), productJson(unique("CSH"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("forbidden"))
        .andExpect(jsonPath("$.status").value(403));
    as(Role.CASHIER, jsonBody(put("/api/products/" + id), productJson(unique("CSH"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("forbidden"));

    as(Role.CASHIER, get("/api/products?q=PB-001")).andExpect(status().isOk());
    as(Role.CASHIER, get("/api/products/" + id)).andExpect(status().isOk());
  }

  @Test
  void adminAdministersProducts() throws Exception {
    String id =
        json(as(Role.ADMIN, jsonBody(post("/api/products"), productJson(unique("ADM"))))
                .andExpect(status().isCreated()))
            .get("id")
            .asText();
    as(Role.ADMIN, jsonBody(put("/api/products/" + id), productJson(unique("ADM2"))))
        .andExpect(status().isOk());
  }

  @Test
  void cashierOperatesTheTill() throws Exception {
    String email = unique("caixa") + "@cliente.pos.example";
    String customer =
        json(as(
                    Role.CASHIER,
                    jsonBody(
                        post("/api/customers"),
                        "{\"name\":\"Cliente\",\"email\":\"" + email + "\"}"))
                .andExpect(status().isCreated()))
            .get("id")
            .asText();
    as(
            Role.CASHIER,
            jsonBody(
                put("/api/customers/" + customer),
                "{\"name\":\"Cliente Editado\",\"email\":\"" + email + "\"}"))
        .andExpect(status().isOk());
    as(Role.CASHIER, get("/api/customers?q=" + email)).andExpect(status().isOk());

    UUID product = seededProductId("PB-003-ESS");
    String orderBody =
        "{\"customerId\":\""
            + customer
            + "\",\"items\":[{\"productId\":\""
            + product
            + "\",\"quantity\":1}]}";
    String toPay =
        json(as(Role.CASHIER, jsonBody(post("/api/orders"), orderBody))
                .andExpect(status().isCreated()))
            .get("id")
            .asText();
    String toCancel =
        json(as(Role.CASHIER, jsonBody(post("/api/orders"), orderBody))).get("id").asText();

    as(
            Role.CASHIER,
            jsonBody(post("/api/orders/" + toPay + "/pay"), "{\"paymentMethod\":\"CASH\"}"))
        .andExpect(status().isOk());
    as(Role.CASHIER, post("/api/orders/" + toCancel + "/cancel"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELED"));
    as(Role.CASHIER, get("/api/orders")).andExpect(status().isOk());
    as(Role.CASHIER, get("/api/orders/" + toPay)).andExpect(status().isOk());
    as(Role.CASHIER, get("/api/auth/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("CASHIER"));
  }

  @Test
  void onlyAdminRefunds() throws Exception {
    String id = createPendingOrder().get("id").asText();
    as(Role.CASHIER, jsonBody(post("/api/orders/" + id + "/pay"), "{\"paymentMethod\":\"PIX\"}"))
        .andExpect(status().isOk());

    as(Role.CASHIER, post("/api/orders/" + id + "/refund"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("forbidden"));
    as(Role.CASHIER, get("/api/orders/" + id)).andExpect(jsonPath("$.status").value("PAID"));

    as(Role.ADMIN, post("/api/orders/" + id + "/refund"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REFUNDED"));
  }

  static Stream<Arguments> protectedEndpoints() {
    String id = UUID.randomUUID().toString();
    return Stream.of(
        Arguments.of(HttpMethod.GET, "/api/auth/me"),
        Arguments.of(HttpMethod.GET, "/api/products"),
        Arguments.of(HttpMethod.GET, "/api/products/" + id),
        Arguments.of(HttpMethod.POST, "/api/products"),
        Arguments.of(HttpMethod.PUT, "/api/products/" + id),
        Arguments.of(HttpMethod.GET, "/api/customers"),
        Arguments.of(HttpMethod.POST, "/api/customers"),
        Arguments.of(HttpMethod.PUT, "/api/customers/" + id),
        Arguments.of(HttpMethod.GET, "/api/orders"),
        Arguments.of(HttpMethod.GET, "/api/orders/" + id),
        Arguments.of(HttpMethod.POST, "/api/orders"),
        Arguments.of(HttpMethod.POST, "/api/orders/" + id + "/pay"),
        Arguments.of(HttpMethod.POST, "/api/orders/" + id + "/cancel"),
        Arguments.of(HttpMethod.POST, "/api/orders/" + id + "/refund"),
        Arguments.of(HttpMethod.GET, "/api/integration/events"),
        Arguments.of(HttpMethod.GET, "/api/integration/events/" + id),
        Arguments.of(HttpMethod.POST, "/api/integration/events/" + id + "/retry"),
        Arguments.of(HttpMethod.POST, "/api/integration/events/retry-configuration-failures"),
        Arguments.of(HttpMethod.GET, "/api/integration/health"));
  }

  @ParameterizedTest(name = "{0} {1}")
  @MethodSource("protectedEndpoints")
  void everyApiEndpointRequiresAToken(HttpMethod method, String path) throws Exception {
    mvc.perform(jsonBody(MockMvcRequestBuilders.request(method, path), "{}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("unauthorized"));
  }
}
